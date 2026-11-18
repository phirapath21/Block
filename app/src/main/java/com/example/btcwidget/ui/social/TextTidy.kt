package com.example.btcwidget.ui.social

/**
 * Blank space a note's text is left with once links shown as blocks (photos, videos, quoted
 * notes) are taken out of it: the line breaks around them stay behind. Pure, so it can be
 * tested on the JVM; [NoteBody] applies the ranges to its styled text.
 */
object TextTidy {
    private val BLANK_RUN = Regex("""\n[ \t ]*\n(?:[ \t ]*\n)+""")

    /**
     * The parts of [text] to keep, in order, and what goes between them: whitespace trimmed
     * from both ends, and any run of two or more blank lines cut to one (a paragraph break).
     * Joining the kept parts with "\n\n" gives the tidy text.
     */
    fun keep(text: String): List<IntRange> {
        var start = 0
        while (start < text.length && text[start].isWhitespace()) start++
        var end = text.length
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start >= end) return emptyList()
        val parts = mutableListOf<IntRange>()
        var from = start
        for (m in BLANK_RUN.findAll(text.substring(0, end), start)) {
            parts += from until m.range.first
            from = m.range.last + 1
        }
        parts += from until end
        return parts
    }

    /** [text] tidied: what [keep] keeps, paragraphs joined by one blank line. */
    fun tidy(text: String): String = keep(text).joinToString("\n\n") { text.substring(it) }
}
