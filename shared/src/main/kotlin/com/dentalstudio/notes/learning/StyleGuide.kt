package com.dentalstudio.notes.learning

import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.domain.ToothNotation

/** A previous note kept verbatim so the model can copy the clinician's voice. */
data class StyleExemplar(
    val templateId: String,
    val transcript: String,
    val finalNote: String,
)

/** Everything learned so far, in the form the prompt builder consumes. */
data class StyleContext(
    val rules: List<LearnedRule> = emptyList(),
    val profile: StyleProfile = StyleProfile(),
    val templateProfile: StyleProfile = StyleProfile(),
    val exemplars: List<StyleExemplar> = emptyList(),
    val toothNotation: ToothNotation = ToothNotation.FDI,
) {
    val activeRules: List<LearnedRule> get() = rules.filter { it.isActive }
    val appliedRules: List<LearnedRule> get() = rules.filter { it.isAutoApplied }
    val hasLearning: Boolean get() = activeRules.isNotEmpty() || profile.samples > 0
}

/**
 * Renders learned preferences as instructions for the note generator.
 *
 * Two things happen with what the app learns. High confidence rewrites are
 * applied to the output directly by [RuleApplier]; everything else is described
 * here so the model produces the right note first time instead of being
 * corrected afterwards.
 */
object StyleGuide {

    private const val MAX_RULES_PER_KIND = 12

    fun build(context: StyleContext, template: NoteTemplate): String {
        if (!context.hasLearning) return ""
        val sb = StringBuilder()
        sb.appendLine("<clinician_style>")
        sb.appendLine(
            "These preferences were learned from ${context.profile.samples} note(s) this " +
                "clinician edited. Follow them exactly — they outrank the generic house style."
        )

        lengthGuidance(context, template)?.let { sb.appendLine(it) }
        formatGuidance(context)?.let { sb.appendLine(it) }

        val scoped = context.activeRules.filter { it.scope == MinedRule.SCOPE_ALL || it.scope == template.id }

        scoped.filter { it.type == RuleType.TERM }
            .sortedByDescending { it.occurrences }
            .take(MAX_RULES_PER_KIND)
            .takeIf { it.isNotEmpty() }
            ?.let { terms ->
                sb.appendLine()
                sb.appendLine("Wording — always use the clinician's term:")
                terms.forEach { sb.appendLine("  - write \"${it.replacement}\", never \"${it.pattern}\"") }
            }

        scoped.filter { it.type == RuleType.REMOVE }
            .sortedByDescending { it.occurrences }
            .take(MAX_RULES_PER_KIND)
            .takeIf { it.isNotEmpty() }
            ?.let { removals ->
                sb.appendLine()
                sb.appendLine("Never include this filler — the clinician deletes it every time:")
                removals.forEach { sb.appendLine("  - \"${it.pattern}\"") }
            }

        scoped.filter { it.type == RuleType.ADD }
            .sortedByDescending { it.occurrences }
            .take(MAX_RULES_PER_KIND)
            .takeIf { it.isNotEmpty() }
            ?.let { additions ->
                sb.appendLine()
                sb.appendLine("Standard wording the clinician always adds — include it when the visit warrants it:")
                additions.forEach { sb.appendLine("  - \"${it.exampleAfter.ifBlank { it.pattern }}\"") }
            }

        scoped.filter { it.type == RuleType.SECTION_DROP }
            .sortedByDescending { it.occurrences }
            .takeIf { it.isNotEmpty() }
            ?.let { drops ->
                sb.appendLine()
                sb.appendLine("Omit these headings entirely for this template:")
                drops.forEach { sb.appendLine("  - ${it.pattern}") }
            }

        val order = context.templateProfile.sectionOrder.takeIf { it.size >= 2 }
        if (order != null) {
            sb.appendLine()
            sb.appendLine("Heading order the clinician keeps: ${order.joinToString(" → ")}")
        }

        sb.appendLine()
        sb.appendLine("Tooth numbering: ${context.toothNotation.label} (${context.toothNotation.example}).")
        sb.append("</clinician_style>")
        return sb.toString()
    }

    /** Worked examples carry tone that no list of rules can express. */
    fun exemplarBlock(context: StyleContext): String {
        if (context.exemplars.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("<approved_examples>")
        sb.appendLine("Notes this clinician approved. Match this voice, density and layout.")
        context.exemplars.forEachIndexed { i, ex ->
            sb.appendLine()
            sb.appendLine("Example ${i + 1} — dictation:")
            sb.appendLine(ex.transcript.take(1200).trim())
            sb.appendLine("Example ${i + 1} — the note they kept:")
            sb.appendLine(ex.finalNote.take(2000).trim())
        }
        sb.append("</approved_examples>")
        return sb.toString()
    }

    private fun lengthGuidance(context: StyleContext, template: NoteTemplate): String? {
        val profile = context.templateProfile.takeIf { it.samples >= 2 } ?: context.profile
        if (profile.samples < 2) return null
        val target = profile.targetWords
        if (target <= 0) return null
        val trim = (profile.trimRatio * 100).toInt()
        return if (trim >= 15) {
            "Length: aim for about $target words for a ${template.name} note. " +
                "This clinician cuts roughly $trim% out of drafts, so write tight from the start."
        } else {
            "Length: aim for about $target words for a ${template.name} note."
        }
    }

    private fun formatGuidance(context: StyleContext): String? = when {
        context.profile.prefersBullets ->
            "Layout: short bullet lines under each heading, not paragraphs."
        context.profile.prefersProse ->
            "Layout: continuous prose under each heading, not bullet lists."
        else -> null
    }
}
