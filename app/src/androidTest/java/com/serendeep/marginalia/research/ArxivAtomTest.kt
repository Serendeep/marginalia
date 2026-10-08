package com.serendeep.marginalia.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArxivAtomTest {
    private val feed = """<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title type="html">ArXiv Query: search_query=&amp;id_list=1909.13231</title>
          <entry>
            <id>http://arxiv.org/abs/1909.13231v3</id>
            <published>2019-09-29T17:59:19Z</published>
            <title>Test-Time Training with Self-Supervision for
          Generalization under Distribution Shifts</title>
            <author><name>Yu Sun</name></author>
            <author><name>Moritz Hardt</name></author>
          </entry>
        </feed>"""

    @Test
    fun parsesFirstEntryNotFeedTitle() {
        val e = parseArxivAtom(feed, "1909.13231")!!
        assertEquals("Test-Time Training with Self-Supervision for Generalization under Distribution Shifts", e.title)
        assertEquals(listOf("Yu Sun", "Moritz Hardt"), e.authors)
        assertEquals(2019, e.year)
    }

    @Test
    fun emptyFeedIsNull() {
        assertNull(parseArxivAtom("""<feed xmlns="http://www.w3.org/2005/Atom"><title>q</title></feed>""", "1"))
    }
}
