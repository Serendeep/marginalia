package com.serendeep.marginalia.ai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object Http {
    fun open(url: String, method: String, readTimeoutMs: Int = 120_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }

    fun postForm(url: String, fields: Map<String, String>): Pair<Int, String> {
        val body = fields.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val conn = open(url, "POST", 30_000).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode to readBody(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun get(url: String, bearer: String?): Pair<Int, String> {
        val conn = open(url, "GET", 30_000)
        if (!bearer.isNullOrEmpty()) conn.setRequestProperty("Authorization", "Bearer $bearer")
        return try {
            conn.responseCode to readBody(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun postJson(url: String, bearer: String?, body: JSONObject): HttpURLConnection =
        open(url, "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (!bearer.isNullOrEmpty()) setRequestProperty("Authorization", "Bearer $bearer")
            outputStream.use { it.write(body.toString().toByteArray()) }
        }

    fun readBody(conn: HttpURLConnection): String =
        ((if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }).orEmpty()

    fun httpError(code: Int, body: String): AiError {
        val err = try { JSONObject(body).optJSONObject("error") } catch (_: Exception) { null }
        val errCode = err?.optString("code")?.ifEmpty { null }
        val message = err?.optString("message")?.ifEmpty { null }
        if (errCode != null && errCode.startsWith("subscription_sharing_")) return usageError(errCode, message ?: "Request failed")
        return when (code) {
            401 -> AiError(AiErrorKind.UNAUTHORIZED, "Not authorized — reconnect in settings")
            else -> AiError(AiErrorKind.HTTP, message ?: "Request failed ($code)")
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
