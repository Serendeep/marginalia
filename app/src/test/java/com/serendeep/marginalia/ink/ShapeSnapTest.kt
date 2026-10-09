package com.serendeep.marginalia.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class ShapeSnapTest {

    private fun snapped(points: List<InkPt>) = ShapeSnap.snap(Traces.hold(points))

    @Test
    fun straightLineWithHoldBecomesTwoEndpointLine() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 380f), noise = 1.5f)
        val out = snapped(raw)!!
        assertEquals(raw.first().x, out.first().x, 3f)
        assertEquals(out.first().y, out.first().y, 0.01f)
        // Every synthesized point sits on the chord between the endpoints.
        val a = out.first()
        val b = out.last()
        out.forEach { assertTrue(distanceToLine(it, a, b) < 0.5f) }
        assertTrue(maxStep(out) <= ShapeSnap.SPACING_PX + 0.01f)
    }

    @Test
    fun holdTailIsStrippedFromTheOutput() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 400f))
        val out = snapped(raw)!!
        assertEquals(raw.last().x, out.last().x, 3f)
    }

    @Test
    fun noHoldMeansNoSnap() {
        assertNull(ShapeSnap.snap(Traces.walk(listOf(50f to 400f, 350f to 400f))))
        assertNull(ShapeSnap.snap(Traces.circle(300f, 300f, 80f)))
    }

    @Test
    fun shortHoldIsNotEnough() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 400f))
        assertNull(ShapeSnap.snap(Traces.hold(raw, ms = 300L)))
    }

    @Test
    fun slowDriftIsNotAHold() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 400f), msPerPoint = 8)
        val drifting = raw + (1..10).map { InkPt(raw.last().x + it * 8f, raw.last().y, raw.last().t + it * 80L) }
        assertNull(ShapeSnap.snap(drifting))
    }

    @Test
    fun arrowKeepsShaftAndBothHeadSegments() {
        val out = snapped(Traces.arrow())!!
        val box = Extent.of(out)
        assertEquals(100f, box.left, 4f)
        assertEquals(400f, box.right, 4f)
        // The head fans out on both sides of the shaft.
        assertTrue(box.top < 280f)
        assertTrue(box.bottom > 320f)
    }

    @Test
    fun circleBecomesAClosedRound() {
        val out = snapped(Traces.circle(300f, 300f, 80f))!!
        val box = Extent.of(out)
        assertEquals(160f, box.width, 8f)
        assertEquals(160f, box.height, 8f)
        out.forEach { assertEquals(80f, hypot(it.x - 300f, it.y - 300f), 8f) }
        assertEquals(out.first().x, out.last().x, 0.5f)
        assertEquals(out.first().y, out.last().y, 0.5f)
    }

    @Test
    fun ellipseKeepsItsAspect() {
        val out = snapped(Traces.ellipse(300f, 300f, 120f, 60f))!!
        val box = Extent.of(out)
        assertEquals(240f, box.width, 10f)
        assertEquals(120f, box.height, 10f)
    }

    @Test
    fun squareBecomesAxisAlignedRectangle() {
        val sq = listOf(100f to 100f, 260f to 104f, 258f to 262f, 98f to 258f)
        val out = snapped(Traces.polygon(sq))!!
        val box = Extent.of(out)
        assertEquals(160f, box.width, 10f)
        assertEquals(160f, box.height, 10f)
        // Straight edges: almost every point lies on the bounding box outline.
        val onEdge = out.count {
            abs(it.x - box.left) < 0.5f || abs(it.x - box.right) < 0.5f ||
                abs(it.y - box.top) < 0.5f || abs(it.y - box.bottom) < 0.5f
        }
        assertEquals(out.size, onEdge)
    }

    @Test
    fun wideRectangleSnaps() {
        val rect = listOf(80f to 100f, 380f to 100f, 380f to 200f, 80f to 200f)
        val out = snapped(Traces.polygon(rect))!!
        val box = Extent.of(out)
        assertEquals(300f, box.width, 10f)
        assertEquals(100f, box.height, 10f)
    }

    @Test
    fun clearlyRotatedRectangleStaysRotated() {
        val base = listOf(100f to 100f, 300f to 100f, 300f to 200f, 100f to 200f)
        val rot = Traces.rotated(base, 30f, 200f, 150f)
        val out = snapped(Traces.polygon(rot))!!
        val box = Extent.of(out)
        // An axis-aligned fit of this shape would be 100*cos+200*sin wide in the unrotated sense; a rotated one is wider than the sides.
        assertTrue(box.width > 215f)
        val horizontalEdges = out.zipWithNext().count { (a, b) -> abs(b.y - a.y) < 0.01f && abs(b.x - a.x) > 1f }
        assertTrue("rotated rectangle has no axis-aligned runs", horizontalEdges < out.size / 10)
    }

    @Test
    fun slightlyTiltedRectangleIsStraightened() {
        val base = listOf(100f to 100f, 300f to 100f, 300f to 200f, 100f to 200f)
        val rot = Traces.rotated(base, 5f, 200f, 150f)
        val out = snapped(Traces.polygon(rot))!!
        val box = Extent.of(out)
        val onEdge = out.count {
            abs(it.x - box.left) < 0.5f || abs(it.x - box.right) < 0.5f ||
                abs(it.y - box.top) < 0.5f || abs(it.y - box.bottom) < 0.5f
        }
        assertEquals(out.size, onEdge)
    }

    @Test
    fun triangleBecomesThreeCornerPolygon() {
        val tri = listOf(200f to 80f, 330f to 300f, 70f to 300f)
        val out = snapped(Traces.polygon(tri))!!
        val box = Extent.of(out)
        assertEquals(260f, box.width, 14f)
        assertEquals(220f, box.height, 14f)
        // Three straight runs: direction changes only near three places.
        var turns = 0
        for (i in 2 until out.size) {
            val a = out[i - 1]
            val b = out[i]
            val c = out[i - 2]
            val cross = (a.x - c.x) * (b.y - a.y) - (a.y - c.y) * (b.x - a.x)
            if (abs(cross) > 3f) turns++
        }
        assertTrue("only the corners turn", turns <= 6)
    }

    @Test
    fun scribbleDoesNotSnap() {
        for (seed in 1..8) assertNull("seed $seed", snapped(Traces.scribble(seed)))
    }

    @Test
    fun zigzagDoesNotSnap() {
        assertNull(snapped(Traces.zigzag()))
    }

    @Test
    fun openCurveDoesNotSnap() {
        val arc = (0..40).map { 100f + it * 8f to 300f - 90f * kotlin.math.sin(it / 40f * 2.6f) }
        assertNull(snapped(Traces.walk(arc)))
    }

    @Test
    fun tinyHoldDotDoesNotSnap() {
        val dot = Traces.walk(listOf(100f to 100f, 106f to 102f))
        assertNull(snapped(dot))
    }

    @Test
    fun outputTimingIsMonotonicAndPressureFree() {
        val out = snapped(Traces.circle(300f, 300f, 80f))!!
        assertNotNull(out)
        out.zipWithNext().forEach { (a, b) -> assertTrue(b.t >= a.t) }
        assertTrue(out.last().t > out.first().t)
    }

    private fun distanceToLine(p: InkPt, a: InkPt, b: InkPt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return abs(dx * (a.y - p.y) - dy * (a.x - p.x)) / hypot(dx, dy)
    }

    private fun maxStep(p: List<InkPt>) = p.zipWithNext().maxOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) }

    @Test
    fun wideOvalAroundAWordSnaps() {
        // Circling a word draws a wide, wobbly oval whose tight ends look like corners.
        assertNotNull(ShapeSnap.snap(wideOval))
    }

    private val wideOval = listOf(InkPt(1900.0f,1278.2f,0L),InkPt(1903.5f,1278.2f,6L),InkPt(1907.0f,1278.1f,12L),InkPt(1910.5f,1278.0f,18L),InkPt(1914.1f,1277.9f,24L),InkPt(1917.1f,1277.7f,30L),InkPt(1920.0f,1277.4f,36L),InkPt(1923.0f,1277.2f,42L),InkPt(1926.0f,1277.0f,48L),InkPt(1929.0f,1276.7f,54L),InkPt(1932.1f,1277.6f,60L),InkPt(1935.2f,1278.5f,66L),InkPt(1938.2f,1279.3f,72L),InkPt(1941.3f,1280.2f,78L),InkPt(1944.7f,1280.5f,84L),InkPt(1948.0f,1280.9f,90L),InkPt(1951.4f,1281.2f,96L),InkPt(1954.8f,1281.5f,102L),InkPt(1958.1f,1281.9f,108L),InkPt(1961.4f,1282.4f,114L),InkPt(1964.8f,1282.8f,120L),InkPt(1968.1f,1283.2f,126L),InkPt(1971.1f,1284.5f,132L),InkPt(1974.1f,1285.9f,138L),InkPt(1977.2f,1287.3f,144L),InkPt(1980.7f,1287.7f,150L),InkPt(1984.2f,1288.0f,156L),InkPt(1987.8f,1288.3f,162L),InkPt(1991.3f,1288.7f,168L),InkPt(1994.3f,1289.3f,174L),InkPt(1997.3f,1290.0f,180L),InkPt(2000.2f,1290.7f,186L),InkPt(2003.2f,1291.3f,192L),InkPt(2006.2f,1292.1f,198L),InkPt(2009.1f,1292.8f,204L),InkPt(2012.0f,1293.5f,210L),InkPt(2015.0f,1294.3f,216L),InkPt(2015.3f,1297.3f,222L),InkPt(2015.7f,1300.3f,228L),InkPt(2019.1f,1301.4f,234L),InkPt(2022.4f,1302.5f,240L),InkPt(2025.8f,1303.6f,246L),InkPt(2029.5f,1308.3f,252L),InkPt(2032.6f,1308.9f,258L),InkPt(2035.7f,1309.5f,264L),InkPt(2038.7f,1310.1f,270L),InkPt(2041.8f,1310.7f,276L),InkPt(2044.9f,1311.3f,282L),InkPt(2047.6f,1316.2f,288L),InkPt(2044.3f,1318.9f,294L),InkPt(2041.1f,1321.6f,300L),InkPt(2044.2f,1322.4f,306L),InkPt(2047.3f,1323.3f,312L),InkPt(2050.5f,1324.1f,318L),InkPt(2053.6f,1325.0f,324L),InkPt(2056.8f,1325.8f,330L),InkPt(2057.0f,1330.9f,336L),InkPt(2054.2f,1333.3f,342L),InkPt(2051.4f,1335.8f,348L),InkPt(2048.6f,1340.6f,354L),InkPt(2045.3f,1342.0f,360L),InkPt(2041.9f,1343.3f,366L),InkPt(2038.6f,1344.7f,372L),InkPt(2035.3f,1346.7f,378L),InkPt(2032.0f,1348.8f,384L),InkPt(2033.2f,1354.2f,390L),InkPt(2030.0f,1354.9f,396L),InkPt(2026.7f,1355.7f,402L),InkPt(2023.5f,1356.4f,408L),InkPt(2020.2f,1357.1f,414L),InkPt(2017.1f,1359.2f,420L),InkPt(2014.0f,1361.3f,426L),InkPt(2011.2f,1362.5f,432L),InkPt(2008.4f,1363.8f,438L),InkPt(2005.6f,1365.0f,444L),InkPt(2002.6f,1365.6f,450L),InkPt(1999.6f,1366.2f,456L),InkPt(1996.7f,1366.8f,462L),InkPt(1993.7f,1367.4f,468L),InkPt(1990.3f,1369.8f,474L),InkPt(1987.0f,1372.1f,480L),InkPt(1984.0f,1372.8f,486L),InkPt(1981.0f,1373.4f,492L),InkPt(1978.1f,1374.0f,498L),InkPt(1975.1f,1374.7f,504L),InkPt(1971.8f,1376.0f,510L),InkPt(1968.6f,1377.4f,516L),InkPt(1965.3f,1378.8f,522L),InkPt(1962.3f,1378.8f,528L),InkPt(1959.3f,1378.9f,534L),InkPt(1956.3f,1379.0f,540L),InkPt(1953.3f,1379.0f,546L),InkPt(1950.3f,1379.1f,552L),InkPt(1947.0f,1379.6f,558L),InkPt(1943.8f,1380.1f,564L),InkPt(1940.5f,1380.6f,570L),InkPt(1937.3f,1381.1f,576L),InkPt(1933.7f,1381.2f,582L),InkPt(1930.2f,1381.3f,588L),InkPt(1926.6f,1381.3f,594L),InkPt(1923.1f,1381.4f,600L),InkPt(1919.6f,1381.7f,606L),InkPt(1916.2f,1382.1f,612L),InkPt(1912.7f,1382.4f,618L),InkPt(1909.3f,1382.7f,624L),InkPt(1905.8f,1382.5f,630L),InkPt(1902.2f,1382.2f,636L),InkPt(1898.7f,1382.0f,642L),InkPt(1895.2f,1381.8f,648L),InkPt(1891.8f,1381.4f,654L),InkPt(1888.4f,1381.1f,660L),InkPt(1885.0f,1380.8f,666L),InkPt(1881.6f,1380.4f,672L),InkPt(1878.5f,1381.0f,678L),InkPt(1875.3f,1381.6f,684L),InkPt(1872.1f,1382.1f,690L),InkPt(1869.0f,1382.7f,696L),InkPt(1865.8f,1383.3f,702L),InkPt(1862.2f,1382.9f,708L),InkPt(1858.7f,1382.6f,714L),InkPt(1855.2f,1382.3f,720L),InkPt(1851.6f,1381.9f,726L),InkPt(1848.4f,1381.3f,732L),InkPt(1845.2f,1380.7f,738L),InkPt(1842.0f,1380.0f,744L),InkPt(1838.8f,1379.4f,750L),InkPt(1835.8f,1378.7f,756L),InkPt(1832.8f,1378.0f,762L),InkPt(1829.7f,1377.3f,768L),InkPt(1826.7f,1376.6f,774L),InkPt(1823.7f,1375.2f,780L),InkPt(1820.8f,1373.8f,786L),InkPt(1817.8f,1372.3f,792L),InkPt(1814.4f,1371.3f,798L),InkPt(1811.0f,1370.2f,804L),InkPt(1807.6f,1369.2f,810L),InkPt(1804.0f,1368.2f,816L),InkPt(1800.4f,1367.2f,822L),InkPt(1796.8f,1366.2f,828L),InkPt(1793.4f,1364.1f,834L),InkPt(1790.1f,1362.0f,840L),InkPt(1786.7f,1361.7f,846L),InkPt(1783.4f,1361.3f,852L),InkPt(1780.0f,1361.0f,858L),InkPt(1776.7f,1360.7f,864L),InkPt(1773.3f,1360.4f,870L),InkPt(1770.5f,1355.2f,876L),InkPt(1767.4f,1354.4f,882L),InkPt(1764.3f,1353.5f,888L),InkPt(1761.3f,1352.7f,894L),InkPt(1758.2f,1351.9f,900L),InkPt(1759.5f,1346.4f,906L),InkPt(1756.4f,1345.4f,912L),InkPt(1753.4f,1344.4f,918L),InkPt(1750.4f,1343.5f,924L),InkPt(1747.3f,1342.5f,930L),InkPt(1746.3f,1337.4f,936L),InkPt(1749.1f,1336.1f,942L),InkPt(1752.0f,1334.8f,948L),InkPt(1754.8f,1333.5f,954L),InkPt(1757.6f,1332.2f,960L),InkPt(1754.5f,1327.6f,966L),InkPt(1751.5f,1325.8f,972L),InkPt(1748.5f,1324.1f,978L),InkPt(1745.4f,1322.3f,984L),InkPt(1748.5f,1320.9f,990L),InkPt(1751.5f,1319.4f,996L),InkPt(1754.6f,1317.9f,1002L),InkPt(1752.9f,1315.2f,1008L),InkPt(1751.2f,1312.4f,1014L),InkPt(1754.5f,1311.5f,1020L),InkPt(1757.9f,1310.7f,1026L),InkPt(1761.2f,1309.8f,1032L),InkPt(1764.6f,1308.9f,1038L),InkPt(1768.1f,1307.8f,1044L),InkPt(1771.5f,1306.6f,1050L),InkPt(1775.0f,1305.5f,1056L),InkPt(1775.3f,1299.9f,1062L),InkPt(1778.8f,1297.7f,1068L),InkPt(1782.3f,1295.5f,1074L),InkPt(1785.3f,1295.1f,1080L),InkPt(1788.3f,1294.8f,1086L),InkPt(1791.4f,1294.4f,1092L),InkPt(1794.4f,1294.1f,1098L),InkPt(1797.4f,1293.7f,1104L),InkPt(1800.4f,1293.1f,1110L),InkPt(1803.4f,1292.5f,1116L),InkPt(1806.4f,1291.9f,1122L),InkPt(1809.3f,1291.3f,1128L),InkPt(1812.3f,1290.0f,1134L),InkPt(1815.2f,1288.8f,1140L),InkPt(1818.1f,1287.5f,1146L),InkPt(1820.5f,1285.7f,1152L),InkPt(1822.9f,1283.9f,1158L),InkPt(1825.4f,1282.1f,1164L),InkPt(1829.0f,1281.8f,1170L),InkPt(1832.6f,1281.5f,1176L),InkPt(1836.2f,1281.2f,1182L),InkPt(1839.8f,1280.9f,1188L),InkPt(1843.1f,1281.2f,1194L),InkPt(1846.3f,1281.5f,1200L),InkPt(1849.6f,1281.8f,1206L),InkPt(1852.9f,1282.1f,1212L),InkPt(1856.2f,1282.4f,1218L),InkPt(1859.3f,1281.9f,1224L),InkPt(1862.5f,1281.4f,1230L),InkPt(1865.6f,1281.0f,1236L),InkPt(1868.7f,1280.5f,1242L),InkPt(1872.2f,1280.5f,1248L),InkPt(1875.6f,1280.5f,1254L),InkPt(1879.0f,1280.5f,1260L),InkPt(1882.5f,1280.4f,1266L),InkPt(1885.8f,1280.4f,1272L),InkPt(1889.2f,1280.4f,1278L),InkPt(1892.5f,1280.3f,1284L),InkPt(1895.9f,1280.3f,1290L),InkPt(1899.4f,1279.4f,1296L),InkPt(1902.9f,1278.4f,1302L),InkPt(1906.5f,1277.5f,1308L),InkPt(1910.0f,1276.6f,1314L),InkPt(1913.2f,1277.5f,1320L),InkPt(1916.4f,1278.4f,1326L),InkPt(1919.6f,1279.4f,1332L),InkPt(1922.8f,1280.3f,1338L),InkPt(1926.5f,1280.1f,1344L),InkPt(1930.2f,1279.8f,1350L),InkPt(1933.9f,1279.6f,1356L),InkPt(1937.5f,1279.3f,1362L),InkPt(1937.5f,1279.3f,1408L),InkPt(1937.5f,1279.3f,1448L),InkPt(1937.5f,1279.3f,1488L),InkPt(1937.5f,1279.3f,1528L),InkPt(1937.5f,1279.3f,1568L),InkPt(1937.5f,1279.3f,1608L),InkPt(1937.5f,1279.3f,1648L),InkPt(1937.5f,1279.3f,1688L),InkPt(1937.5f,1279.3f,1728L),InkPt(1937.5f,1279.3f,1768L),InkPt(1937.5f,1279.3f,1808L),InkPt(1937.5f,1279.3f,1848L),InkPt(1937.5f,1279.3f,1888L),InkPt(1937.5f,1279.3f,1928L),InkPt(1937.5f,1279.3f,1968L),InkPt(1937.5f,1279.3f,2008L),InkPt(1937.5f,1279.3f,2048L),InkPt(1937.5f,1279.3f,2088L))
}
