package com.serendeep.marginalia.ink

/** Pen stroke width, in canvas pixels. */
enum class PenWidth(val px: Float) {
    FINE(3f),
    MEDIUM(Pens.DEFAULT_SIZE_PX),
    BOLD(10f),
}

/** What the Pencil double-tap does. */
enum class PencilAction(val label: String) {
    TOGGLE_ERASER("Toggle eraser"),
    LAST_TOOL("Last tool"),
    HIGHLIGHTER("Highlighter"),
    UNDO("Undo"),
}

/** The tool a double-tap should switch to; null when the action is not a tool switch. */
fun doubleTapTool(action: PencilAction, current: InkTool, previous: InkTool): InkTool? = when (action) {
    PencilAction.TOGGLE_ERASER -> if (current == InkTool.PEN) InkTool.ERASER else InkTool.PEN
    PencilAction.LAST_TOOL -> previous
    PencilAction.HIGHLIGHTER -> if (current == InkTool.HIGHLIGHTER) InkTool.PEN else InkTool.HIGHLIGHTER
    PencilAction.UNDO -> null
}

/**
 * The eight colours a pen swatch can take. The first three follow the theme palette so
 * they stay legible in light and dark; the rest are fixed.
 */
object PenColors {
    const val SWATCHES = 3
    const val PALETTE_CHOICES = 3

    private val fixed = intArrayOf(
        0xFFE5484D.toInt(),
        0xFF3DBE74.toInt(),
        0xFF111111.toInt(),
        0xFFF2F2F5.toInt(),
        0xFFF5C842.toInt(),
    )

    val choiceCount = PALETTE_CHOICES + fixed.size

    /** ARGB for [choice]; [themed] holds the graphite, indigo and rust of the current theme. */
    fun resolve(choice: Int, themed: IntArray): Int {
        val c = choice.coerceIn(0, choiceCount - 1)
        return if (c < PALETTE_CHOICES) themed[c] else fixed[c - PALETTE_CHOICES]
    }

    fun choiceFrom(raw: Int?, slot: Int): Int =
        raw?.takeIf { it in 0 until choiceCount } ?: slot.coerceIn(0, SWATCHES - 1)

    fun widthFrom(name: String?): PenWidth =
        name?.let { n -> PenWidth.entries.firstOrNull { it.name == n } } ?: PenWidth.MEDIUM

    fun actionFrom(name: String?): PencilAction =
        name?.let { n -> PencilAction.entries.firstOrNull { it.name == n } } ?: PencilAction.TOGGLE_ERASER

    const val WIDTH_KEY = "pen_width"
    const val ACTION_KEY = "pencil_double_tap"
    fun swatchKey(slot: Int) = "pen_swatch_$slot"
}
