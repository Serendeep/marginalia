package com.serendeep.marginalia.research

import org.junit.Assert.assertEquals
import org.junit.Test

class CitationTest {
    private val entry = ArxivEntry(
        id = "1909.13231",
        title = "Test-Time Training with Self-Supervision for Generalization under Distribution Shifts",
        authors = listOf("Yu Sun", "Xiaolong Wang", "Zhuang Liu", "John Miller", "Alexei A. Efros", "Moritz Hardt"),
        year = 2019,
    )

    @Test
    fun arxivBibtex_isMiscWithEprint() {
        assertEquals(
            """
            @misc{sun2019test,
              title = {Test-Time Training with Self-Supervision for Generalization under Distribution Shifts},
              author = {Yu Sun and Xiaolong Wang and Zhuang Liu and John Miller and Alexei A. Efros and Moritz Hardt},
              year = {2019},
              eprint = {1909.13231},
              archivePrefix = {arXiv},
              url = {https://arxiv.org/abs/1909.13231}
            }
            """.trimIndent(),
            arxivBibtex(entry),
        )
    }

    @Test
    fun arxivBibtex_keyFallsBackWhenNothingUsable() {
        val key = arxivBibtex(ArxivEntry("2303.15361", "—", emptyList(), 2023)).lineSequence().first()
        assertEquals("@misc{2023,", key)
    }

    @Test
    fun fallback_titleWithFile() {
        assertEquals("Notes (a.pdf)", fallbackCitation("Notes", "a.pdf"))
        assertEquals("Notes", fallbackCitation("Notes", null))
    }
}
