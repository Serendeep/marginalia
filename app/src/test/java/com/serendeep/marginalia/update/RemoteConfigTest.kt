package com.serendeep.marginalia.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigTest {
    @Test
    fun emptyObjectGivesSafeDefaults() {
        val c = RemoteConfig.parse("{}")!!
        assertEquals(RemoteConfig(), c)
        assertFalse(c.mustUpdate(1))
        assertTrue(c.aiAllowed)
        assertTrue(c.flags.autoSort)
        assertTrue(c.flags.handwritingSearch)
    }

    @Test
    fun unparseableTextIsRejected() {
        assertNull(RemoteConfig.parse("<html>"))
        assertNull(RemoteConfig.parse(""))
    }

    @Test
    fun parsesEveryField() {
        val c = RemoteConfig.parse(
            """{"minSupportedVersionCode": 5, "knownBadVersionCodes": [3, 9],
                "message": {"text": "Heads up", "url": "https://example.com/n"},
                "flags": {"ai": false, "agent": true, "autoSort": false, "handwritingSearch": false},
                "somethingNew": {"x": 1}}""",
        )!!
        assertEquals(5L, c.minSupportedVersionCode)
        assertEquals(setOf(3L, 9L), c.knownBadVersionCodes)
        assertEquals(RemoteMessage("Heads up", "https://example.com/n"), c.message)
        assertFalse(c.flags.ai)
        assertFalse(c.flags.autoSort)
        assertFalse(c.flags.handwritingSearch)
    }

    @Test
    fun missingFlagsKeepTheirDefaults() {
        val c = RemoteConfig.parse("""{"flags": {"autoSort": false}}""")!!
        assertFalse(c.flags.autoSort)
        assertTrue(c.flags.ai)
        assertTrue(c.flags.agent)
        assertTrue(c.flags.handwritingSearch)
    }

    @Test
    fun wrongTypesFallBackInsteadOfFailing() {
        val c = RemoteConfig.parse("""{"minSupportedVersionCode": "soon", "knownBadVersionCodes": "none", "message": "hi", "flags": 3}""")!!
        assertEquals(RemoteConfig(), c)
    }

    @Test
    fun blankMessageAndUnsafeLinkAreDropped() {
        assertNull(RemoteConfig.parse("""{"message": {"text": "  "}}""")!!.message)
        assertNull(RemoteConfig.parse("""{"message": {"text": "x", "url": "http://example.com"}}""")!!.message!!.url)
    }

    @Test
    fun mustUpdateBelowMinimumOrOnKnownBadVersion() {
        val c = RemoteConfig(minSupportedVersionCode = 10, knownBadVersionCodes = setOf(12))
        assertTrue(c.mustUpdate(9))
        assertFalse(c.mustUpdate(10))
        assertTrue(c.mustUpdate(12))
        assertFalse(c.mustUpdate(13))
    }

    @Test
    fun aiNeedsBothFlags() {
        assertFalse(RemoteConfig(flags = RemoteFlags(ai = false)).aiAllowed)
        assertFalse(RemoteConfig(flags = RemoteFlags(agent = false)).aiAllowed)
        assertTrue(RemoteConfig().aiAllowed)
    }
}
