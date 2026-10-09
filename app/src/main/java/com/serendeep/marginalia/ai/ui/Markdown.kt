package com.serendeep.marginalia.ai.ui

data class MdSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Bullet(val marker: String, val spans: List<MdSpan>) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val bulletLine = Regex("""^\s*([-*•]|\d{1,2}[.)])\s+(.*)$""")
private val headingLine = Regex("""^\s{0,3}#{1,6}\s+(.*)$""")

/** Paragraphs, bullets, fenced code, **bold** and `code`; an unclosed ** bolds to the end of its block. */
fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = ArrayList<MdBlock>()
    val para = ArrayList<String>()
    var code: StringBuilder? = null

    fun flushPara() {
        if (para.isNotEmpty()) blocks += MdBlock.Paragraph(parseInline(para.joinToString(" ")))
        para.clear()
    }

    for (line in src.lines()) {
        val fence = line.trimStart().startsWith("```")
        val open = code
        if (open != null) {
            if (fence) {
                blocks += MdBlock.Code(open.toString().trimEnd('\n'))
                code = null
            } else {
                open.append(line).append('\n')
            }
            continue
        }
        if (fence) {
            flushPara()
            code = StringBuilder()
            continue
        }
        if (line.isBlank()) {
            flushPara()
            continue
        }
        val heading = headingLine.matchEntire(line)
        if (heading != null) {
            flushPara()
            blocks += MdBlock.Paragraph(parseInline(heading.groupValues[1]).map { it.copy(bold = true) })
            continue
        }
        val bullet = bulletLine.matchEntire(line)
        if (bullet != null) {
            flushPara()
            val marker = bullet.groupValues[1].let { if (it[0].isDigit()) it else "•" }
            blocks += MdBlock.Bullet(marker, parseInline(bullet.groupValues[2]))
            continue
        }
        para += line.trim()
    }
    code?.let { blocks += MdBlock.Code(it.toString().trimEnd('\n')) }
    flushPara()
    return blocks
}

fun parseInline(s: String): List<MdSpan> {
    val out = ArrayList<MdSpan>()
    val sb = StringBuilder()
    var bold = false
    var i = 0
    fun flush() {
        if (sb.isNotEmpty()) out += MdSpan(sb.toString(), bold = bold)
        sb.setLength(0)
    }
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                flush()
                bold = !bold
                i += 2
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end < 0) {
                    sb.append('`')
                    i++
                } else {
                    flush()
                    out += MdSpan(s.substring(i + 1, end), bold = bold, code = true)
                    i = end + 1
                }
            }
            else -> {
                sb.append(s[i])
                i++
            }
        }
    }
    flush()
    return out
}
