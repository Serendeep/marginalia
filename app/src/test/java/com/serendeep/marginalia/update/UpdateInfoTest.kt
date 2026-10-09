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

    private fun patch(from: Int, url: String = "https://example.com/p$from.zst", sha: String = this.sha, size: Int = 1_200_000) =
        """{"fromVersionCode": $from, "url": "$url", "sha256": "$sha", "size": $size}"""

    @Test
    fun patchesAreOptional() {
        assertTrue(UpdateInfo.parse(feed())!!.patches.isEmpty())
        assertTrue(UpdateInfo.parse(feed("\"patches\": []"))!!.patches.isEmpty())
    }

    @Test
    fun parsesPatchesAndPicksTheOneForTheInstalledVersion() {
        val info = UpdateInfo.parse(feed("\"size\": 22000000", "\"patches\": [${patch(5)}, ${patch(6)}]"))!!
        assertEquals(listOf(5L, 6L), info.patches.map { it.fromVersionCode })
        assertEquals("https://example.com/p6.zst", info.patchFor(6)!!.url)
        assertNull(info.patchFor(4))
        assertEquals(1_200_000L, info.downloadSize(installed = 6))
        assertEquals(22_000_000L, info.downloadSize(installed = 4))
        assertEquals(22_000_000L, info.downloadSize(installed = 6, usePatch = false))
        assertEquals(info, UpdateInfo.parse(info.toJson()))
    }

    @Test
    fun dropsPatchesItCannotTrust() {
        val info = UpdateInfo.parse(
            feed(
                "\"patches\": [${patch(5, url = "http://example.com/p.zst")}, ${patch(6, sha = "abc")}, ${patch(0)}, ${patch(4)}, 7]",
            ),
        )!!
        assertEquals(listOf(4L), info.patches.map { it.fromVersionCode })
    }
}
