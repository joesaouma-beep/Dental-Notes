package com.dentalstudio.notes.learning

/**
 * Word level diff used to work out what the clinician actually changed.
 *
 * A change block is a maximal run of tokens that were removed and/or inserted
 * between two aligned anchors. Blocks are the raw material the rule miner turns
 * into learned preferences.
 */
data class ChangeBlock(
    val removed: List<String>,
    val inserted: List<String>,
    val contextBefore: List<String>,
    val contextAfter: List<String>,
) {
    val removedText: String get() = join(removed)
    val insertedText: String get() = join(inserted)
    val isSubstitution: Boolean get() = removed.isNotEmpty() && inserted.isNotEmpty()
    val isDeletion: Boolean get() = removed.isNotEmpty() && inserted.isEmpty()
    val isInsertion: Boolean get() = removed.isEmpty() && inserted.isNotEmpty()

    companion object {
        /** Re-joins tokens, keeping punctuation tight against the preceding word. */
        fun join(tokens: List<String>): String {
            val sb = StringBuilder()
            tokens.forEach { t ->
                val punctuation = t.length == 1 && !t.first().isLetterOrDigit()
                if (sb.isNotEmpty() && !punctuation) sb.append(' ')
                sb.append(t)
            }
            return sb.toString().trim()
        }
    }
}

object TextDiff {

    /** Above this many tokens per side the diff is skipped to keep memory bounded. */
    const val MAX_TOKENS = 1500

    fun blocks(before: String, after: String, contextSize: Int = 3): List<ChangeBlock> {
        val a = TextTokens.words(before)
        val b = TextTokens.words(after)
        if (a.size > MAX_TOKENS || b.size > MAX_TOKENS) return emptyList()
        return blocks(a, b, contextSize)
    }

    fun blocks(a: List<String>, b: List<String>, contextSize: Int = 3): List<ChangeBlock> {
        val pairs = align(a, b)
        val out = mutableListOf<ChangeBlock>()
        var i = 0
        while (i < pairs.size) {
            val op = pairs[i]
            if (op.kind == Kind.EQUAL) { i++; continue }
            val removed = mutableListOf<String>()
            val inserted = mutableListOf<String>()
            val start = i
            while (i < pairs.size && pairs[i].kind != Kind.EQUAL) {
                when (pairs[i].kind) {
                    Kind.DELETE -> removed += pairs[i].token
                    Kind.INSERT -> inserted += pairs[i].token
                    else -> {}
                }
                i++
            }
            val before = pairs.subList(maxOf(0, start - contextSize * 2), start)
                .filter { it.kind == Kind.EQUAL }.takeLast(contextSize).map { it.token }
            val after = pairs.subList(i, minOf(pairs.size, i + contextSize * 2))
                .filter { it.kind == Kind.EQUAL }.take(contextSize).map { it.token }
            out += ChangeBlock(removed, inserted, before, after)
        }
        return out
    }

    /** Fraction of tokens that survived the edit, 1.0 when nothing changed. */
    fun similarity(before: String, after: String): Double {
        val a = TextTokens.words(before)
        val b = TextTokens.words(after)
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.size > MAX_TOKENS || b.size > MAX_TOKENS) return if (before == after) 1.0 else 0.0
        val common = align(a, b).count { it.kind == Kind.EQUAL }
        return 2.0 * common / (a.size + b.size)
    }

    private enum class Kind { EQUAL, DELETE, INSERT }

    private data class Op(val kind: Kind, val token: String)

    /** Classic LCS alignment. Note sizes are small, so the table is affordable. */
    private fun align(a: List<String>, b: List<String>): List<Op> {
        val n = a.size
        val m = b.size
        val table = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                table[i][j] = if (a[i].equals(b[j], ignoreCase = true)) {
                    table[i + 1][j + 1] + 1
                } else {
                    maxOf(table[i + 1][j], table[i][j + 1])
                }
            }
        }
        val out = ArrayList<Op>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i].equals(b[j], ignoreCase = true) -> { out += Op(Kind.EQUAL, b[j]); i++; j++ }
                table[i + 1][j] >= table[i][j + 1] -> { out += Op(Kind.DELETE, a[i]); i++ }
                else -> { out += Op(Kind.INSERT, b[j]); j++ }
            }
        }
        while (i < n) { out += Op(Kind.DELETE, a[i]); i++ }
        while (j < m) { out += Op(Kind.INSERT, b[j]); j++ }
        return out
    }
}
