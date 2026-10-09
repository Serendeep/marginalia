package com.serendeep.marginalia.update

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.concurrent.thread

/** A just-enough HTTP/1.1 server; the JDK's own is not visible from Android unit tests. */
private class TestServer(private val handle: (path: String, headers: Map<String, String>) -> Response) {
    class Response(val status: Int, val headers: Map<String, String> = emptyMap(), val body: ByteArray = ByteArray(0))

    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val port get() = socket.localPort

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: IOException) { return@thread }
                thread(isDaemon = true) { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) = client.use {
        val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val path = input.readLine()?.split(" ")?.getOrNull(1) ?: return
        val headers = generateSequence { input.readLine()?.takeIf(String::isNotEmpty) }
            .associate { line -> line.substringBefore(":").lowercase() to line.substringAfter(":").trim() }
        val r = handle(path, headers)
        val out = it.getOutputStream()
        val head = buildString {
            append("HTTP/1.1 ${r.status} X\r\nConnection: close\r\nContent-Length: ${r.body.size}\r\n")
            r.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(r.body)
        out.flush()
    }

    fun stop() = socket.close()
}

class UpdateHttpTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: TestServer
    private var body = ByteArray(0)
    private var etag = "\"v1\""
    private val requests = mutableListOf<Map<String, String?>>()

    private val base get() = "http://127.0.0.1:${server.port}"

    @Before
    fun start() {
        body = ByteArray(300_000) { (it * 31 + 7).toByte() }
        server = TestServer { path, h ->
            when (path) {
                "/app.apk" -> {
                    requests += mapOf("Range" to h["range"], "If-Range" to h["if-range"])
                    val range = h["range"]
                    if (range != null && h["if-range"] == etag) {
                        val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                        TestServer.Response(206, mapOf("ETag" to etag), body.copyOfRange(from, body.size))
                    } else {
                        TestServer.Response(200, mapOf("ETag" to etag), body)
                    }
                }
                "/feed.json" ->
                    if (h["if-none-match"] == etag) {
                        TestServer.Response(304)
                    } else {
                        TestServer.Response(200, mapOf("ETag" to etag), """{"a":1}""".toByteArray())
                    }
                else -> TestServer.Response(404)
            }
        }
    }

    @After
    fun stop() = server.stop()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun download(part: java.io.File, etagFile: java.io.File, cancelled: () -> Boolean = { false }) =
        UpdateHttp.download("$base/app.apk", part, etagFile, { _, _ -> }, cancelled)

    @Test
    fun downloadsAWholeFileAndHashesItWhileStreaming() {
        val part = tmp.newFile("1.apk.part").also { it.delete() }
        val etagFile = tmp.newFile("1.apk.part.etag").also { it.delete() }
        val digest = download(part, etagFile)
        assertEquals(sha(body), digest)
        assertArrayEquals(body, part.readBytes())
        assertEquals(etag, etagFile.readText())
        assertNull(requests.single()["Range"])
    }

    @Test
    fun resumesFromAPartialFileWithRangeAndIfRange() {
        val part = tmp.newFile("2.apk.part").apply { writeBytes(body.copyOf(100_000)) }
        val etagFile = tmp.newFile("2.apk.part.etag").apply { writeText(etag) }
        val digest = download(part, etagFile)
        assertEquals("bytes=100000-", requests.single()["Range"])
        assertEquals(etag, requests.single()["If-Range"])
        assertEquals(sha(body), digest)
        assertArrayEquals(body, part.readBytes())
    }

    @Test
    fun restartsWhenTheServerFileChanged() {
        val stale = ByteArray(100_000) { 9 }
        val part = tmp.newFile("3.apk.part").apply { writeBytes(stale) }
        val etagFile = tmp.newFile("3.apk.part.etag").apply { writeText("\"old\"") }
        val digest = download(part, etagFile)
        assertEquals("bytes=100000-", requests.single()["Range"])
        assertEquals(sha(body), digest)
        assertArrayEquals(body, part.readBytes())
        assertEquals(etag, etagFile.readText())
    }

    @Test
    fun ignoresAPartialFileWithNoRecordedVersion() {
        val part = tmp.newFile("4.apk.part").apply { writeBytes(ByteArray(5_000) { 1 }) }
        val etagFile = tmp.newFile("4.apk.part.etag").also { it.delete() }
        assertEquals(sha(body), download(part, etagFile))
        assertNull(requests.single()["Range"])
        assertArrayEquals(body, part.readBytes())
    }

    @Test
    fun cancellationStopsAndKeepsThePartialFileForLater() {
        val part = tmp.newFile("5.apk.part").also { it.delete() }
        val etagFile = tmp.newFile("5.apk.part.etag").also { it.delete() }
        try {
            download(part, etagFile) { true }
            fail("expected a stop")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("stopped"))
        }
        assertTrue(etagFile.exists())
    }

    @Test
    fun serverErrorsSurfaceAsIoExceptions() {
        val part = tmp.newFile("6.apk.part").also { it.delete() }
        try {
            UpdateHttp.download("$base/missing", part, tmp.newFile("6.etag").also { it.delete() }, { _, _ -> }, { false })
            fail("expected an error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test
    fun feedFetchUsesEtagAndHonoursNotModified() {
        val first = UpdateHttp.fetchText("$base/feed.json") as Fetched.Body
        assertEquals("""{"a":1}""", first.text)
        assertEquals(etag, first.etag)
        assertEquals(Fetched.NotModified, UpdateHttp.fetchText("$base/feed.json", first.etag))
    }

    @Test
    fun feedFetchFailsOnHttpErrors() {
        try {
            UpdateHttp.fetchText("$base/missing")
            fail("expected an error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test
    fun sha256OfMatchesStreamedDigest() {
        val f = tmp.newFile("x").apply { writeBytes(body) }
        assertEquals(sha(body), sha256Of(f))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Of(tmp.newFile("empty")))
    }
}
