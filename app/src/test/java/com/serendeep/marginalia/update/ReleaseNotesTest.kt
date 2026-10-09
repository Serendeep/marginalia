package com.serendeep.marginalia.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    private val changelog = """
# Changelog

## [1.2.0](https://github.com/x/y/compare/v1.1.0...v1.2.0) (2026-10-09)


### Features

* add an Updates section to settings ([d5270a1](https://github.com/x/y/commit/d5270a1449b0))
* add remote config ([8721a44](https://github.com/x/y/commit/8721a443262f))


### Bug Fixes

* open settings from a sidebar gear ([03d58f4](https://github.com/x/y/commit/03d58f441d85))

## [1.1.0](https://github.com/x/y/compare/v1.0.0...v1.1.0) (2026-10-08)


### Features

* **ai:** add a model picker ([a18e3c6](https://github.com/x/y/commit/a18e3c6ca9))


### Performance Improvements

* cache page renders ([1111111](https://github.com/x/y/commit/1111111111))

## [1.0.0](https://github.com/x/y/commits/v1.0.0) (2026-10-01)

### Features

* first release
"""

    @Test
    fun readsVersionsDatesAndGroups() {
        val v = ReleaseNotes.parse(changelog)
        assertEquals(listOf("1.2.0", "1.1.0", "1.0.0"), v.map { it.version })
        assertEquals("2026-10-09", v[0].date)
        assertEquals(listOf("Add an Updates section to settings", "Add remote config"), v[0].new)
        assertEquals(listOf("Open settings from a sidebar gear"), v[0].fixed)
        assertEquals(listOf("Cache page renders"), v[1].improved)
        assertEquals(listOf("Add a model picker"), v[1].new)
    }

    @Test
    fun dropsDuplicatesAndMergeTitlesThatRepeatACommit() {
        val v = ReleaseNotes.parse(
            """
## 1.3.0 (2026-10-10)

### Features

* add a model picker to the ask surfaces
* Add a model picker to the ask surfaces
* model picker
* add shared panels

### Bug Fixes

* fix flicker
""",
        ).single()
        assertEquals(listOf("Add a model picker to the ask surfaces", "Add shared panels"), v.new)
        assertEquals(listOf("Fix flicker"), v.fixed)
    }

    @Test
    fun readsASectionWithoutAVersionHeading() {
        val v = ReleaseNotes.parse("### New\n\n- one thing\n\n### Fixed\n\n- another\n\n### Improved\n\n- a third\n").single()
        assertEquals("", v.version)
        assertEquals(listOf("One thing"), v.new)
        assertEquals(listOf("Another"), v.fixed)
        assertEquals(listOf("A third"), v.improved)
    }

    @Test
    fun emptyInputHasNoVersions() {
        assertTrue(ReleaseNotes.parse("").isEmpty())
        assertTrue(ReleaseNotes.parse("# Changelog\n").isEmpty())
    }

    @Test
    fun listsEveryVersionSinceTheLastOneSeen() {
        val v = ReleaseNotes.parse(changelog)
        assertEquals(listOf("1.2.0", "1.1.0"), ReleaseNotes.sinceLastSeen(v, "1.0.0", "1.2.0", null).map { it.version })
        assertEquals(listOf("1.2.0"), ReleaseNotes.sinceLastSeen(v, "1.1.0", "1.2.0", null).map { it.version })
    }

    @Test
    fun showsOnlyTheInstalledVersionWhenTheLastSeenOneIsUnknown() {
        val v = ReleaseNotes.parse(changelog)
        assertEquals(listOf("1.2.0"), ReleaseNotes.sinceLastSeen(v, null, "1.2.0", null).map { it.version })
        assertEquals(listOf("1.2.0"), ReleaseNotes.sinceLastSeen(v, "1.1.0-nightly.5+abc", "1.2.0", null).map { it.version })
    }

    @Test
    fun usesTheCachedNotesForABuildTheChangelogLacks() {
        val v = ReleaseNotes.parse(changelog)
        val cached = VersionNotes("", null, new = listOf("Thing"))
        val shown = ReleaseNotes.sinceLastSeen(v, "1.2.0", "1.3.0-nightly.9+abc", cached)
        assertEquals(listOf("1.3.0-nightly.9+abc"), shown.map { it.version })
        assertTrue(ReleaseNotes.sinceLastSeen(v, "1.2.0", "1.3.0-nightly.9+abc", null).isEmpty())
    }
}
