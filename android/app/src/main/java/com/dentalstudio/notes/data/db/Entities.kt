package com.dentalstudio.notes.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One dictated visit.
 *
 * [draftNote] is what the generator produced and never changes. [finalNote] is
 * what the clinician kept. Holding both is what makes learning possible: the
 * difference between them is the whole training signal.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientLabel: String,
    val templateId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val durationSec: Int = 0,
    val transcript: String = "",
    val draftNote: String = "",
    val finalNote: String = "",
    val edited: Boolean = false,
    val learned: Boolean = false,
    val appliedRuleCount: Int = 0,
    val generatorLabel: String = "",
)

/** An audit trail of every edit, kept so learning can be recomputed or undone. */
@Entity(
    tableName = "edit_events",
    indices = [Index("noteId")],
)
data class EditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val templateId: String,
    val createdAt: Long,
    val draftText: String,
    val finalText: String,
    val wordDelta: Int,
    val rulesLearned: Int,
)

/** A learned preference with the evidence that supports it. */
@Entity(
    tableName = "rules",
    indices = [Index(value = ["type", "scope", "pattern", "replacement"], unique = true)],
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val scope: String,
    val pattern: String,
    val replacement: String,
    val occurrences: Int,
    val contradictions: Int,
    val enabled: Boolean,
    val exampleBefore: String,
    val exampleAfter: String,
    val createdAt: Long,
    val lastSeenAt: Long,
)

/** Running style averages, one row per template plus one global row. */
@Entity(tableName = "style_profiles")
data class StyleProfileEntity(
    @PrimaryKey val scope: String,
    val samples: Int,
    val avgDraftWords: Double,
    val avgFinalWords: Double,
    val avgBulletRatio: Double,
    val avgSentenceLength: Double,
    val avgSimilarity: Double,
    val sectionOrder: String,
    val updatedAt: Long,
)
