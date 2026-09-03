package com.dentalstudio.notes.desktop.store

import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.generate.AnthropicNoteGenerator
import com.dentalstudio.notes.generate.GenerationRequest
import com.dentalstudio.notes.generate.OnDeviceNoteGenerator
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.LearningMerge
import com.dentalstudio.notes.learning.MinedEdit
import com.dentalstudio.notes.learning.RuleApplier
import com.dentalstudio.notes.learning.RuleMiner
import com.dentalstudio.notes.learning.StyleContext
import com.dentalstudio.notes.learning.StyleExemplar
import com.dentalstudio.notes.learning.StyleProfile
import com.dentalstudio.notes.learning.TextTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The note the clinician is about to see, and what learning contributed to it. */
data class GeneratedNote(
    val note: String,
    val generatorLabel: String,
    val appliedRules: List<LearnedRule>,
)

/** What one saved edit taught the app. */
data class LearningOutcome(
    val newRules: Int,
    val reinforcedRules: Int,
    val wordsTrimmed: Int,
) {
    val learnedSomething: Boolean get() = newRules > 0 || reinforcedRules > 0
}

/**
 * The desktop equivalent of the Android NoteRepository.
 *
 * Storage differs — JSON files rather than Room — but every decision about what
 * to learn comes from the shared RuleMiner and LearningMerge, so a clinician
 * using both gets the same preferences applied the same way.
 */
class DesktopRepository(
    private val workspaceStore: JsonStore<Workspace> = JsonStore(
        AppPaths.workspaceFile, Workspace.serializer()
    ) { Workspace() },
    private val settingsStore: JsonStore<DesktopSettings> = JsonStore(
        AppPaths.settingsFile, DesktopSettings.serializer()
    ) { DesktopSettings() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val writeLock = Mutex()

    private val _workspace = MutableStateFlow(workspaceStore.load())
    val workspace: StateFlow<Workspace> = _workspace.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    private val remoteGenerator = AnthropicNoteGenerator(
        apiKeyProvider = { _settings.value.apiKey },
        modelProvider = { _settings.value.model },
    )
    private val onDeviceGenerator = OnDeviceNoteGenerator()

    val rules: List<LearnedRule> get() = _workspace.value.rules.map { it.toDomain() }

    fun profile(scope: String = "*"): StyleProfile =
        _workspace.value.profiles.firstOrNull { it.scope == scope }?.toDomain() ?: StyleProfile(scope = scope)

    fun note(id: Long): NoteRecord? = _workspace.value.notes.firstOrNull { it.id == id }

    // ── Generation ────────────────────────────────────────────────────────────

    suspend fun generate(transcript: String, template: NoteTemplate, patientLabel: String): GeneratedNote {
        val settings = _settings.value
        val style = styleContext(template.id)

        val result = (if (settings.hasApiKey) remoteGenerator else onDeviceGenerator).generate(
            GenerationRequest(
                transcript = transcript,
                template = template,
                patientLabel = patientLabel,
                style = style,
                clinicianName = settings.clinicianName,
            )
        )

        val applied = RuleApplier.apply(
            result.note,
            if (settings.autoApplyLearning) style.rules else emptyList(),
            template.id,
        )
        return GeneratedNote(applied.text, result.generatorLabel, applied.applied)
    }

    fun styleContext(templateId: String): StyleContext {
        val ws = _workspace.value
        val exemplars = ws.notes
            .filter { it.edited && it.templateId == templateId }
            .ifEmpty { ws.notes.filter { it.edited } }
            .sortedByDescending { it.updatedAt }
            .take(EXEMPLAR_LIMIT)
            .map { StyleExemplar(it.templateId, it.transcript, it.displayText) }

        return StyleContext(
            rules = ws.rules.map { it.toDomain() },
            profile = profile(GLOBAL_SCOPE),
            templateProfile = profile(templateId),
            exemplars = exemplars,
            toothNotation = _settings.value.notation,
        )
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    suspend fun createNote(
        patientLabel: String,
        templateId: String,
        transcript: String,
        generated: GeneratedNote,
        durationSec: Int,
    ): Long = mutate { ws ->
        val id = ws.nextNoteId
        val now = clock()
        ws.copy(
            notes = ws.notes + NoteRecord(
                id = id,
                patientLabel = patientLabel.ifBlank { "Unnamed visit" },
                templateId = templateId,
                createdAt = now,
                updatedAt = now,
                durationSec = durationSec,
                transcript = transcript,
                draftNote = generated.note,
                finalNote = generated.note,
                appliedRuleCount = generated.appliedRules.size,
                generatorLabel = generated.generatorLabel,
            ),
            nextNoteId = id + 1,
        ) to id
    }

    suspend fun deleteNote(id: Long) {
        mutate { ws -> ws.copy(notes = ws.notes.filterNot { it.id == id }) to Unit }
    }

    // ── Learning ──────────────────────────────────────────────────────────────

    /**
     * Saves an edited note and folds the difference into what the app knows.
     * Returns null when nothing meaningful changed.
     */
    suspend fun saveEdit(noteId: Long, editedText: String): LearningOutcome? = mutate { ws ->
        val note = ws.notes.firstOrNull { it.id == noteId } ?: return@mutate ws to null
        val previous = note.displayText
        if (editedText.trim() == previous.trim()) return@mutate ws to null

        val now = clock()
        var next = ws.copy(
            notes = ws.notes.map {
                if (it.id == noteId) it.copy(finalNote = editedText, edited = true, updatedAt = now) else it
            }
        )

        // Learn against the original draft, not the last saved version: the gap
        // between what was generated and what was kept is the real signal.
        val baseline = note.draftNote.ifBlank { previous }
        if (TextTokens.wordCount(baseline) < MIN_WORDS_TO_LEARN &&
            TextTokens.wordCount(editedText) < MIN_WORDS_TO_LEARN
        ) {
            return@mutate next to null
        }

        val existing = next.rules.map { it.toDomain() }
        val mined = RuleMiner.mine(baseline, editedText, note.templateId, existing)
        val plan = LearningMerge.plan(mined, existing)

        var nextRuleId = next.nextRuleId
        val added = plan.newRules.map { it.toRecord(nextRuleId++, now) }

        val updated = next.rules.map { rule ->
            when (rule.id) {
                in plan.reinforcedIds -> rule.copy(occurrences = rule.occurrences + 1, lastSeenAt = now, enabled = true)
                in plan.contradictedIds -> rule.copy(contradictions = rule.contradictions + 1)
                else -> rule
            }
        }.map { rule ->
            if (rule.id in plan.retiredIds) rule.copy(enabled = false) else rule
        }

        next = next.copy(
            rules = updated + added,
            nextRuleId = nextRuleId,
            profiles = mergeProfiles(next.profiles, mined, now),
            editCount = next.editCount + 1,
        )

        next to LearningOutcome(
            newRules = plan.newCount,
            reinforcedRules = plan.reinforcedCount,
            wordsTrimmed = (mined.observation.draftWords - mined.observation.finalWords).coerceAtLeast(0),
        )
    }

    private fun mergeProfiles(current: List<ProfileRecord>, mined: MinedEdit, now: Long): List<ProfileRecord> {
        var out = current
        listOf(GLOBAL_SCOPE, mined.observation.templateId).forEach { scope ->
            val existing = out.firstOrNull { it.scope == scope }?.toDomain() ?: StyleProfile(scope = scope)
            val merged = existing.merge(mined.observation, now).copy(scope = scope).toRecord()
            out = out.filterNot { it.scope == scope } + merged
        }
        return out
    }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
        mutate { ws ->
            ws.copy(rules = ws.rules.map { if (it.id == id) it.copy(enabled = enabled) else it }) to Unit
        }
    }

    suspend fun deleteRule(id: Long) {
        mutate { ws -> ws.copy(rules = ws.rules.filterNot { it.id == id }) to Unit }
    }

    suspend fun resetLearning() {
        mutate { ws -> ws.copy(rules = emptyList(), profiles = emptyList(), editCount = 0) to Unit }
    }

    suspend fun wipeEverything() {
        mutate { Workspace() to Unit }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    suspend fun updateSettings(block: (DesktopSettings) -> DesktopSettings) {
        writeLock.withLock {
            val next = block(_settings.value)
            _settings.value = next
            withContext(Dispatchers.IO) { settingsStore.save(next) }
        }
    }

    /** Applies a change to the workspace and persists it atomically. */
    private suspend fun <R> mutate(block: (Workspace) -> Pair<Workspace, R>): R = writeLock.withLock {
        val (next, result) = block(_workspace.value)
        if (next != _workspace.value) {
            _workspace.value = next
            withContext(Dispatchers.IO) { workspaceStore.save(next) }
        }
        result
    }

    companion object {
        const val GLOBAL_SCOPE = "*"
        private const val EXEMPLAR_LIMIT = 3
        private const val MIN_WORDS_TO_LEARN = 8
    }
}
