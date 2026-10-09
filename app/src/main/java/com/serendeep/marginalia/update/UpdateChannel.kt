package com.serendeep.marginalia.update

enum class UpdateChannel(val id: String, val label: String) {
    STABLE("stable", "Stable"),
    NIGHTLY("nightly", "Nightly");

    companion object {
        /** The saved choice if it names a channel, else [default]. */
        fun parse(saved: String?, default: String): UpdateChannel =
            entries.firstOrNull { it.id == saved } ?: entries.firstOrNull { it.id == default } ?: STABLE
    }
}

internal fun feedUrl(channel: UpdateChannel, stable: String, nightly: String): String =
    if (channel == UpdateChannel.NIGHTLY) nightly else stable

/** Shown when a Nightly build is set to follow Stable: nothing is downgraded, the next stable release replaces it. */
internal fun switchBackNote(installed: UpdateChannel, selected: UpdateChannel): String? =
    if (installed == UpdateChannel.NIGHTLY && selected == UpdateChannel.STABLE) {
        "You'll move to Stable with the next stable release."
    } else {
        null
    }

internal fun installedLine(versionName: String, installed: UpdateChannel) = "Installed: $versionName · ${installed.id}"
