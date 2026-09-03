package com.dentalstudio.notes.learning

/** The kinds of preference the app can learn from an edit. */
enum class RuleType {
    /** Clinician consistently rewrites one phrase as another. */
    TERM,

    /** Clinician consistently deletes a sentence or phrase. */
    REMOVE,

    /** Clinician consistently types the same sentence in. */
    ADD,

    /** Clinician consistently deletes a whole section. */
    SECTION_DROP,
}

/** A candidate preference extracted from a single edit, before it is merged. */
data class MinedRule(
    val type: RuleType,
    val pattern: String,
    val replacement: String = "",
    val exampleBefore: String = "",
    val exampleAfter: String = "",
    val scope: String = SCOPE_ALL,
) {
    companion object {
        const val SCOPE_ALL = "*"
    }
}

/** A preference that has been seen at least once and carries evidence. */
data class LearnedRule(
    val id: Long = 0,
    val type: RuleType,
    val scope: String = MinedRule.SCOPE_ALL,
    val pattern: String,
    val replacement: String = "",
    val occurrences: Int = 1,
    val contradictions: Int = 0,
    val enabled: Boolean = true,
    val exampleBefore: String = "",
    val exampleAfter: String = "",
    val createdAt: Long = 0L,
    val lastSeenAt: Long = 0L,
) {
    val confidence: Double
        get() = occurrences.toDouble() / (occurrences + contradictions).coerceAtLeast(1)

    /** Strong enough to describe in the prompt. */
    val isActive: Boolean
        get() = enabled && occurrences >= MIN_OCCURRENCES_PROMPT && confidence >= MIN_CONFIDENCE_PROMPT

    /** Strong enough to rewrite the model output directly, without asking. */
    val isAutoApplied: Boolean
        get() = enabled && occurrences >= MIN_OCCURRENCES_APPLY && confidence >= MIN_CONFIDENCE_APPLY

    companion object {
        const val MIN_OCCURRENCES_PROMPT = 2
        const val MIN_CONFIDENCE_PROMPT = 0.55
        const val MIN_OCCURRENCES_APPLY = 3
        const val MIN_CONFIDENCE_APPLY = 0.75
    }
}

/** Aggregate statistics describing how the clinician writes. */
data class StyleObservation(
    val templateId: String,
    val draftWords: Int,
    val finalWords: Int,
    val bulletRatio: Double,
    val sentenceLength: Double,
    val sectionOrder: List<String>,
    val similarity: Double,
)

/** Running averages kept per template, updated with every saved edit. */
data class StyleProfile(
    val scope: String = MinedRule.SCOPE_ALL,
    val samples: Int = 0,
    val avgDraftWords: Double = 0.0,
    val avgFinalWords: Double = 0.0,
    val avgBulletRatio: Double = 0.0,
    val avgSentenceLength: Double = 0.0,
    val avgSimilarity: Double = 1.0,
    val sectionOrder: List<String> = emptyList(),
    val updatedAt: Long = 0L,
) {
    /** How much shorter the clinician makes the draft, 0.0 when they add length. */
    val trimRatio: Double
        get() = if (avgDraftWords <= 0.0) 0.0 else (1.0 - avgFinalWords / avgDraftWords).coerceIn(0.0, 0.9)

    val prefersBullets: Boolean get() = samples >= 2 && avgBulletRatio >= 0.5
    val prefersProse: Boolean get() = samples >= 2 && avgBulletRatio <= 0.15

    val targetWords: Int
        get() = if (samples == 0) 0 else avgFinalWords.toInt().coerceAtLeast(25)

    fun merge(o: StyleObservation, now: Long): StyleProfile {
        val n = samples + 1
        fun avg(old: Double, new: Double) = (old * samples + new) / n
        return copy(
            samples = n,
            avgDraftWords = avg(avgDraftWords, o.draftWords.toDouble()),
            avgFinalWords = avg(avgFinalWords, o.finalWords.toDouble()),
            avgBulletRatio = avg(avgBulletRatio, o.bulletRatio),
            avgSentenceLength = avg(avgSentenceLength, o.sentenceLength),
            avgSimilarity = avg(avgSimilarity, o.similarity),
            sectionOrder = if (o.sectionOrder.isNotEmpty()) o.sectionOrder else sectionOrder,
            updatedAt = now,
        )
    }
}

/** Everything a single saved edit taught the app. */
data class MinedEdit(
    val rules: List<MinedRule>,
    val observation: StyleObservation,
    /** Patterns of existing rules the clinician left untouched this time. */
    val contradictedPatterns: List<String>,
)
