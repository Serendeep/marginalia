package com.serendeep.marginalia.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ChatGptStatus {
    data object Disconnected : ChatGptStatus
    data object Connecting : ChatGptStatus
    data class Connected(val email: String, val model: String?) : ChatGptStatus
    data class Error(val message: String) : ChatGptStatus
}

class SignInAttempt(
    val url: String,
    val port: Int,
    internal val state: String,
    internal val nonce: String,
    internal val verifier: String,
    internal val savedClientId: String?,
)

@Singleton
class ChatGptAuth @Inject constructor(private val store: TokenStore) {
    private val _status = MutableStateFlow(statusOf(store.session()))
    val status: StateFlow<ChatGptStatus> = _status.asStateFlow()

    private val refreshLock = Mutex()
    private val jwks = HashMap<String, JSONObject>()

    fun beginSignIn(): SignInAttempt {
        val port = LoopbackServer.freePort() ?: throw AiException(AiError(AiErrorKind.NETWORK, "No free local port for sign-in"))
        val session = store.session()
        val state = Pkce.randomString(32)
        val nonce = Pkce.randomString(32)
        val verifier = Pkce.verifier()
        val url = AuthFlow.authorizeUrl(
            clientId = session?.issuedClientId ?: AuthFlow.REGISTRATION_CLIENT_ID,
            extAgentHostId = store.extAgentHostId(),
            port = port,
            state = state,
            nonce = nonce,
            codeChallenge = Pkce.challenge(verifier),
            firstRegistration = session == null,
            loginHint = session?.email,
        )
        return SignInAttempt(url, port, state, nonce, verifier, session?.issuedClientId)
    }

    suspend fun completeSignIn(attempt: SignInAttempt) {
        val previous = _status.value
        _status.value = ChatGptStatus.Connecting
        try {
            val params = LoopbackServer.awaitCallback(attempt.port, attempt.state) {
                (AuthFlow.validateCallback(it, attempt.state, attempt.savedClientId) as? AuthFlow.Callback.Rejected)?.message
            }
            val callback = when (val r = AuthFlow.validateCallback(params, attempt.state, attempt.savedClientId)) {
                is AuthFlow.Callback.Rejected -> throw AiException(AiError(AiErrorKind.UNAUTHORIZED, r.message))
                is AuthFlow.Callback.Ok -> r
            }
            val token = withContext(Dispatchers.IO) {
                val (code, body) = Http.postForm(
                    AuthFlow.TOKEN_URL,
                    mapOf(
                        "grant_type" to "authorization_code",
                        "client_id" to callback.clientId,
                        "code" to callback.code,
                        "code_verifier" to attempt.verifier,
                        "redirect_uri" to AuthFlow.redirectUri(attempt.port),
                        "resource" to AuthFlow.RESOURCE,
                    ),
                )
                parseTokenResponse(code, body)
            }
            val idToken = token.idToken ?: throw AiException(AiError(AiErrorKind.PROTOCOL, "Sign-in returned no ID token"))
            val claims = withContext(Dispatchers.IO) { verifyIdToken(idToken, callback.clientId, attempt.nonce) }
            val sub = claims.optString("sub")
            val existing = store.session()
            if (sub.isEmpty() || (existing != null && existing.accountSub != sub)) {
                throw AiException(AiError(AiErrorKind.UNAUTHORIZED, "Signed in with a different ChatGPT account"))
            }
            if (AuthFlow.PLAN_SCOPE !in token.scopes) {
                throw AiException(AiError(AiErrorKind.UNAUTHORIZED, "This sign-in is not authorized to use your ChatGPT plan"))
            }
            val refresh = token.refreshToken ?: throw AiException(AiError(AiErrorKind.PROTOCOL, "Sign-in returned no refresh token"))
            val session = ChatGptSession(
                issuedClientId = callback.clientId,
                accountSub = sub,
                email = claims.optString("email"),
                accessToken = token.accessToken,
                refreshToken = refresh,
                expiresAt = token.expiresAt,
                scopes = token.scopes,
                selectedModelSlug = existing?.selectedModelSlug,
            )
            store.save(session)
            _status.value = statusOf(session)
        } catch (e: CancellationException) {
            _status.value = previous
            throw e
        } catch (e: AiException) {
            _status.value = ChatGptStatus.Error(e.error.message)
        } catch (e: InvalidGrant) {
            _status.value = ChatGptStatus.Error("Sign-in expired — try connecting again")
        } catch (e: IOException) {
            _status.value = ChatGptStatus.Error("Couldn't reach ChatGPT sign-in")
        }
    }

    /** Throws [AiException] when not connected or the session can no longer be refreshed. */
    suspend fun validAccessToken(force: Boolean = false): String = refreshLock.withLock {
        val s = store.session() ?: throw AiException(AiError(AiErrorKind.NOT_CONNECTED, "ChatGPT isn't connected"))
        if (!force && s.expiresAt - System.currentTimeMillis() > REFRESH_MARGIN_MS) return s.accessToken
        refresh(s).accessToken
    }

    private suspend fun refresh(s: ChatGptSession): ChatGptSession = withContext(Dispatchers.IO) {
        val (code, body) = try {
            Http.postForm(
                AuthFlow.TOKEN_URL,
                mapOf(
                    "grant_type" to "refresh_token",
                    "client_id" to s.issuedClientId,
                    "refresh_token" to s.refreshToken,
                    "resource" to AuthFlow.RESOURCE,
                ),
            )
        } catch (e: IOException) {
            throw AiException(AiError(AiErrorKind.NETWORK, "Couldn't reach ChatGPT"))
        }
        val token = try {
            parseTokenResponse(code, body)
        } catch (e: InvalidGrant) {
            store.clear()
            val message = "ChatGPT session expired — reconnect in settings"
            _status.value = ChatGptStatus.Error(message)
            throw AiException(AiError(AiErrorKind.NOT_CONNECTED, message))
        }
        val next = s.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken ?: s.refreshToken,
            expiresAt = token.expiresAt,
            scopes = token.scopes.ifEmpty { s.scopes },
        )
        store.save(next)
        next
    }

    fun disconnect() {
        store.clear()
        _status.value = ChatGptStatus.Disconnected
    }

    fun selectedModel(): String? = store.session()?.selectedModelSlug

    fun selectModel(slug: String) {
        val s = store.session() ?: return
        val next = s.copy(selectedModelSlug = slug)
        store.save(next)
        _status.value = statusOf(next)
    }

    private fun verifyIdToken(idToken: String, clientId: String, nonce: String): JSONObject {
        val jwt = Jwt.parse(idToken) ?: throw AiException(AiError(AiErrorKind.PROTOCOL, "Malformed ID token"))
        val key = jwk(jwt.kid) ?: throw AiException(AiError(AiErrorKind.PROTOCOL, "Unknown ID token signing key"))
        if (!jwt.verifyRs256(key.optString("n"), key.optString("e"))) {
            throw AiException(AiError(AiErrorKind.UNAUTHORIZED, "ID token signature is invalid"))
        }
        jwt.checkClaims(AuthFlow.ISSUER, clientId, nonce, System.currentTimeMillis() / 1000)?.let {
            throw AiException(AiError(AiErrorKind.UNAUTHORIZED, it))
        }
        return jwt.claims
    }

    @Synchronized
    private fun jwk(kid: String?): JSONObject? {
        if (kid == null) return null
        jwks[kid]?.let { return it }
        val (code, body) = Http.get(AuthFlow.JWKS_URL, null)
        if (code !in 200..299) return null
        val keys: JSONArray = JSONObject(body).optJSONArray("keys") ?: return null
        for (i in 0 until keys.length()) {
            val k = keys.optJSONObject(i) ?: continue
            if (k.optString("kty") == "RSA") jwks[k.optString("kid")] = k
        }
        return jwks[kid]
    }

    private class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val expiresAt: Long,
        val scopes: Set<String>,
    )

    private class InvalidGrant : Exception()

    private fun parseTokenResponse(code: Int, body: String): Tokens {
        val json = try { JSONObject(body) } catch (_: Exception) { null }
        if (code !in 200..299 || json == null) {
            if (json?.optString("error") == "invalid_grant") throw InvalidGrant()
            val message = json?.optString("error_description")?.ifEmpty { null } ?: "Sign-in failed ($code)"
            throw AiException(AiError(AiErrorKind.UNAUTHORIZED, message))
        }
        val access = json.optString("access_token").ifEmpty {
            throw AiException(AiError(AiErrorKind.PROTOCOL, "Sign-in returned no access token"))
        }
        val scopes = json.optJSONArray("scopes")?.let { a -> List(a.length()) { a.optString(it) } }
            ?: json.optString("scope").split(' ')
        return Tokens(
            accessToken = access,
            refreshToken = json.optString("refresh_token").ifEmpty { null },
            idToken = json.optString("id_token").ifEmpty { null },
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
            scopes = scopes.filter { it.isNotEmpty() }.toSet(),
        )
    }

    private fun statusOf(s: ChatGptSession?): ChatGptStatus =
        if (s == null) ChatGptStatus.Disconnected else ChatGptStatus.Connected(s.email, s.selectedModelSlug)

    private companion object {
        const val REFRESH_MARGIN_MS = 2 * 60 * 1000L
    }
}
