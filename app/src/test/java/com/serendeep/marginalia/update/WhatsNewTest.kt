package com.serendeep.marginalia.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    private fun feed(code: Long, notes: String?) = UpdateInfo(code, "1.3.0", "https://x/app.apk", "a".repeat(64), 1, notes, null)

    @Test
    fun announcesOnlyAnUpgrade() {
        assertTrue(justUpdated(lastSeen = 12, installed = 13))
        assertFalse(justUpdated(lastSeen = 13, installed = 13))
        assertFalse(justUpdated(lastSeen = 0, installed = 13))
        assertFalse(justUpdated(lastSeen = 14, installed = 13))
    }

    @Test
    fun usesTheFeedNotesOnlyForTheInstalledVersion() {
        assertEquals("https://x/notes", whatsNewUrl(feed(13, "https://x/notes"), 13, "1.3.0"))
        assertEquals(
            "https://github.com/Serendeep/marginalia/releases/tag/v1.3.0",
            whatsNewUrl(feed(14, "https://x/notes"), 13, "1.3.0"),
        )
        assertEquals(
            "https://github.com/Serendeep/marginalia/releases/tag/v1.3.0",
            whatsNewUrl(null, 13, "1.3.0"),
        )
    }
}
