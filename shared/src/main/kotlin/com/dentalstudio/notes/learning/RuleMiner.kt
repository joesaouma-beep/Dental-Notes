package com.dentalstudio.notes.learning

/**
 * Turns one (draft, edited) pair into candidate preferences.
 *
 * The miner is deliberately conservative. Anything that looks like clinical
 * content for this particular patient — numbers, tooth codes, doses, names —
 * is discarded, because repeating it on the next patient would be wrong and
 * potentially unsafe. Only stylistic habits survive.
 */
object RuleMiner {

    /** Longest substitution worth learning, per side, in words. */
    private const val MAX_SUBSTITUTION_WORDS = 5

    /** Longest deletion learned as a phrase rather than a sentence. */
    private const val MAX_PHRASE_WORDS = 12

    /** Deciduous teeth written in Palmer notation, e.g. "E" or "C". */
    private val DECIDUOUS_CODE = Regex("(?<![A-Za-z])[B-E](?![A-Za-z])")

    fun mine(
        draft: String,
        final: String,
        templateId: String,
        existingRules: List<LearnedRule> = emptyList(),
    ): MinedEdit {
        val rules = mutableListOf<MinedRule>()

        rules += mineSectionDrops(draft, final, templateId)
        rules += minePhrases(draft, final, templateId)

        val finalBody = NoteStructure.bodyOnly(final)
        val sentences = TextTokens.sentences(finalBody)
        val observation = StyleObservation(
            templateId = templateId,
            draftWords = TextTokens.wordCount(NoteStructure.bodyOnly(draft)),
            finalWords = TextTokens.wordCount(finalBody),
            bulletRatio = TextTokens.bulletLineRatio(final),
            sentenceLength = if (sentences.isEmpty()) 0.0
            else sentences.sumOf { TextTokens.wordCount(it) }.toDouble() / sentences.size,
            sectionOrder = NoteStructure.parse(final).map { it.heading },
            similarity = TextDiff.similarity(draft, final),
        )

        return MinedEdit(
            rules = dedupe(rules),
            observation = observation,
            contradictedPatterns = contradictions(existingRules, draft, final),
        )
    }

    /**
     * A rule is contradicted when its trigger was present in the draft and the
     * clinician chose to keep it. That is evidence against the rule, and it is
     * how a preference that was a one-off fades away again.
     */
    fun contradictions(rules: List<LearnedRule>, draft: String, final: String): List<String> {
        val draftNorm = TextTokens.normalize(draft)
        val finalNorm = TextTokens.normalize(final)
        return rules.filter { rule ->
            when (rule.type) {
                RuleType.TERM, RuleType.REMOVE ->
                    draftNorm.contains(rule.pattern) && finalNorm.contains(rule.pattern)
                RuleType.ADD ->
                    !finalNorm.contains(rule.pattern)
                RuleType.SECTION_DROP ->
                    NoteStructure.parse(final).any { it.heading == rule.pattern }
            }
        }.map { it.pattern }
    }

    private fun mineSectionDrops(draft: String, final: String, templateId: String): List<MinedRule> {
        val draftSections = NoteStructure.parse(draft)
        if (draftSections.size < 2) return emptyList()
        val finalHeadings = NoteStructure.parse(final).map { it.heading }.toSet()
        return draftSections
            .filter { it.heading !in finalHeadings && it.body.isNotBlank() }
            .map {
                MinedRule(
                    type = RuleType.SECTION_DROP,
                    pattern = it.heading,
                    exampleBefore = it.body.lineSequence().first().take(120),
                    scope = templateId,
                )
            }
    }

    private fun minePhrases(draft: String, final: String, templateId: String): List<MinedRule> {
        val out = mutableListOf<MinedRule>()
        val draftSections = NoteStructure.parse(draft).associateBy { it.heading }
        val finalSections = NoteStructure.parse(final).associateBy { it.heading }

        // Diff section by section where possible: it keeps the alignment honest
        // and stops a moved paragraph from looking like a rewrite.
        val shared = draftSections.keys.intersect(finalSections.keys)
        if (shared.isNotEmpty()) {
            shared.forEach { heading ->
                out += fromBlocks(
                    TextDiff.blocks(draftSections.getValue(heading).body, finalSections.getValue(heading).body),
                    templateId,
                )
            }
        } else {
            out += fromBlocks(TextDiff.blocks(draft, final), templateId)
        }

        out += mineAddedSentences(draft, final, templateId)
        return out
    }

    private fun fromBlocks(blocks: List<ChangeBlock>, templateId: String): List<MinedRule> {
        val out = mutableListOf<MinedRule>()
        blocks.forEach { block ->
            val removed = block.removedText
            val inserted = block.insertedText
            when {
                block.isSubstitution -> {
                    if (!isLearnableTerm(removed) || !isLearnableTerm(inserted)) return@forEach
                    if (TextTokens.wordCount(removed) > MAX_SUBSTITUTION_WORDS) return@forEach
                    if (TextTokens.wordCount(inserted) > MAX_SUBSTITUTION_WORDS) return@forEach
                    val pattern = TextTokens.normalize(removed)
                    val replacement = TextTokens.normalize(inserted)
                    if (pattern.isEmpty() || replacement.isEmpty() || pattern == replacement) return@forEach
                    out += MinedRule(
                        type = RuleType.TERM,
                        pattern = pattern,
                        replacement = replacement,
                        exampleBefore = removed,
                        exampleAfter = inserted,
                        scope = MinedRule.SCOPE_ALL,
                    )
                }

                block.isDeletion -> {
                    if (!isLearnableTerm(removed)) return@forEach
                    if (TextTokens.wordCount(removed) > MAX_PHRASE_WORDS) return@forEach
                    if (TextTokens.wordCount(removed) < 2) return@forEach
                    out += MinedRule(
                        type = RuleType.REMOVE,
                        pattern = TextTokens.normalize(removed),
                        exampleBefore = removed,
                        scope = templateId,
                    )
                }
            }
        }
        return out
    }

    /**
     * Whole sentences the clinician typed in that carry no patient specifics are
     * boilerplate they expect to see every time — consent wording, post-op
     * advice, medico-legal phrasing.
     */
    private fun mineAddedSentences(draft: String, final: String, templateId: String): List<MinedRule> {
        val draftSentences = TextTokens.sentences(NoteStructure.bodyOnly(draft))
            .map { TextTokens.normalize(it) }.toSet()
        return TextTokens.sentences(NoteStructure.bodyOnly(final))
            .filter { sentence ->
                val norm = TextTokens.normalize(sentence)
                norm !in draftSentences &&
                    TextTokens.wordCount(sentence) in 3..20 &&
                    isLearnableTerm(sentence) &&
                    draftSentences.none { TextDiff.similarity(it, norm) > 0.7 }
            }
            .map {
                MinedRule(
                    type = RuleType.ADD,
                    pattern = TextTokens.normalize(it),
                    exampleAfter = it,
                    scope = templateId,
                )
            }
    }

    /**
     * Rejects fragments that describe this patient rather than the clinician's
     * style: anything numeric (tooth numbers, doses, probing depths, shades),
     * and anything made only of filler words.
     */
    private fun isLearnableTerm(fragment: String): Boolean {
        val trimmed = fragment.trim()
        if (trimmed.length < 3) return false
        if (trimmed.any { it.isDigit() }) return false
        if (DECIDUOUS_CODE.containsMatchIn(trimmed)) return false
        if (TextTokens.containsNumberWord(trimmed)) return false
        if (TextTokens.isAllStopwords(trimmed)) return false
        if (trimmed.none { it.isLetter() }) return false
        return true
    }

    private fun dedupe(rules: List<MinedRule>): List<MinedRule> =
        rules.distinctBy { listOf(it.type, it.scope, it.pattern, it.replacement) }
}
