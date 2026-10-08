package com.serendeep.marginalia.research

/** The fields of an arXiv API entry that a citation needs. */
data class ArxivEntry(val id: String, val title: String, val authors: List<String>, val year: Int)

/** A `@misc` BibTeX entry for a preprint with its arXiv identifiers. */
fun arxivBibtex(entry: ArxivEntry): String {
    val surname = entry.authors.firstOrNull()?.trim()?.substringAfterLast(' ').orEmpty()
    val firstWord = entry.title.split(Regex("[^A-Za-z0-9]+")).firstOrNull { it.isNotEmpty() }.orEmpty()
    val key = (surname + entry.year + firstWord).lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        .ifEmpty { "arxiv" + entry.id.replace(".", "") }
    return buildString {
        append("@misc{").append(key).append(",\n")
        append("  title = {").append(entry.title).append("},\n")
        append("  author = {").append(entry.authors.joinToString(" and ")).append("},\n")
        append("  year = {").append(entry.year).append("},\n")
        append("  eprint = {").append(entry.id).append("},\n")
        append("  archivePrefix = {arXiv},\n")
        append("  url = {https://arxiv.org/abs/").append(entry.id).append("}\n")
        append("}")
    }
}

/** "Title (file.pdf)": what gets copied when no BibTeX could be fetched. */
fun fallbackCitation(title: String, fileName: String?): String =
    if (fileName.isNullOrBlank()) title else "$title ($fileName)"
