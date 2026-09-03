package com.dentalstudio.notes.learning

/**
 * Decides what a mined edit should change about the stored preferences.
 *
 * Both the Android app (Room) and the desktop app (a JSON store) keep their
 * preferences differently, but the decision — what is new, what is reinforced,
 * what has just been argued with, what should be retired — must be identical on
 * both, or the same clinician would get different notes on their phone and
 * their desktop. That decision lives here, as a pure function over the current
 * rules, and each platform only has to apply the resulting plan.
 */
object LearningMerge {

    /** Retire a preference once it has been overruled this many times... */
    const val RETIRE_AFTER_CONTRADICTIONS = 3

    /** ...and its confidence has fallen this low. */
    const val RETIRE_BELOW_CONFIDENCE = 0.4

    data class Plan(
        /** Preferences seen for the first time. */
        val newRules: List<MinedRule> = emptyList(),
        /** Ids of preferences seen again, which gain confidence. */
        val reinforcedIds: List<Long> = emptyList(),
        /** Ids of preferences the clinician overruled this time. */
        val contradictedIds: List<Long> = emptyList(),
        /** Ids of preferences that have now been overruled too often to keep using. */
        val retiredIds: List<Long> = emptyList(),
    ) {
        val newCount: Int get() = newRules.size
        val reinforcedCount: Int get() = reinforcedIds.size
        val learnedSomething: Boolean get() = newCount > 0 || reinforcedCount > 0
    }

    fun plan(mined: MinedEdit, existing: List<LearnedRule>): Plan {
        val byIdentity = existing.associateBy { identity(it.type, it.scope, it.pattern, it.replacement) }

        val newRules = mutableListOf<MinedRule>()
        val reinforced = mutableListOf<Long>()
        mined.rules.forEach { candidate ->
            val match = byIdentity[identity(candidate.type, candidate.scope, candidate.pattern, candidate.replacement)]
            if (match == null) newRules += candidate else reinforced += match.id
        }

        val contradictedPatterns = mined.contradictedPatterns.toSet()
        val contradicted = existing.filter { it.pattern in contradictedPatterns }

        // Retirement is judged on the counts as they will stand after this edit,
        // so a preference that tips over the threshold is switched off now
        // rather than steering one more note first.
        val retired = contradicted
            .map { it.copy(contradictions = it.contradictions + 1) }
            .filter { it.contradictions >= RETIRE_AFTER_CONTRADICTIONS && it.confidence < RETIRE_BELOW_CONFIDENCE }
            .map { it.id }

        return Plan(
            newRules = newRules,
            reinforcedIds = reinforced,
            contradictedIds = contradicted.map { it.id },
            retiredIds = retired,
        )
    }

    private fun identity(type: RuleType, scope: String, pattern: String, replacement: String) =
        listOf(type.name, scope, pattern, replacement)
}
