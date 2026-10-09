package com.serendeep.marginalia.ui.theme

const val THEME_MODE_KEY = "theme_mode"

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    fun isDark(system: Boolean) = when (this) {
        SYSTEM -> system
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
