package com.serendeep.marginalia.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {

    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun movingDownLandsOnTheTargetSlot() {
        assertEquals(listOf("b", "c", "a", "d"), ids.moved("a", "c"))
    }

    @Test
    fun movingUpLandsOnTheTargetSlot() {
        assertEquals(listOf("d", "a", "b", "c"), ids.moved("d", "a"))
    }

    @Test
    fun unknownIdsLeaveTheOrderAlone() {
        assertEquals(ids, ids.moved("a", "z"))
        assertEquals(ids, ids.moved("z", "a"))
    }

    @Test
    fun movingOntoItselfIsANoOp() {
        assertEquals(ids, ids.moved("b", "b"))
    }
}
