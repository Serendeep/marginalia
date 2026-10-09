package com.serendeep.marginalia.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    @Test
    fun announcesOnlyAnUpgrade() {
        assertTrue(justUpdated(lastSeen = 12, installed = 13))
        assertFalse(justUpdated(lastSeen = 13, installed = 13))
        assertFalse(justUpdated(lastSeen = 0, installed = 13))
        assertFalse(justUpdated(lastSeen = 14, installed = 13))
    }
}
