package com.serendeep.marginalia.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PublishableTest {
    @Test
    fun publishesUpToLastBlankLine() {
        assertEquals("one\n\ntwo\n\n", publishable("one\n\ntwo\n\nthr", 0))
    }

    @Test
    fun noBoundaryPublishesNothingUntilTimeout() {
        assertEquals("", publishable("partial para", 0))
        assertEquals("", publishable("partial para", PUBLISH_TIMEOUT_MS - 1))
        assertEquals("partial para", publishable("partial para", PUBLISH_TIMEOUT_MS))
    }

    @Test
    fun blankLinesInsideFenceAreNotBoundaries() {
        assertEquals("intro\n\n", publishable("intro\n\n```kotlin\nval a = 1\n\nval b = 2\n", 0))
        assertEquals("intro\n\n```kotlin\na\n\nb\n```\n\n", publishable("intro\n\n```kotlin\na\n\nb\n```\n\nafter", 0))
    }

    @Test
    fun blankLinesInsideMathBlockAreNotBoundaries() {
        assertEquals("a\n\n", publishable("a\n\n\$\$\nx = 1\n\ny = 2\n", 0))
        assertEquals("a\n\n\$\$\nx\n\$\$\n\n", publishable("a\n\n\$\$\nx\n\$\$\n\nb", 0))
        assertEquals("a\n\n\$\$x\$\$\n\n", publishable("a\n\n\$\$x\$\$\n\nb", 0))
    }

    @Test
    fun existingBoundaryBeatsTimeout() {
        assertEquals("a\n\n", publishable("a\n\n```\nx\n\ny", PUBLISH_TIMEOUT_MS))
    }

    @Test
    fun emptyBuffer() {
        assertEquals("", publishable("", 0))
    }
}
