package com.serendeep.marginalia.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScratchOutTest {

    @Test
    fun zigzagIsAScratch() {
        val box = ScratchOut.detect(Traces.zigzag(strokes = 6))
        assertNotNull(box)
        assertEquals(120f, box!!.width, 6f)
    }

    @Test
    fun denseVerticalScratchIsAScratch() {
        val verts = (0..8).map { (100f + it * 4f) to (if (it % 2 == 0) 100f else 180f) }
        assertNotNull(ScratchOut.detect(Traces.walk(verts, noise = 1f)))
    }

    @Test
    fun fewSweepsAreNotEnough() {
        assertNull(ScratchOut.detect(Traces.zigzag(strokes = 3, height = 30f)))
    }

    @Test
    fun handwrittenMmIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.humps(4)))
    }

    @Test
    fun handwrittenWwwIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.humps(6, pitch = 18f, height = 30f, cusp = 4f)))
    }

    @Test
    fun longRunOfLoopsIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.humps(10, pitch = 14f, height = 24f)))
    }

    @Test
    fun straightUnderlineIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.walk(listOf(100f to 100f, 300f to 102f))))
    }

    @Test
    fun flatBackAndForthIsTooThin() {
        val verts = (0..8).map { (if (it % 2 == 0) 100f else 220f) to 100f + (it % 3) }
        assertNull(ScratchOut.detect(Traces.walk(verts, noise = 0.5f)))
    }

    @Test
    fun circleIsNotAScratch() {
        assertNull(ScratchOut.detect(Traces.circle(200f, 200f, 50f)))
    }

    @Test
    fun reversalsIgnoreSmallWobble() {
        val v = floatArrayOf(0f, 10f, 9f, 11f, 10f, 30f, 29f, 31f)
        assertEquals(0, ScratchOut.reversals(v, 8f))
        assertEquals(2, ScratchOut.reversals(floatArrayOf(0f, 50f, 0f, 50f), 20f))
    }

    @Test
    fun coverageNeedsSixtyPercentInsideTheInflatedBox() {
        val area = Extent(100f, 100f, 200f, 140f)
        val inside = (0..9).map { InkPt(110f + it * 8f, 120f) }
        assertTrue(ScratchOut.covers(area, inside))
        val most = inside.take(6) + (0..3).map { InkPt(400f, 400f) }
        assertTrue(ScratchOut.covers(area, most))
        val half = inside.take(5) + (0..4).map { InkPt(400f, 400f) }
        assertFalse(ScratchOut.covers(area, half))
    }

    @Test
    fun inflationCatchesStrokesJustOutsideTheBox() {
        val area = Extent(100f, 100f, 200f, 140f)
        val edge = (0..9).map { InkPt(110f + it * 8f, 97f) }
        assertTrue(ScratchOut.covers(area, edge))
        val off = (0..9).map { InkPt(110f + it * 8f, 90f) }
        assertFalse(ScratchOut.covers(area, off))
    }

    @Test
    fun cursiveElfIsNotAScratch() {
        // Joined-up "elf" from a script font: its loops reverse direction, but each loop is small next to the word.
        assertNull(ScratchOut.detect(cursiveElf))
    }

    private val cursiveElf = listOf(InkPt(1468.4f,1472.8f,0L),InkPt(1471.9f,1470.9f,6L),InkPt(1473.5f,1469.0f,12L),InkPt(1474.7f,1465.1f,18L),InkPt(1474.0f,1461.2f,24L),InkPt(1471.8f,1459.3f,30L),InkPt(1469.8f,1459.3f,36L),InkPt(1466.3f,1461.2f,42L),InkPt(1465.0f,1465.1f,48L),InkPt(1466.1f,1470.9f,54L),InkPt(1468.7f,1474.8f,60L),InkPt(1473.0f,1476.7f,66L),InkPt(1476.8f,1476.7f,72L),InkPt(1480.4f,1474.8f,78L),InkPt(1482.0f,1472.8f,84L),InkPt(1483.4f,1469.9f,90L),InkPt(1484.8f,1467.0f,96L),InkPt(1486.2f,1464.1f,102L),InkPt(1487.6f,1461.2f,108L),InkPt(1489.0f,1458.0f,114L),InkPt(1490.3f,1454.7f,120L),InkPt(1491.7f,1451.5f,126L),InkPt(1492.9f,1447.6f,132L),InkPt(1493.8f,1441.8f,138L),InkPt(1493.1f,1437.9f,144L),InkPt(1490.8f,1436.0f,150L),InkPt(1487.3f,1437.9f,156L),InkPt(1486.1f,1441.8f,162L),InkPt(1485.8f,1445.7f,168L),InkPt(1485.5f,1449.6f,174L),InkPt(1485.7f,1453.0f,180L),InkPt(1485.8f,1456.4f,186L),InkPt(1485.9f,1459.8f,192L),InkPt(1486.0f,1463.2f,198L),InkPt(1486.7f,1467.0f,204L),InkPt(1487.4f,1470.9f,210L),InkPt(1488.1f,1474.8f,216L),InkPt(1490.4f,1476.7f,222L),InkPt(1492.4f,1476.7f,228L),InkPt(1495.9f,1474.8f,234L),InkPt(1497.5f,1472.8f,240L),InkPt(1498.9f,1469.9f,246L),InkPt(1500.3f,1467.0f,252L),InkPt(1502.3f,1463.8f,258L),InkPt(1504.3f,1460.6f,264L),InkPt(1506.3f,1457.3f,270L),InkPt(1507.7f,1454.4f,276L),InkPt(1509.2f,1451.5f,282L),InkPt(1510.4f,1447.6f,288L),InkPt(1511.3f,1441.8f,294L),InkPt(1510.6f,1437.9f,300L),InkPt(1508.3f,1436.0f,306L),InkPt(1504.8f,1437.9f,312L),InkPt(1503.5f,1441.8f,318L),InkPt(1503.3f,1444.9f,324L),InkPt(1503.1f,1448.0f,330L),InkPt(1502.9f,1451.1f,336L),InkPt(1502.7f,1454.2f,342L),InkPt(1502.4f,1457.3f,348L),InkPt(1501.9f,1460.8f,354L),InkPt(1501.4f,1464.3f,360L),InkPt(1500.8f,1467.8f,366L),InkPt(1500.3f,1471.3f,372L),InkPt(1499.8f,1474.8f,378L),InkPt(1498.9f,1478.2f,384L),InkPt(1498.1f,1481.6f,390L),InkPt(1497.2f,1485.0f,396L),InkPt(1496.4f,1488.4f,402L),InkPt(1495.5f,1494.2f,408L),InkPt(1496.2f,1498.1f,414L),InkPt(1498.5f,1500.0f,420L),InkPt(1502.0f,1498.1f,426L),InkPt(1502.9f,1492.2f,432L),InkPt(1502.7f,1488.8f,438L),InkPt(1502.4f,1485.3f,444L),InkPt(1502.2f,1481.8f,450L),InkPt(1501.9f,1478.3f,456L),InkPt(1501.7f,1474.8f,462L),InkPt(1504.0f,1476.7f,468L),InkPt(1507.9f,1476.7f,474L),InkPt(1511.4f,1474.8f,480L),InkPt(1513.0f,1472.8f,486L),InkPt(1514.4f,1469.9f,492L),InkPt(1515.8f,1467.0f,498L))
}
