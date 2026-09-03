package com.dentalstudio.notes.desktop.store

import com.dentalstudio.notes.domain.ToothNotation
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.MinedRule
import com.dentalstudio.notes.learning.RuleType
import com.dentalstudio.notes.learning.StyleProfile
import kotlinx.serialization.Serializable

/**
 * On-disk shapes.
 *
 * The desktop app keeps everything in two JSON files rather than a database:
 * the data is small, a clinician can read or back up the files themselves, and
 * there is no native SQLite library to ship with the installer.
 */
@Serializable
data class NoteRecord(
    val id: Long,
    val patientLabel: String,
    val templateId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val durationSec: Int = 0,
    val transcript: String = "",
    val draftNote: String = "",
    val finalNote: String = "",
    val edited: Boolean = false,
    val appliedRuleCount: Int = 0,
    val generatorLabel: String = "",
) {
    val displayText: String get() = finalNote.ifBlank { draftNote }
}

@Serializable
data class RuleRecord(
    val id: Long,
    val type: String,
    val scope: String,
    val pattern: String,
    val replacement: String = "",
    val occurrences: Int = 1,
    val contradictions: Int = 0,
    val enabled: Boolean = true,
    val exampleBefore: String = "",
    val exampleAfter: String = "",
    val createdAt: Long = 0,
    val lastSeenAt: Long = 0,
)

@Serializable
data class ProfileRecord(
    val scope: String,
    val samples: Int = 0,
    val avgDraftWords: Double = 0.0,
    val avgFinalWords: Double = 0.0,
    val avgBulletRatio: Double = 0.0,
    val avgSentenceLength: Double = 0.0,
    val avgSimilarity: Double = 1.0,
    val sectionOrder: List<String> = emptyList(),
    val updatedAt: Long = 0,
)

@Serializable
data class Workspace(
    val notes: List<NoteRecord> = emptyList(),
    val rules: List<RuleRecord> = emptyList(),
    val profiles: List<ProfileRecord> = emptyList(),
    val editCount: Int = 0,
    val nextNoteId: Long = 1,
    val nextRuleId: Long = 1,
)

@Serializable
data class DesktopSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val toothNotation: String = ToothNotation.FDI.name,
    val clinicianName: String = "",
    val practiceName: String = "",
    val autoApplyLearning: Boolean = true,
    /** Directory holding an unpacked Vosk model, or blank for no dictation. */
    val voskModelPath: String = "",
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val notation: ToothNotation
        get() = runCatching { ToothNotation.valueOf(toothNotation) }.getOrDefault(ToothNotation.FDI)

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5"
        val MODELS = listOf(
            "claude-sonnet-5" to "Sonnet 5 — balanced, recommended",
            "claude-opus-5" to "Opus 5 — most thorough",
            "claude-haiku-4-5-20251001" to "Haiku 4.5 — fastest",
        )
    }
}

// ── Conversions to and from the shared learning model ────────────────────────

fun RuleRecord.toDomain(): LearnedRule = LearnedRule(
    id = id,
    type = runCatching { RuleType.valueOf(type) }.getOrDefault(RuleType.TERM),
    scope = scope,
    pattern = pattern,
    replacement = replacement,
    occurrences = occurrences,
    contradictions = contradictions,
    enabled = enabled,
    exampleBefore = exampleBefore,
    exampleAfter = exampleAfter,
    createdAt = createdAt,
    lastSeenAt = lastSeenAt,
)

fun MinedRule.toRecord(id: Long, now: Long): RuleRecord = RuleRecord(
    id = id,
    type = type.name,
    scope = scope,
    pattern = pattern,
    replacement = replacement,
    occurrences = 1,
    contradictions = 0,
    enabled = true,
    exampleBefore = exampleBefore,
    exampleAfter = exampleAfter,
    createdAt = now,
    lastSeenAt = now,
)

fun ProfileRecord.toDomain(): StyleProfile = StyleProfile(
    scope = scope,
    samples = samples,
    avgDraftWords = avgDraftWords,
    avgFinalWords = avgFinalWords,
    avgBulletRatio = avgBulletRatio,
    avgSentenceLength = avgSentenceLength,
    avgSimilarity = avgSimilarity,
    sectionOrder = sectionOrder,
    updatedAt = updatedAt,
)

fun StyleProfile.toRecord(): ProfileRecord = ProfileRecord(
    scope = scope,
    samples = samples,
    avgDraftWords = avgDraftWords,
    avgFinalWords = avgFinalWords,
    avgBulletRatio = avgBulletRatio,
    avgSentenceLength = avgSentenceLength,
    avgSimilarity = avgSimilarity,
    sectionOrder = sectionOrder,
    updatedAt = updatedAt,
)
