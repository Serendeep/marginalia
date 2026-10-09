package com.serendeep.marginalia.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelTest {
    private val sha = "a".repeat(64)

    @Test
    fun savedChoiceWinsOverTheBuildDefault() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.parse("stable", "nightly"))
        assertEquals(UpdateChannel.NIGHTLY, UpdateChannel.parse("nightly", "stable"))
    }

    @Test
    fun fallsBackToTheBuildChannelThenStable() {
        assertEquals(UpdateChannel.NIGHTLY, UpdateChannel.parse(null, "nightly"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.parse("beta", "stable"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.parse(null, "junk"))
    }

    @Test
    fun picksTheFeedOfTheChannel() {
        assertEquals("s", feedUrl(UpdateChannel.STABLE, "s", "n"))
        assertEquals("n", feedUrl(UpdateChannel.NIGHTLY, "s", "n"))
    }

    @Test
    fun noteAppearsOnlyWhenANightlyBuildFollowsStable() {
        assertTrue(switchBackNote(UpdateChannel.NIGHTLY, UpdateChannel.STABLE)!!.startsWith("You'll move to Stable"))
        assertNull(switchBackNote(UpdateChannel.NIGHTLY, UpdateChannel.NIGHTLY))
        assertNull(switchBackNote(UpdateChannel.STABLE, UpdateChannel.STABLE))
        assertNull(switchBackNote(UpdateChannel.STABLE, UpdateChannel.NIGHTLY))
    }

    @Test
    fun installedLineNamesVersionAndChannel() {
        assertEquals("Installed: 1.3.0-nightly.214+abc1234 · nightly", installedLine("1.3.0-nightly.214+abc1234", UpdateChannel.NIGHTLY))
    }

    @Test
    fun feedChannelIsParsedAndKeptThroughJson() {
        val json = """{"channel": "nightly", "versionCode": 1970, "apkUrl": "https://example.com/app.apk", "sha256": "$sha"}"""
        val info = UpdateInfo.parse(json)!!
        assertEquals("nightly", info.channel)
        assertEquals("nightly", UpdateInfo.parse(info.toJson())!!.channel)
    }

    @Test
    fun feedWithoutChannelStillParses() {
        val info = UpdateInfo.parse("""{"versionCode": 7, "apkUrl": "https://example.com/app.apk", "sha256": "$sha"}""")!!
        assertNull(info.channel)
    }

    @Test
    fun aNightlyNeverOffersALowerCode() {
        assertTrue(isNewer(1975, 1970))
        assertEquals(false, isNewer(1960, 1970))
    }
}
