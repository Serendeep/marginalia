package com.serendeep.marginalia.update

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val CONNECT_MS = 15_000
private const val READ_MS = 30_000
private const val MAX_TEXT_BYTES = 256 * 1024

sealed interface Fetched {
    data object NotModified : Fetched
    data class Body(val text: String, val etag: String?) : Fetched
}

internal object UpdateHttp {

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }

    /** Small text document, with a conditional request when [etag] is known. */
    fun fetchText(url: String, etag: String? = null): Fetched {
        val conn = open(url)
        if (!etag.isNullOrEmpty()) conn.setRequestProperty("If-None-Match", etag)
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return Fetched.NotModified
            if (code !in 200..299) throw IOException("Update server answered $code")
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_TEXT_BYTES) throw IOException("Update feed is too large")
                }
                out.toByteArray()
            }
            return Fetched.Body(String(bytes, Charsets.UTF_8), conn.getHeaderField("ETag"))
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads [url] into [part], continuing from what is already there when the server still
     * has the same file, and returns the SHA-256 of the whole file. [etagFile] remembers which
     * version of the file [part] holds.
     */
    fun download(
        url: String,
        part: File,
        etagFile: File,
        onProgress: (done: Long, total: Long) -> Unit,
        cancelled: () -> Boolean,
    ): String {
        val savedEtag = etagFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        var have = if (part.exists() && savedEtag.isNotEmpty()) part.length() else 0L
        if (have == 0L) part.delete()
        val conn = open(url)
        conn.setRequestProperty("Accept", "application/octet-stream")
        // Compressed responses would make Range offsets meaningless.
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (have > 0) {
            conn.setRequestProperty("Range", "bytes=$have-")
            conn.setRequestProperty("If-Range", savedEtag)
        }
        try {
            val code = conn.responseCode
            when {
                code == HttpURLConnection.HTTP_PARTIAL -> Unit
                code == HttpURLConnection.HTTP_OK -> have = 0
                code == 416 -> {
                    part.delete()
                    etagFile.delete()
                    throw IOException("Partial download was out of date; retrying")
                }
                else -> throw IOException("Download server answered $code")
            }
            val etag = conn.getHeaderField("ETag").orEmpty()
            if (have == 0L) {
                part.delete()
                if (etag.isNotEmpty()) etagFile.writeText(etag) else etagFile.delete()
            }
            val total = when {
                code == HttpURLConnection.HTTP_PARTIAL -> have + conn.contentLengthLong
                else -> conn.contentLengthLong
            }
            val digest = MessageDigest.getInstance("SHA-256")
            if (have > 0) part.inputStream().use { digest.updateFrom(it) }
            var done = have
            onProgress(done, total)
            RandomAccessFile(part, "rw").use { out ->
                out.seek(have)
                val buf = ByteArray(64 * 1024)
                conn.inputStream.use { input ->
                    while (true) {
                        if (cancelled()) throw IOException("Download stopped")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
                if (total > 0 && done != total) throw IOException("Download ended early")
            }
            return digest.digest().toHex()
        } finally {
            conn.disconnect()
        }
    }
}

private fun MessageDigest.updateFrom(input: java.io.InputStream) {
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) return
        update(buf, 0, n)
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** SHA-256 of a file, streamed. */
internal fun sha256Of(file: File): String =
    MessageDigest.getInstance("SHA-256").also { d -> file.inputStream().use { d.updateFrom(it) } }.digest().toHex()
