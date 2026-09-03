package com.dentalstudio.notes.learning

/** The result of rewriting generated text with high-confidence preferences. */
data class ApplyResult(
    val text: String,
    val applied: List<LearnedRule>,
)

/**
 * Applies the preferences the app is most sure about directly to the generated
 * note. Prompt conditioning alone is probabilistic; this pass makes the strong
 * rules deterministic, so a term the clinician has corrected three times never
 * comes back a fourth.
 */
object RuleApplier {

    fun apply(note: String, rules: List<LearnedRule>, templateId: String): ApplyResult {
        val relevant = rules.filter {
            it.isAutoApplied && (it.scope == MinedRule.SCOPE_ALL || it.scope == templateId)
        }
        if (relevant.isEmpty()) return ApplyResult(note, emptyList())

        var text = note
        val applied = mutableListOf<LearnedRule>()

        relevant.filter { it.type == RuleType.TERM }.forEach { rule ->
            val next = substitute(text, rule.pattern, rule.replacement)
            if (next != text) { text = next; applied += rule }
        }

        relevant.filter { it.type == RuleType.REMOVE }.forEach { rule ->
            val next = removePhrase(text, rule.pattern)
            if (next != text) { text = next; applied += rule }
        }

        relevant.filter { it.type == RuleType.SECTION_DROP }.forEach { rule ->
            val sections = NoteStructure.parse(text)
            if (sections.any { it.heading == rule.pattern } && sections.size > 1) {
                text = NoteStructure.render(sections.filterNot { it.heading == rule.pattern })
                applied += rule
            }
        }

        return ApplyResult(tidy(text), applied)
    }

    /** Word-boundary replacement that keeps the capitalisation of the original. */
    fun substitute(text: String, pattern: String, replacement: String): String {
        if (pattern.isBlank() || replacement.isBlank()) return text
        val regex = Regex("(?<![\\w-])" + Regex.escape(pattern) + "(?![\\w-])", RegexOption.IGNORE_CASE)
        return regex.replace(text) { m -> TextTokens.matchCase(m.value, replacement) }
    }

    /**
     * Removes a learned phrase. When the phrase is the whole sentence the
     * sentence goes with it, otherwise only the phrase is cut and the spacing
     * around it is repaired.
     */
    fun removePhrase(text: String, pattern: String): String {
        if (pattern.isBlank()) return text
        val kept = mutableListOf<String>()
        text.lines().forEach { line ->
            val stripped = removeFromLine(line, pattern)
            // A line emptied by the removal is dropped outright. Leaving it behind
            // would open a gap in the middle of a list; a line that was already
            // blank is a deliberate separator and stays.
            if (stripped.isBlank() && line.isNotBlank()) return@forEach
            kept += stripped
        }
        return kept.joinToString("\n")
    }

    private fun removeFromLine(line: String, pattern: String): String {
        val bare = line.trim().removePrefix("- ").removePrefix("• ").removePrefix("* ").trim()
        if (TextTokens.normalize(bare) == pattern) return ""

        val sentences = TextTokens.sentences(bare)
        if (sentences.size > 1) {
            val kept = sentences.filter { TextTokens.normalize(it) != pattern }
            if (kept.size != sentences.size) {
                val prefix = line.takeWhile { it.isWhitespace() } +
                    (line.trim().takeWhile { it == '-' || it == '•' || it == '*' || it == ' ' })
                return if (kept.isEmpty()) "" else prefix + kept.joinToString(" ")
            }
        }

        val regex = Regex("(?<![\\w-])" + Regex.escape(pattern) + "[.,;]?\\s*", RegexOption.IGNORE_CASE)
        val stripped = regex.replace(line, "")
        return if (stripped.trim().isEmpty()) "" else stripped.trimEnd()
    }

    /** Collapses the blank lines and dangling bullets left behind by removals. */
    fun tidy(text: String): String = text
        .lines()
        .filterNot { it.trim() == "-" || it.trim() == "•" || it.trim() == "*" }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .replace(Regex("[ \t]+\n"), "\n")
        .trim()
}
