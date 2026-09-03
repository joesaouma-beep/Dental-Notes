package com.dentalstudio.notes.learning

/** Tokenising and normalisation helpers shared by the diff and the rule miner. */
object TextTokens {

    private val WORD = Regex("[A-Za-z][A-Za-z'\\-]*|\\d+[A-Za-z]*|[^\\sA-Za-z\\d]")
    private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+")

    /**
     * Quantities spelled out. A substitution between two of these is a change
     * of clinical fact — a probing depth, a dose, a number of canals — never a
     * change of style, so the miner must never generalise it to the next patient.
     */
    val NUMBER_WORDS = setOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen", "twenty", "thirty", "forty", "fifty",
        "sixty", "seventy", "eighty", "ninety", "hundred", "thousand",
        "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth",
        "ninth", "tenth", "half", "quarter", "once", "twice", "single", "double", "triple",
        "millimetre", "millimetres", "millimeter", "millimeters", "mm", "ml", "mg", "cc",
        "percent", "minute", "minutes", "hour", "hours", "day", "days", "week", "weeks",
        "month", "months", "year", "years",
    )

    val STOPWORDS = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "at", "for", "with",
        "was", "is", "were", "are", "be", "been", "has", "had", "have", "it", "its",
        "this", "that", "as", "by", "from", "no", "not",
    )

    fun words(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

    fun wordCount(text: String): Int = words(text).count { it.first().isLetterOrDigit() }

    private const val EDGE_PUNCTUATION = ".,;:-\u2013\u2022*"

    /**
     * The canonical form a phrase is stored and matched by.
     *
     * Bullet markers and punctuation are stripped from both ends together with
     * whitespace. A phrase learned from a bullet line ("- Patient tolerated the
     * procedure well.") has to match the same phrase written as prose, or the
     * preference would be recorded once and then never fire again.
     */
    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim { it.isWhitespace() || it in EDGE_PUNCTUATION }

    fun sentences(text: String): List<String> =
        text.split('\n')
            .flatMap { line -> line.split(SENTENCE_SPLIT) }
            .map { it.trim().removePrefix("- ").removePrefix("• ").trim() }
            .filter { it.isNotEmpty() }

    /** True when the fragment is patient-specific rather than a stylistic habit. */
    fun isPatientSpecific(fragment: String): Boolean {
        if (fragment.any { it.isDigit() }) return true
        val w = words(fragment).filter { it.first().isLetter() }
        if (w.isEmpty()) return true
        return false
    }

    fun containsNumberWord(fragment: String): Boolean =
        words(fragment).any { it.lowercase() in NUMBER_WORDS }

    fun isAllStopwords(fragment: String): Boolean =
        words(fragment).filter { it.first().isLetter() }.all { it.lowercase() in STOPWORDS }

    fun bulletLineRatio(text: String): Double {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && NoteStructure.headingOf(it) == null }
        if (lines.isEmpty()) return 0.0
        val bullets = lines.count { it.startsWith("- ") || it.startsWith("• ") || it.startsWith("* ") }
        return bullets.toDouble() / lines.size
    }

    /** Copies the capitalisation shape of [source] onto [target]. */
    fun matchCase(source: String, target: String): String {
        if (source.isEmpty() || target.isEmpty()) return target
        val letters = source.filter { it.isLetter() }
        if (letters.isNotEmpty() && letters.all { it.isUpperCase() } && letters.length > 1) return target.uppercase()
        if (source.first().isUpperCase()) return target.replaceFirstChar { it.uppercaseChar() }
        return target
    }
}
