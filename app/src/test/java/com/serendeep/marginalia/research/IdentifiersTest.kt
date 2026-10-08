package com.serendeep.marginalia.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentifiersTest {
    @Test
    fun doi_fromCitationLine() {
        assertEquals(
            "10.1145/3292500.3330701",
            findDoi("KDD '19, August 4-8, 2019, Anchorage, AK, USA. https://doi.org/10.1145/3292500.3330701"),
        )
    }

    @Test
    fun doi_trimsTrailingPunctuation() {
        assertEquals("10.1038/nature14539", findDoi("see doi:10.1038/nature14539)."))
        assertEquals("10.1038/nature14539", findDoi("(10.1038/nature14539),"))
    }

    @Test
    fun doi_keepsBalancedParentheses() {
        assertEquals("10.1002/(SICI)1097-4571(199806)49:8", findDoi("DOI 10.1002/(SICI)1097-4571(199806)49:8<693 text"))
    }

    @Test
    fun doi_absent() {
        assertNull(findDoi("Attention is all you need. Section 10.5 shows results."))
    }

    @Test
    fun arxiv_prefixedStampDropsVersion() {
        assertEquals("1909.13231", findArxivId("arXiv:1909.13231v3 [cs.LG] 4 Feb 2020", requirePrefix = true))
        assertEquals("2303.15361", findArxivId("ArXiv: 2303.15361v2 [cs.CV]"))
    }

    @Test
    fun arxiv_fromFileName() {
        assertEquals("1909.13231", findArxivId("1909.13231v3.pdf"))
        assertEquals("2303.15361", findArxivId("2303.15361v2.pdf"))
        assertEquals("1706.03762", findArxivId("1706.03762.pdf"))
    }

    @Test
    fun arxiv_bareNumbersInProseAreIgnored() {
        assertNull(findArxivId("accuracy rose from 1234.5678 to 0.99", requirePrefix = true))
        assertNull(findArxivId("table 3499.56789 values"))
        assertNull(findArxivId("lecture-notes.pdf"))
    }
}
