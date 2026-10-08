package com.serendeep.marginalia.ai.ui

import com.serendeep.marginalia.data.PageHit
import java.util.Locale

private val stopWords = setOf(
    "the", "and", "for", "are", "was", "were", "what", "which", "who", "whom", "whose", "when", "where", "why", "how",
    "does", "did", "can", "could", "would", "should", "this", "that", "these", "those", "with", "from", "about", "into",
    "your", "you", "have", "has", "had", "not", "but", "any", "all", "explain", "tell", "give", "show", "say", "says",
    "there", "their", "them", "than", "then", "also", "its", "his", "her", "our", "mine", "document", "page", "pages",
)

/** Search terms for a free-form question: longest informative words first. */
fun askTerms(question: String, max: Int = 5): List<String> {
    val words = question.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.distinct()
    val informative = words.filter { it !in stopWords }.ifEmpty { words }
    return informative.sortedByDescending { it.length }.take(max)
}

/** Pages matched by the most terms first; ties keep title and page order. */
fun rankPages(perTerm: List<List<PageHit>>, limit: Int): List<PageHit> {
    val score = HashMap<Pair<String, Int>, Int>()
    val first = HashMap<Pair<String, Int>, PageHit>()
    perTerm.forEach { hits ->
        hits.forEach {
            val key = it.lectureId to it.page
            score[key] = (score[key] ?: 0) + 1
            first.putIfAbsent(key, it)
        }
    }
    return first.values
        .sortedWith(
            compareByDescending<PageHit> { score.getValue(it.lectureId to it.page) }
                .thenBy { it.title }
                .thenBy { it.page },
        )
        .take(limit)
}
