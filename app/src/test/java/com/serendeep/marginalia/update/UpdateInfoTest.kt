package com.serendeep.marginalia.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {
    private val sha = "a".repeat(64)

    private fun feed(vararg extra: String) =
        """{"versionCode": 7, "apkUrl": "https://example.com/app.apk", "sha256": "$sha"${extra.joinToString("") { ",$it" }}}"""

    @Test
    fun parsesAFullFeed() {
        val info = UpdateInfo.parse(
            """{"versionCode": 12, "versionName": "1.2.0", "apkUrl": "https://example.com/app.apk", "sha256": "${sha.uppercase()}",
               "size": 21000000, "certSha256": "x", "notesUrl": "https://example.com/notes", "publishedAt": "2026-10-01T00:00:00Z", "future": 1}""",
        )!!
        assertEquals(12L, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertEquals(sha, info.sha256)
        assertEquals(21_000_000L, info.size)
        assertEquals("https://example.com/notes", info.notesUrl)
    }

    @Test
    fun optionalFieldsMayBeMissing() {
        val info = UpdateInfo.parse(feed())!!
        assertEquals("7", info.versionName)
        assertEquals(-1L, info.size)
        assertNull(info.notesUrl)
        assertNull(info.publishedAt)
    }

    @Test
    fun roundTripsThroughJson() {
        val info = UpdateInfo.parse(feed("\"versionName\": \"1.0\"", "\"size\": 5"))!!
        assertEquals(info, UpdateInfo.parse(info.toJson()))
    }

    @Test
    fun rejectsFeedsMissingRequiredFields() {
        assertNull(UpdateInfo.parse("""{"apkUrl": "https://example.com/a.apk", "sha256": "$sha"}"""))
        assertNull(UpdateInfo.parse("""{"versionCode": 7, "sha256": "$sha"}"""))
        assertNull(UpdateInfo.parse("""{"versionCode": 7, "apkUrl": "https://example.com/a.apk"}"""))
        assertNull(UpdateInfo.parse("""{"versionCode": 7, "apkUrl": "https://example.com/a.apk", "sha256": "abc"}"""))
        assertNull(UpdateInfo.parse("not json"))
        assertNull(UpdateInfo.parse(""))
    }

    @Test
    fun onlyHttpsOrLoopbackUrlsAreAccepted() {
        assertTrue(UpdateInfo.isSafeUrl("https://github.com/x"))
        assertTrue(UpdateInfo.isSafeUrl("http://127.0.0.1:8765/app.apk"))
        assertTrue(UpdateInfo.isSafeUrl("http://localhost/app.apk"))
        assertFalse(UpdateInfo.isSafeUrl("http://example.com/app.apk"))
        assertFalse(UpdateInfo.isSafeUrl("file:///sdcard/app.apk"))
        assertFalse(UpdateInfo.isSafeUrl("javascript:alert(1)"))
        assertNull(UpdateInfo.parse("""{"versionCode": 7, "apkUrl": "http://example.com/a.apk", "sha256": "$sha"}"""))
    }

    @Test
    fun neverOffersADowngradeOrTheSameVersion() {
        assertTrue(isNewer(8, 7))
        assertFalse(isNewer(7, 7))
        assertFalse(isNewer(6, 7))
        assertNotNull(UpdateInfo.parse(feed()))
    }
}
