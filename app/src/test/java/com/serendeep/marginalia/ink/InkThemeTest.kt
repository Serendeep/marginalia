package com.serendeep.marginalia.ink

import androidx.compose.ui.graphics.toArgb
import com.serendeep.marginalia.ui.theme.PenGraphiteDark
import com.serendeep.marginalia.ui.theme.PenGraphiteLight
import com.serendeep.marginalia.ui.theme.PenIndigoLight
import com.serendeep.marginalia.ui.theme.PenIndigoDark
import org.junit.Assert.assertEquals
import org.junit.Test

class InkThemeTest {
    @Test fun `graphite written on dark paper turns dark on light paper`() {
        assertEquals(PenGraphiteLight.toArgb(), InkTheme.adapt(PenGraphiteDark.toArgb(), dark = false))
        assertEquals(PenGraphiteDark.toArgb(), InkTheme.adapt(PenGraphiteLight.toArgb(), dark = true))
    }

    @Test fun `palette pen keeps its alpha`() {
        val translucent = (PenIndigoDark.toArgb() and 0x00FFFFFF) or 0x80000000.toInt()
        assertEquals((PenIndigoLight.toArgb() and 0x00FFFFFF) or 0x80000000.toInt(), InkTheme.adapt(translucent, dark = false))
    }

    @Test fun `fixed colours are untouched`() {
        assertEquals(0xFFE5484D.toInt(), InkTheme.adapt(0xFFE5484D.toInt(), dark = false))
        assertEquals(0xFFF2F2F5.toInt(), InkTheme.adapt(0xFFF2F2F5.toInt(), dark = false))
    }
}
