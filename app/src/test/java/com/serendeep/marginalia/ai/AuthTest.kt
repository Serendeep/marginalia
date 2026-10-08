package com.serendeep.marginalia.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class PkceTest {
    @Test
    fun rfc7636Vector() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun verifierIsSixtyFourUrlSafeChars() {
        val v = Pkce.verifier()
        assertEquals(64, v.length)
        assertTrue(v.all { it.isLetterOrDigit() || it in "-._~" })
        assertTrue(v != Pkce.verifier())
    }
}

class JwtTest {
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pub = pair.public as RSAPublicKey
    private val n = enc.encodeToString(pub.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
    private val e = enc.encodeToString(pub.publicExponent.toByteArray())

    private fun token(claims: String, tamper: Boolean = false): String {
        val head = enc.encodeToString("""{"alg":"RS256","kid":"k1"}""".toByteArray())
        val body = enc.encodeToString((if (tamper) claims.replace("u1", "u2") else claims).toByteArray())
        val signed = if (tamper) enc.encodeToString(claims.toByteArray()) else body
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private)
            update("$head.$signed".toByteArray())
            sign()
        }
        return "$head.$body.${enc.encodeToString(sig)}"
    }

    private val good = """{"iss":"https://auth.openai.com","aud":["oaiapp_1"],"exp":2000,"nonce":"n1","sub":"u1"}"""

    @Test
    fun validSignatureVerifies() {
        val jwt = Jwt.parse(token(good))!!
        assertEquals("k1", jwt.kid)
        assertTrue(jwt.verifyRs256(n, e))
        assertNull(jwt.checkClaims("https://auth.openai.com", "oaiapp_1", "n1", 1000))
    }

    @Test
    fun tamperedPayloadFails() {
        assertFalse(Jwt.parse(token(good, tamper = true))!!.verifyRs256(n, e))
    }

    @Test
    fun otherKeyFails() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        val on = enc.encodeToString(other.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
        assertFalse(Jwt.parse(token(good))!!.verifyRs256(on, e))
    }

    @Test
    fun claimChecks() {
        val jwt = Jwt.parse(token(good))!!
        assertNotNull(jwt.checkClaims("https://evil.example", "oaiapp_1", "n1", 1000))
        assertNotNull(jwt.checkClaims("https://auth.openai.com", "oaiapp_2", "n1", 1000))
        assertNotNull(jwt.checkClaims("https://auth.openai.com", "oaiapp_1", "other", 1000))
        assertNotNull(jwt.checkClaims("https://auth.openai.com", "oaiapp_1", "n1", 2061))
        assertNull(jwt.checkClaims("https://auth.openai.com", "oaiapp_1", "n1", 2059))
    }

    @Test
    fun stringAudienceAccepted() {
        val jwt = Jwt.parse(token(good.replace("""["oaiapp_1"]""", "\"oaiapp_1\"")))!!
        assertNull(jwt.checkClaims("https://auth.openai.com", "oaiapp_1", "n1", 1000))
    }

    @Test
    fun malformedTokenIsNull() {
        assertNull(Jwt.parse("not-a-jwt"))
        assertNull(Jwt.parse("a.b.c"))
    }
}

class AuthFlowTest {
    private fun params(url: String) = LoopbackServer.parseQuery(url.substringAfter('?'))

    @Test
    fun firstRegistrationUrl() {
        val url = AuthFlow.authorizeUrl("dynamic_agent_client", "urn:uuid:abc", 1455, "st", "no", "ch", firstRegistration = true)
        assertTrue(url.startsWith("https://auth.openai.com/api/accounts/authorize?"))
        val p = params(url)
        assertEquals("dynamic_agent_client", p["client_id"])
        assertEquals("Marginalia", p["agent_name_hint"])
        assertEquals("urn:uuid:abc", p["ext_agent_host_id"])
        assertEquals("code", p["response_type"])
        assertEquals("http://127.0.0.1:1455/auth/callback", p["redirect_uri"])
        assertEquals(AuthFlow.SCOPE, p["scope"])
        assertEquals("https://api.openai.com/v1", p["resource"])
        assertEquals("st", p["state"])
        assertEquals("no", p["nonce"])
        assertEquals("S256", p["code_challenge_method"])
        assertEquals("ch", p["code_challenge"])
        assertFalse(p.containsKey("login_hint"))
        assertTrue(url.contains("scope=openid%20profile%20email"))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A1455%2Fauth%2Fcallback"))
    }

    @Test
    fun reauthUrlOmitsAgentNameAndAddsHint() {
        val url = AuthFlow.authorizeUrl("oaiapp_1", "urn:uuid:abc", 1457, "s", "n", "c", firstRegistration = false, loginHint = "a+b@x.com")
        val p = params(url)
        assertFalse(p.containsKey("agent_name_hint"))
        assertEquals("oaiapp_1", p["client_id"])
        assertEquals("a+b@x.com", p["login_hint"])
        assertTrue(p["redirect_uri"]!!.contains(":1457/"))
    }

    @Test
    fun stateMismatchRejected() {
        val r = AuthFlow.validateCallback(mapOf("state" to "bad", "code" to "c", "client_id" to "oaiapp_1"), "good", null)
        assertTrue(r is AuthFlow.Callback.Rejected)
    }

    @Test
    fun firstRegistrationNeedsIssuedClientId() {
        val r = AuthFlow.validateCallback(mapOf("state" to "s", "code" to "c"), "s", null)
        assertTrue(r is AuthFlow.Callback.Rejected)
        val ok = AuthFlow.validateCallback(mapOf("state" to "s", "code" to "c", "client_id" to "oaiapp_9"), "s", null)
        assertEquals(AuthFlow.Callback.Ok("c", "oaiapp_9"), ok)
    }

    @Test
    fun reauthRules() {
        assertEquals(
            AuthFlow.Callback.Ok("c", "oaiapp_1"),
            AuthFlow.validateCallback(mapOf("state" to "s", "code" to "c"), "s", "oaiapp_1"),
        )
        assertTrue(
            AuthFlow.validateCallback(mapOf("state" to "s", "code" to "c", "client_id" to "oaiapp_2"), "s", "oaiapp_1")
                is AuthFlow.Callback.Rejected,
        )
    }

    @Test
    fun accessDeniedStops() {
        val r = AuthFlow.validateCallback(mapOf("state" to "s", "error" to "access_denied"), "s", null)
        assertTrue(r is AuthFlow.Callback.Rejected)
        assertTrue(AuthFlow.validateCallback(mapOf("error" to "access_denied"), "s", null) is AuthFlow.Callback.Rejected)
    }

    @Test
    fun missingCodeRejected() {
        assertTrue(AuthFlow.validateCallback(mapOf("state" to "s", "client_id" to "oaiapp_1"), "s", null) is AuthFlow.Callback.Rejected)
    }
}
