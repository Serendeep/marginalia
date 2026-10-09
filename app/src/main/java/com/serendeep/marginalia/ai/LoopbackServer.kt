package com.serendeep.marginalia.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object LoopbackServer {
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    fun freePort(ports: IntRange = AuthFlow.PORTS): Int? = ports.firstOrNull {
        try { ServerSocket(it, 1, loopback).use { true } } catch (_: Exception) { false }
    }

    /** [check] returns an error message to show in the browser, or null if the callback is acceptable. */
    suspend fun awaitCallback(
        port: Int,
        expectedState: String,
        timeout: Duration = 5.minutes,
        check: (Map<String, String>) -> String? = { if (it["state"] != expectedState) "State mismatch" else it["error"] },
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        ServerSocket(port, 1, loopback).use { server ->
            server.soTimeout = 400
            var result: Map<String, String>? = null
            while (result == null) {
                ensureActive()
                if (System.nanoTime() > deadline) throw AiException(AiError(AiErrorKind.NETWORK, "Sign-in timed out"))
                val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                result = socket.use { handle(it, check) }
            }
            result
        }
    }

    private fun handle(socket: Socket, check: (Map<String, String>) -> String?): Map<String, String>? {
        socket.soTimeout = 5000
        val requestLine = try { socket.getInputStream().bufferedReader().readLine().orEmpty() } catch (_: Exception) { "" }
        val target = requestLine.split(' ').getOrNull(1).orEmpty()
        if (target.substringBefore('?') != AuthFlow.CALLBACK_PATH) {
            respond(socket, 404, "Not found")
            return null
        }
        val params = parseQuery(target.substringAfter('?', ""))
        val error = check(params)
        respond(socket, if (error == null) 200 else 400, page(error))
        return params
    }

    fun parseQuery(query: String): Map<String, String> = query.split('&').filter { it.isNotEmpty() }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }

    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 404 -> "Not Found"; else -> "Bad Request" }
        socket.getOutputStream().run {
            write(
                (
                    "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    ).toByteArray(),
            )
            write(bytes)
            flush()
        }
    }

    private fun page(error: String?): String {
        val message = if (error == null) {
            "Marginalia is connected — you can close this tab"
        } else {
            "Sign-in failed: " + error.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Marginalia</title></head>" +
            "<body style=\"margin:0;height:100vh;display:flex;align-items:center;justify-content:center;" +
            "background:#121212;color:#e8e8e8;font-family:sans-serif\"><p style=\"font-size:20px\">$message</p></body></html>"
    }
}
