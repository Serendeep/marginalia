package com.serendeep.marginalia.ai

import java.net.URLEncoder

object AuthFlow {
    const val ISSUER = "https://auth.openai.com"
    const val AUTHORIZE_URL = "https://auth.openai.com/api/accounts/authorize"
    const val TOKEN_URL = "https://auth.openai.com/api/accounts/oauth/token"
    const val JWKS_URL = "https://auth.openai.com/.well-known/jwks.json"
    const val RESOURCE = "https://api.openai.com/v1"
    const val REGISTRATION_CLIENT_ID = "dynamic_agent_client"
    const val SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    const val CALLBACK_PATH = "/auth/callback"
    val PORTS = 1455..1460

    fun redirectUri(port: Int) = "http://127.0.0.1:$port$CALLBACK_PATH"

    fun authorizeUrl(
        clientId: String,
        extAgentHostId: String,
        port: Int,
        state: String,
        nonce: String,
        codeChallenge: String,
        firstRegistration: Boolean,
        loginHint: String? = null,
    ): String {
        val params = buildList {
            add("client_id" to clientId)
            if (firstRegistration) add("agent_name_hint" to "Marginalia")
            add("ext_agent_host_id" to extAgentHostId)
            if (!loginHint.isNullOrEmpty()) add("login_hint" to loginHint)
            add("response_type" to "code")
            add("redirect_uri" to redirectUri(port))
            add("scope" to SCOPE)
            add("resource" to RESOURCE)
            add("state" to state)
            add("nonce" to nonce)
            add("code_challenge_method" to "S256")
            add("code_challenge" to codeChallenge)
        }
        return AUTHORIZE_URL + "?" + params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    sealed interface Callback {
        data class Ok(val code: String, val clientId: String) : Callback
        data class Rejected(val message: String) : Callback
    }

    /** [savedClientId] is the issued id on reauth and null on first registration. */
    fun validateCallback(params: Map<String, String>, expectedState: String, savedClientId: String?): Callback {
        if (params["state"] != expectedState) return Callback.Rejected("Sign-in response did not match this request")
        val err = params["error"]
        if (err != null) {
            return Callback.Rejected(
                if (err == "access_denied") "Sign-in was cancelled" else params["error_description"] ?: "Sign-in failed ($err)",
            )
        }
        val code = params["code"]
        if (code.isNullOrEmpty()) return Callback.Rejected("Sign-in response had no authorization code")
        val returned = params["client_id"]?.ifEmpty { null }
        if (savedClientId == null) {
            if (returned == null) return Callback.Rejected("Registration with ChatGPT was incomplete")
            return Callback.Ok(code, returned)
        }
        if (returned != null && returned != savedClientId) return Callback.Rejected("Sign-in returned a different client")
        return Callback.Ok(code, savedClientId)
    }
}
