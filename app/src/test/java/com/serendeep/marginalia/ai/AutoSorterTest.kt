package com.serendeep.marginalia.ai

import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.library.LibraryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSorterTest {
    private val unsorted = CourseEntity("u", LibraryViewModel.UNSORTED_NAME, 0, 0, colorIndex = 0)
    private val algo = CourseEntity("a", "Algorithms", 0, 1, colorIndex = 0)
    private val bio = CourseEntity("b", "Biology", 0, 2, colorIndex = 1)
    private val courses = listOf(unsorted, algo, bio)

    private fun decision(course: String? = null, new: String? = null, emoji: String? = null) =
        SortDecision(course, new, emoji, emptyList(), null)

    @Test
    fun existingCourseMatchesCaseInsensitively() {
        assertEquals(algo, pickCourse(decision(course = "algorithms"), courses)!!.existing)
    }

    @Test
    fun newCourseGetsNextUnusedColourAndEmoji() {
        val pick = pickCourse(decision(new = "Quantum", emoji = "⚛"), courses)!!
        assertNull(pick.existing)
        assertEquals("Quantum", pick.newName)
        assertEquals("⚛", pick.emoji)
        assertEquals(2, pick.colorIndex)
    }

    @Test
    fun newCourseNamedLikeExistingReusesIt() {
        assertEquals(bio, pickCourse(decision(new = "BIOLOGY"), courses)!!.existing)
    }

    @Test
    fun neverFilesIntoUnsortedOrNothing() {
        assertNull(pickCourse(decision(course = "Unsorted"), courses))
        assertNull(pickCourse(decision(new = "unsorted"), courses))
        assertNull(pickCourse(decision(), courses))
        assertNotNull(pickCourse(decision(new = "X"), emptyList()))
    }

    @Test
    fun colourWrapsWhenPaletteIsFull() {
        assertEquals(3, nextColorIndex(setOf(0, 1, 2), 8))
        assertEquals(0, nextColorIndex(setOf(0, 1), 2))
    }
}
