package com.serendeep.marginalia.update

import java.util.Locale

/** One release's notes in the three groups the app shows. */
data class VersionNotes(
    val version: String,
    val date: String?,
    val new: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = new.isEmpty() && fixed.isEmpty() && improved.isEmpty()
}

object ReleaseNotes {
    private val HEADING = Regex("""^##\s+(?:\[([^\]]+)]\([^)]*\)|(\S+))\s*(?:\((\d{4}-\d{2}-\d{2})\))?""")
    private val HASH_LINK = Regex("""\s*\(\[[^\]]+]\([^)]*\)\)""")
    private val SCOPE = Regex("""^\*\*[^*]+:\*\*\s*""")
    private val WORD = Regex("""[\p{L}\p{N}]+""")

    private enum class Group { NEW, FIXED, IMPROVED }

    /**
     * Reads a CHANGELOG.md, or a single release's section with no version heading, into versions in file order.
     * Features become New, bug fixes Fixed and every other heading Improved.
     */
    fun parse(markdown: String): List<VersionNotes> {
        val versions = mutableListOf<Raw>()
        var current: Raw? = null
        var group = Group.IMPROVED
        for (line in markdown.lines().map { it.trim() }) {
            val heading = HEADING.find(line)
            when {
                heading != null -> {
                    val g = heading.groupValues
                    current = Raw(g[1].ifEmpty { g[2] }, g[3].ifEmpty { null }).also(versions::add)
                    group = Group.IMPROVED
                }
                line.startsWith("###") -> group = groupOf(line.trimStart('#').trim())
                line.startsWith("* ") || line.startsWith("- ") -> {
                    val text = clean(line.drop(2))
                    if (text.isNotEmpty()) (current ?: Raw("", null).also { current = it; versions.add(it) }).entries.add(group to text)
                }
            }
        }
        return versions.map(::finish).filter { !it.isEmpty }
    }

    private class Raw(val version: String, val date: String?) {
        val entries = mutableListOf<Pair<Group, String>>()
    }

    private fun groupOf(heading: String): Group {
        val h = heading.lowercase(Locale.ROOT)
        return when {
            "feature" in h || h == "new" -> Group.NEW
            "fix" in h -> Group.FIXED
            else -> Group.IMPROVED
        }
    }

    private fun clean(raw: String): String =
        raw.replace(HASH_LINK, "").replace(SCOPE, "").trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }

    private fun words(text: String): Set<String> = WORD.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toSet()

    /** Drops repeats, and any entry whose words all appear in another one, which is how a merge title looks next to its commit. */
    private fun finish(raw: Raw): VersionNotes {
        val unique = raw.entries.distinctBy { it.second.lowercase(Locale.ROOT) }
        val sets = unique.map { words(it.second) }
        val kept = unique.filterIndexed { i, _ ->
            sets.indices.none { j ->
                j != i && sets[i].isNotEmpty() && sets[j].containsAll(sets[i]) && (sets[j].size > sets[i].size || j < i)
            }
        }
        fun of(g: Group) = kept.filter { it.first == g }.map { it.second }
        return VersionNotes(raw.version, raw.date, of(Group.NEW), of(Group.FIXED), of(Group.IMPROVED))
    }

    /** What to show after an update: every changelog version since [lastSeen], newest first; [cached] covers builds the changelog does not list. */
    fun sinceLastSeen(changelog: List<VersionNotes>, lastSeen: String?, installed: String, cached: VersionNotes?): List<VersionNotes> {
        val installedIndex = changelog.indexOfFirst { it.version == installed }
        if (installedIndex < 0) return listOfNotNull(cached?.takeIf { !it.isEmpty }?.copy(version = installed))
        val seenIndex = changelog.indexOfFirst { it.version == lastSeen }
        val end = if (seenIndex > installedIndex) seenIndex else installedIndex + 1
        return changelog.subList(installedIndex, end)
    }
}
