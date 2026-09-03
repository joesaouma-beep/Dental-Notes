package com.dentalstudio.notes.data.repo

import com.dentalstudio.notes.data.db.AppDatabase
import com.dentalstudio.notes.data.db.EditEventEntity
import com.dentalstudio.notes.data.db.NoteEntity
import com.dentalstudio.notes.data.generate.AnthropicNoteGenerator
import com.dentalstudio.notes.data.generate.GenerationRequest
import com.dentalstudio.notes.data.generate.OnDeviceNoteGenerator
import com.dentalstudio.notes.data.prefs.AppSettings
import com.dentalstudio.notes.data.prefs.SettingsStore
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.MinedEdit
import com.dentalstudio.notes.learning.RuleApplier
import com.dentalstudio.notes.learning.RuleMiner
import com.dentalstudio.notes.learning.StyleContext
import com.dentalstudio.notes.learning.StyleExemplar
import com.dentalstudio.notes.learning.StyleProfile
import com.dentalstudio.notes.learning.TextTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The note the clinician is about to see, and what learning contributed to it. */
data class GeneratedNote(
    val note: String,
    val generatorLabel: String,
    val appliedRules: List<LearnedRule>,
    val styleSamples: Int,
)

/** What one saved edit taught the app, for the confirmation shown afterwards. */
data class LearningOutcome(
    val newRules: Int,
    val reinforcedRules: Int,
    val wordsTrimmed: Int,
) {
    val learnedSomething: Boolean get() = newRules > 0 || reinforcedRules > 0
}

/**
 * Owns notes and everything the app has learned from editing them.
 *
 * The loop is: generate a draft, let the clinician correct it, diff the two,
 * and fold the difference back into the rules and style profile that shape the
 * next draft.
 */
class NoteRepository(
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val remoteGenerator: AnthropicNoteGenerator,
    private val onDeviceGenerator: OnDeviceNoteGenerator = OnDeviceNoteGenerator(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val notes = db.noteDao()
    private val events = db.editEventDao()
    private val rules = db.ruleDao()
    private val profiles = db.styleProfileDao()

    val allNotes: Flow<List<NoteEntity>> = notes.observeAll()
    val allRules: Flow<List<LearnedRule>> = rules.observeAll().map { list -> list.map { it.toDomain() } }
    val editCount: Flow<Int> = events.count()

    fun note(id: Long): Flow<NoteEntity?> = notes.observe(id)

    fun notesSince(timestamp: Long): Flow<Int> = notes.countSince(timestamp)

    val globalProfile: Flow<StyleProfile> = profiles.observeAll().map { list ->
        list.firstOrNull { it.scope == GLOBAL_SCOPE }?.toDomain() ?: StyleProfile()
    }

    // ── Generation ────────────────────────────────────────────────────────────

    suspend fun generate(
        transcript: String,
        template: NoteTemplate,
        patientLabel: String,
    ): GeneratedNote {
        val settings = settingsStore.settings.first()
        val style = styleContext(template.id, settings)
        val request = GenerationRequest(
            transcript = transcript,
            template = template,
            patientLabel = patientLabel,
            style = style,
            clinicianName = settings.clinicianName,
        )

        val generator = if (settings.hasApiKey) remoteGenerator else onDeviceGenerator
        val result = generator.generate(request)

        val applied = if (settings.autoApplyLearning) {
            RuleApplier.apply(result.note, style.rules, template.id)
        } else {
            RuleApplier.apply(result.note, emptyList(), template.id)
        }

        return GeneratedNote(
            note = applied.text,
            generatorLabel = result.generatorLabel,
            appliedRules = applied.applied,
            styleSamples = style.profile.samples,
        )
    }

    /** Everything learned so far, assembled for one template. */
    suspend fun styleContext(templateId: String, settings: AppSettings? = null): StyleContext {
        val resolved = settings ?: settingsStore.settings.first()
        val learned = rules.all().map { it.toDomain() }
        val global = profiles.byScope(GLOBAL_SCOPE)?.toDomain() ?: StyleProfile()
        val perTemplate = profiles.byScope(templateId)?.toDomain() ?: StyleProfile(scope = templateId)

        val exemplarNotes = notes.recentEditedForTemplate(templateId, EXEMPLAR_LIMIT)
            .ifEmpty { notes.recentEdited(EXEMPLAR_LIMIT) }

        return StyleContext(
            rules = learned,
            profile = global,
            templateProfile = perTemplate,
            exemplars = exemplarNotes.map {
                StyleExemplar(it.templateId, it.transcript, it.finalNote.ifBlank { it.draftNote })
            },
            toothNotation = resolved.toothNotation,
        )
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    suspend fun createNote(
        patientLabel: String,
        templateId: String,
        transcript: String,
        generated: GeneratedNote,
        durationSec: Int,
    ): Long {
        val now = clock()
        return notes.insert(
            NoteEntity(
                patientLabel = patientLabel.ifBlank { "Unnamed visit" },
                templateId = templateId,
                createdAt = now,
                updatedAt = now,
                durationSec = durationSec,
                transcript = transcript,
                draftNote = generated.note,
                finalNote = generated.note,
                edited = false,
                learned = false,
                appliedRuleCount = generated.appliedRules.size,
                generatorLabel = generated.generatorLabel,
            )
        )
    }

    suspend fun deleteNote(id: Long) = notes.delete(id)

    // ── Learning ──────────────────────────────────────────────────────────────

    /**
     * Saves an edited note and folds the difference into what the app knows.
     *
     * Returns null when nothing meaningful changed, so the UI can stay quiet
     * about typo-sized corrections.
     */
    suspend fun saveEdit(noteId: Long, editedText: String): LearningOutcome? {
        val note = notes.byId(noteId) ?: return null
        val previous = note.finalNote.ifBlank { note.draftNote }
        val now = clock()

        if (editedText.trim() == previous.trim()) return null

        notes.update(
            note.copy(
                finalNote = editedText,
                edited = true,
                updatedAt = now,
            )
        )

        // Learn against the original draft, not the last saved version: the gap
        // between what was generated and what was kept is the real signal.
        val baseline = note.draftNote.ifBlank { previous }
        if (TextDiffTooSmall(baseline, editedText)) return null

        val existing = rules.all().map { it.toDomain() }
        val mined = RuleMiner.mine(baseline, editedText, note.templateId, existing)
        val outcome = absorb(mined, existing, now)

        events.insert(
            EditEventEntity(
                noteId = noteId,
                templateId = note.templateId,
                createdAt = now,
                draftText = baseline,
                finalText = editedText,
                wordDelta = mined.observation.finalWords - mined.observation.draftWords,
                rulesLearned = outcome.newRules + outcome.reinforcedRules,
            )
        )
        notes.byId(noteId)?.let { notes.update(it.copy(learned = true)) }
        return outcome
    }

    private suspend fun absorb(mined: MinedEdit, existing: List<LearnedRule>, now: Long): LearningOutcome {
        var added = 0
        var reinforced = 0

        mined.rules.forEach { candidate ->
            val match = rules.find(
                candidate.type.name, candidate.scope, candidate.pattern, candidate.replacement,
            )
            if (match == null) {
                rules.insert(candidate.toEntity(now))
                added++
            } else {
                rules.reinforce(match.id, now)
                reinforced++
            }
        }

        val contradicted = mined.contradictedPatterns.toSet()
        existing.filter { it.pattern in contradicted }.forEach { rules.contradict(it.id) }
        rules.retireWeakRules()

        updateProfile(GLOBAL_SCOPE, mined, now)
        updateProfile(mined.observation.templateId, mined, now)

        return LearningOutcome(
            newRules = added,
            reinforcedRules = reinforced,
            wordsTrimmed = (mined.observation.draftWords - mined.observation.finalWords).coerceAtLeast(0),
        )
    }

    private suspend fun updateProfile(scope: String, mined: MinedEdit, now: Long) {
        val current = profiles.byScope(scope)?.toDomain() ?: StyleProfile(scope = scope)
        profiles.upsert(current.merge(mined.observation, now).copy(scope = scope).toEntity())
    }

    /** Ignores edits too small to be a preference, such as fixing one typo. */
    @Suppress("FunctionName")
    private fun TextDiffTooSmall(before: String, after: String): Boolean {
        val beforeWords = TextTokens.wordCount(before)
        val afterWords = TextTokens.wordCount(after)
        return beforeWords < MIN_WORDS_TO_LEARN && afterWords < MIN_WORDS_TO_LEARN
    }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) = rules.setEnabled(id, enabled)

    suspend fun deleteRule(id: Long) = rules.delete(id)

    /** Forgets every learned preference but keeps the notes themselves. */
    suspend fun resetLearning() {
        rules.clear()
        profiles.clear()
        events.clear()
    }

    suspend fun wipeEverything() {
        resetLearning()
        notes.clear()
    }

    private companion object {
        const val EXEMPLAR_LIMIT = 3
        const val MIN_WORDS_TO_LEARN = 8
    }
}
