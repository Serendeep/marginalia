package com.serendeep.marginalia.ai

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

class Jwt private constructor(
    val header: JSONObject,
    val claims: JSONObject,
    private val signingInput: ByteArray,
    private val signature: ByteArray,
) {
    val kid: String? get() = header.optString("kid").ifEmpty { null }
    val alg: String? get() = header.optString("alg").ifEmpty { null }

    fun verifyRs256(n: String, e: String): Boolean {
        if (alg != "RS256") return false
        return try {
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(RSAPublicKeySpec(BigInteger(1, b64(n)), BigInteger(1, b64(e))))
            Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update(signingInput)
                verify(signature)
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Returns a failure description, or null when issuer, audience, expiry and nonce all hold. */
    fun checkClaims(issuer: String, clientId: String, nonce: String, nowSeconds: Long, skewSeconds: Long = 60): String? {
        if (claims.optString("iss") != issuer) return "ID token issuer mismatch"
        if (clientId !in audiences()) return "ID token audience mismatch"
        if (!claims.has("exp") || claims.optLong("exp") + skewSeconds < nowSeconds) return "ID token expired"
        if (claims.optString("nonce") != nonce) return "ID token nonce mismatch"
        return null
    }

    private fun audiences(): List<String> = when (val aud = claims.opt("aud")) {
        is String -> listOf(aud)
        is JSONArray -> List(aud.length()) { aud.optString(it) }
        else -> emptyList()
    }

    companion object {
        fun parse(token: String): Jwt? = try {
            val parts = token.split('.')
            if (parts.size != 3) {
                null
            } else {
                Jwt(
                    JSONObject(String(b64(parts[0]))),
                    JSONObject(String(b64(parts[1]))),
                    "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII),
                    b64(parts[2]),
                )
            }
        } catch (_: Exception) {
            null
        }

        private fun b64(s: String): ByteArray = Base64.getUrlDecoder().decode(s)
    }
}
