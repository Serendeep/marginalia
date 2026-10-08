package com.serendeep.marginalia.research

import android.util.Xml
import com.serendeep.marginalia.data.MarginaliaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** [fetched] is false when [text] is only the title fallback. */
data class Citation(val text: String, val fetched: Boolean, val hasIdentifier: Boolean)

@Singleton
class CitationService @Inject constructor(private val repository: MarginaliaRepository) {

    /** The cached BibTeX, else a fetched (then cached) one, else "Title (file)". Network work runs on IO. */
    suspend fun citationFor(lectureId: String): Citation = withContext(Dispatchers.IO) {
        val lecture = repository.getLecture(lectureId)
            ?: return@withContext Citation("", fetched = false, hasIdentifier = false)
        lecture.bibtex?.let { return@withContext Citation(it, fetched = true, hasIdentifier = true) }
        val bibtex = runCatching {
            lecture.doi?.let { fetchDoi(it) } ?: lecture.arxivId?.let { fetchArxiv(it) }
        }.getOrNull()
        if (bibtex != null) {
            repository.saveBibtex(lectureId, bibtex)
            return@withContext Citation(bibtex, fetched = true, hasIdentifier = true)
        }
        val file = repository.latestDocument(lectureId)?.fileName
        Citation(
            fallbackCitation(lecture.title, file),
            fetched = false,
            hasIdentifier = lecture.doi != null || lecture.arxivId != null,
        )
    }

    private fun fetchDoi(doi: String): String? =
        get("https://doi.org/$doi", "application/x-bibtex")?.trim()?.takeIf { it.startsWith("@") }

    private fun fetchArxiv(id: String): String? =
        get("https://export.arxiv.org/api/query?id_list=$id", "application/atom+xml")
            ?.let { parseArxivAtom(it, id) }?.let(::arxivBibtex)

    private fun get(url: String, accept: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "Marginalia/1.0")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/** Reads the first `<entry>` of an arXiv Atom feed; null when the feed has none. */
fun parseArxivAtom(xml: String, id: String): ArxivEntry? {
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(xml))
    var title: String? = null
    var published: String? = null
    val authors = ArrayList<String>()
    var inEntry = false
    var inAuthor = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when {
                parser.name == "entry" -> inEntry = true
                !inEntry -> Unit
                parser.name == "author" -> inAuthor = true
                parser.name == "title" && title == null -> title = parser.nextText().squash()
                parser.name == "published" && published == null -> published = parser.nextText().trim()
                parser.name == "name" && inAuthor -> authors += parser.nextText().squash()
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "author" -> inAuthor = false
                "entry" -> break
            }
        }
        event = parser.next()
    }
    val year = published?.take(4)?.toIntOrNull() ?: return null
    return title?.takeIf { it.isNotEmpty() && authors.isNotEmpty() }?.let { ArxivEntry(id, it, authors, year) }
}

private fun String.squash() = trim().replace(Regex("\\s+"), " ")
