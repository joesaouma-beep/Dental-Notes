package com.dentalstudio.notes.learning

/**
 * Parsing and re-assembly of clinical notes.
 *
 * Notes use a simple, stable wire format so that both the renderer and the
 * learning engine agree on where a section starts and ends:
 *
 *   ## PRESENTING COMPLAINT
 *   Body line.
 *   - bullet line
 *
 * Headings are matched leniently (markdown "##", bold "**HEADING**" or a bare
 * ALL-CAPS line) because language models drift between those forms.
 */
data class NoteSection(
    val heading: String,
    val body: String,
) {
    val words: Int get() = TextTokens.wordCount(body)
    fun render(): String = buildString {
        append("## ").append(heading).append('\n')
        append(body.trim())
    }
}

object NoteStructure {

    private val MD_HEADING = Regex("^#{1,4}\\s*(.+?)\\s*#*$")
    private val BOLD_HEADING = Regex("^\\*\\*(.+?)\\*\\*:?$")
    private val CAPS_HEADING = Regex("^([A-Z][A-Z /&'()-]{2,60}):?$")

    /** Returns the heading text if [line] looks like a section heading. */
    fun headingOf(line: String): String? {
        val t = line.trim()
        if (t.isEmpty()) return null
        MD_HEADING.matchEntire(t)?.let { m ->
            val inner = m.groupValues[1].trim().removeSurrounding("**").trim()
            if (inner.isNotEmpty() && TextTokens.wordCount(inner) <= 6) return normalizeHeading(inner)
        }
        BOLD_HEADING.matchEntire(t)?.let { m ->
            val inner = m.groupValues[1].trim()
            if (TextTokens.wordCount(inner) <= 6) return normalizeHeading(inner)
        }
        CAPS_HEADING.matchEntire(t)?.let { m ->
            val inner = m.groupValues[1].trim()
            if (TextTokens.wordCount(inner) <= 6) return normalizeHeading(inner)
        }
        return null
    }

    fun normalizeHeading(raw: String): String =
        raw.trim().trimEnd(':').replace(Regex("\\s+"), " ").uppercase()

    fun parse(note: String): List<NoteSection> {
        val out = mutableListOf<NoteSection>()
        var heading: String? = null
        val body = StringBuilder()

        fun flush() {
            val h = heading
            val text = body.toString().trim()
            if (h != null) {
                out += NoteSection(h, text)
            } else if (text.isNotEmpty()) {
                out += NoteSection("NOTE", text)
            }
            body.setLength(0)
        }

        note.lines().forEach { line ->
            val h = headingOf(line)
            if (h != null) {
                flush()
                heading = h
            } else {
                body.append(line).append('\n')
            }
        }
        flush()
        return out.filter { it.heading.isNotBlank() }
    }

    fun render(sections: List<NoteSection>): String =
        sections.filter { it.body.isNotBlank() }.joinToString("\n\n") { it.render() }.trim()

    /** Plain text with headings stripped — used for prose-level statistics. */
    fun bodyOnly(note: String): String =
        note.lines().filter { headingOf(it) == null }.joinToString("\n")
}
