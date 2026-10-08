package com.serendeep.marginalia.research

private val DOI = Regex("""10\.\d{4,9}/[-._;()/:A-Za-z0-9]+""")
private val ARXIV_PREFIXED = Regex("""arxiv\s*:\s*(\d{4}\.\d{4,5})(?:v\d+)?""", RegexOption.IGNORE_CASE)
private val ARXIV_BARE = Regex("""(?<![\d.])(\d{2})(\d{2})\.(\d{4,5})(?:v\d+)?(?![\d])""")

/** First DOI in [text], without trailing sentence punctuation or an unmatched closing bracket. */
fun findDoi(text: String): String? {
    var doi = DOI.find(text)?.value ?: return null
    while (doi.isNotEmpty()) {
        val last = doi.last()
        val unbalanced = last == ')' && doi.count { it == ')' } > doi.count { it == '(' }
        if (last in ".,;:" || unbalanced) doi = doi.dropLast(1) else break
    }
    return doi.takeIf { it.contains('/') && it.substringAfter('/').isNotEmpty() }
}

/**
 * New-style arXiv id (`1909.13231`, version suffix dropped). An `arXiv:` prefix is
 * always accepted; a bare id (as in file names) is accepted unless [requirePrefix],
 * and then must have a valid month so plain decimals in prose do not match.
 */
fun findArxivId(text: String, requirePrefix: Boolean = false): String? {
    ARXIV_PREFIXED.find(text)?.let { return it.groupValues[1] }
    if (requirePrefix) return null
    return ARXIV_BARE.findAll(text)
        .firstOrNull { (it.groupValues[2].toIntOrNull() ?: 0) in 1..12 }
        ?.let { "${it.groupValues[1]}${it.groupValues[2]}.${it.groupValues[3]}" }
}
