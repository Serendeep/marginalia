package com.serendeep.marginalia.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    private fun ratio(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance().toDouble()
        val b = bg.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun assertAtLeast(min: Double, name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name is %.2f:1, needs $min".format(r), r >= min)
    }

    @Test
    fun lightBodyText() {
        for ((bgName, bg) in listOf("bg" to BgLight, "sheet" to SheetLight)) {
            assertAtLeast(4.5, "ink on $bgName", InkLight, bg)
            assertAtLeast(4.5, "soft ink on $bgName", SoftInkLight, bg)
            assertAtLeast(4.5, "dim ink on $bgName", LightExtras.dimInk, bg)
            assertAtLeast(4.5, "chip text on $bgName", LightExtras.chipText, bg)
            assertAtLeast(4.5, "violet on $bgName", VioletLight, bg)
            assertAtLeast(4.5, "lime ink on $bgName", LightExtras.limeInk, bg)
            assertAtLeast(4.5, "danger on $bgName", LightExtras.danger, bg)
            assertAtLeast(4.5, "error ink on $bgName", LightExtras.errorInk, bg)
        }
        assertAtLeast(4.5, "ink on surface2", InkLight, Surface2Light)
        assertAtLeast(4.5, "soft ink on surface2", SoftInkLight, Surface2Light)
        assertAtLeast(4.5, "white on violet", Color.White, VioletLight)
    }

    @Test
    fun lightGraphics() {
        assertAtLeast(3.0, "violet on surface2", VioletLight, Surface2Light)
        assertAtLeast(3.0, "lime ink on surface2", LightExtras.limeInk, Surface2Light)
        assertAtLeast(3.0, "pen graphite on sheet", PenGraphiteLight, SheetLight)
        assertAtLeast(3.0, "pen indigo on sheet", PenIndigoLight, SheetLight)
        assertAtLeast(3.0, "pen rust on sheet", PenRustLight, SheetLight)
        assertAtLeast(3.0, "top heat cell on bg", LightExtras.heat.last(), BgLight)
    }

    @Test
    fun focusTileKeepsDarkText() {
        assertAtLeast(4.5, "focus label", Color(0xFF4A5A00), Lime)
        assertAtLeast(4.5, "focus caption", Color(0xFF3B4700), Lime)
        assertAtLeast(4.5, "focus time", Color(0xFF131600), Lime)
    }

    @Test
    fun darkBodyText() {
        for ((bgName, bg) in listOf("bg" to BgDark, "sheet" to SheetDark)) {
            assertAtLeast(4.5, "ink on $bgName", InkDark, bg)
            assertAtLeast(4.5, "soft ink on $bgName", SoftInkDark, bg)
            assertAtLeast(4.5, "violet on $bgName", Violet, bg)
            assertAtLeast(4.5, "lime on $bgName", Lime, bg)
            assertAtLeast(4.5, "chip text on $bgName", DarkExtras.chipText, bg)
        }
        assertAtLeast(4.5, "on-violet on violet", OnViolet, Violet)
    }
}
