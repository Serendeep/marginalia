package com.serendeep.marginalia.update

import org.junit.Assert.assertEquals
import org.junit.Test

class PenActivityTest {
    @Test
    fun waitsUntilSixtySecondsAfterTheLastTouch() {
        assertEquals(60_000L, PenActivity.msUntilIdle(lastAt = 1_000, now = 1_000))
        assertEquals(30_000L, PenActivity.msUntilIdle(lastAt = 1_000, now = 31_000))
        assertEquals(0L, PenActivity.msUntilIdle(lastAt = 1_000, now = 61_000))
        assertEquals(0L, PenActivity.msUntilIdle(lastAt = 1_000, now = 500_000))
    }

    @Test
    fun aNeverUsedPenIsIdle() {
        assertEquals(0L, PenActivity.msUntilIdle(lastAt = Long.MIN_VALUE / 2, now = 5))
    }
}
