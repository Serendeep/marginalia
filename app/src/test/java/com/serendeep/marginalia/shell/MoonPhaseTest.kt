package com.serendeep.marginalia.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MoonPhaseTest {
    private fun at(iso: String) = moonPhase(Instant.parse(iso).toEpochMilli())

    @Test
    fun knownNewAndFullMoons() {
        // Published new and full moons (UTC); the mean cycle stays within about a day.
        val new = at("2024-01-11T11:57:00Z")
        assertTrue("new moon age ${new.age}", new.age < 1.2 || new.age > 28.3)
        assertTrue(new.illumination < 0.03)
        val full = at("2024-01-25T17:54:00Z")
        assertTrue("full moon fraction ${full.fraction}", full.fraction in 0.46..0.54)
        assertTrue(full.illumination > 0.97)
    }

    @Test
    fun namesAndSides() {
        val firstQuarter = at("2024-01-18T03:53:00Z")
        assertEquals("First quarter", firstQuarter.name)
        assertTrue(firstQuarter.waxing)
        val lastQuarter = at("2024-02-02T23:18:00Z")
        assertEquals("Last quarter", lastQuarter.name)
        assertTrue(!lastQuarter.waxing)
    }

    @Test
    fun labelRoundsTheLitShare() {
        assertTrue(at("2024-01-25T17:54:00Z").label.startsWith("Full moon, 1"))
    }
}
