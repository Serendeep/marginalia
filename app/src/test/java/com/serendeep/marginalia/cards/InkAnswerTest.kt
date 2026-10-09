package com.serendeep.marginalia.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class InkAnswerTest {

    @Test
    fun fitShrinksToTheTighterAxis() {
        assertEquals(0.5f, fitScale(400f, 100f, 200f, 200f), 0f)
    }

    @Test
    fun fitNeverEnlargesPastTheCap() {
        assertEquals(1.5f, fitScale(10f, 10f, 500f, 500f), 0f)
    }

    @Test
    fun emptyBoundsKeepScaleOne() {
        assertEquals(1f, fitScale(0f, 0f, 200f, 200f), 0f)
    }
}
