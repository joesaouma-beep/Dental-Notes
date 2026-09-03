package com.dentalstudio.notes.desktop

import com.dentalstudio.notes.desktop.store.DesktopRepository
import com.dentalstudio.notes.desktop.store.DesktopSettings
import com.dentalstudio.notes.desktop.store.JsonStore
import com.dentalstudio.notes.desktop.store.Workspace
import com.dentalstudio.notes.desktop.store.toDomain
import com.dentalstudio.notes.domain.NoteTemplate
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopRepositoryTest {

    private val dir: Path = createTempDirectory("dental-notes-test")
    private val workspaceFile = dir.resolve("workspace.json")
    private val settingsFile = dir.resolve("settings.json")

    private fun repository() = DesktopRepository(
        workspaceStore = JsonStore(workspaceFile, Workspace.serializer()) { Workspace() },
        settingsStore = JsonStore(settingsFile, DesktopSettings.serializer()) { DesktopSettings() },
    )

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private val template = NoteTemplate.byId("restorative")

    private val transcript =
        "Patient complains of sensitivity upper left. " +
            "Deep occlusal cavity noted. " +
            "Composite restoration placed and polished. " +
            "Patient tolerated the procedure well. " +
            "Review in six months."

    /** The edit a clinician makes every time: their word, minus the filler. */
    private fun clinicianEdit(draft: String) = draft
        .replace("cavity", "caries")
        .lines()
        .filterNot { it.contains("tolerated", ignoreCase = true) }
        .joinToString("\n")

    @Test
    fun notesSurviveARestart() = runTest {
        val first = repository()
        val generated = first.generate(transcript, template, "J.S.")
        val id = first.createNote("J.S.", template.id, transcript, generated, 90)

        val reopened = repository()
        val note = reopened.note(id)
        assertNotNull(note)
        assertEquals("J.S.", note.patientLabel)
        assertEquals(transcript, note.transcript)
        assertTrue(note.draftNote.isNotBlank())
    }

    @Test
    fun anEditIsLearnedAndPersisted() = runTest {
        val repo = repository()
        val generated = repo.generate(transcript, template, "J.S.")
        val id = repo.createNote("J.S.", template.id, transcript, generated, 90)

        val outcome = repo.saveEdit(id, clinicianEdit(generated.note))
        assertNotNull(outcome)
        assertTrue(outcome.learnedSomething)

        val reopened = repository()
        assertTrue(reopened.rules.any { it.pattern == "cavity" && it.replacement == "caries" })
        assertEquals(1, reopened.workspace.value.editCount)
    }

    @Test
    fun theFourthDraftArrivesAlreadyCorrected() = runTest {
        val repo = repository()

        repeat(3) {
            val generated = repo.generate(transcript, template, "Patient $it")
            val id = repo.createNote("Patient $it", template.id, transcript, generated, 60)
            repo.saveEdit(id, clinicianEdit(generated.note))
        }

        val fourth = repo.generate(transcript, template, "Patient 4")
        assertFalse(fourth.note.contains("cavity", ignoreCase = true), "the rejected word came back: ${fourth.note}")
        assertTrue(fourth.note.contains("caries", ignoreCase = true), "the learned word was not applied: ${fourth.note}")
        assertFalse(fourth.note.contains("tolerated", ignoreCase = true), "the deleted filler came back: ${fourth.note}")
        assertTrue(fourth.appliedRules.isNotEmpty())
    }

    @Test
    fun learnedPreferencesCanBeSwitchedOffAndForgotten() = runTest {
        val repo = repository()
        repeat(3) {
            val generated = repo.generate(transcript, template, "P$it")
            val id = repo.createNote("P$it", template.id, transcript, generated, 60)
            repo.saveEdit(id, clinicianEdit(generated.note))
        }
        val rule = repo.rules.first { it.pattern == "cavity" }

        repo.setRuleEnabled(rule.id, false)
        val withRuleOff = repo.generate(transcript, template, "P4")
        assertTrue(withRuleOff.note.contains("cavity", ignoreCase = true), "a disabled preference should not be applied")

        repo.deleteRule(rule.id)
        assertTrue(repo.rules.none { it.id == rule.id })
    }

    @Test
    fun autoApplyCanBeTurnedOff() = runTest {
        val repo = repository()
        repeat(3) {
            val generated = repo.generate(transcript, template, "P$it")
            val id = repo.createNote("P$it", template.id, transcript, generated, 60)
            repo.saveEdit(id, clinicianEdit(generated.note))
        }
        repo.updateSettings { it.copy(autoApplyLearning = false) }
        val next = repo.generate(transcript, template, "P4")
        assertTrue(next.appliedRules.isEmpty())
        assertTrue(next.note.contains("cavity", ignoreCase = true))
    }

    @Test
    fun aNoOpSaveTeachesNothing() = runTest {
        val repo = repository()
        val generated = repo.generate(transcript, template, "J.S.")
        val id = repo.createNote("J.S.", template.id, transcript, generated, 60)
        assertNull(repo.saveEdit(id, generated.note))
        assertTrue(repo.rules.isEmpty())
    }

    @Test
    fun resetLearningKeepsTheNotes() = runTest {
        val repo = repository()
        val generated = repo.generate(transcript, template, "J.S.")
        val id = repo.createNote("J.S.", template.id, transcript, generated, 60)
        repo.saveEdit(id, clinicianEdit(generated.note))

        repo.resetLearning()
        assertTrue(repo.rules.isEmpty())
        assertEquals(0, repo.workspace.value.editCount)
        assertNotNull(repo.note(id))

        repo.wipeEverything()
        assertNull(repo.note(id))
    }

    @Test
    fun settingsPersistAndTheApiKeyIsNotStoredInTheWorkspace() = runTest {
        val repo = repository()
        val generated = repo.generate(transcript, template, "J.S.")
        repo.createNote("J.S.", template.id, transcript, generated, 60)
        repo.updateSettings { it.copy(apiKey = "sk-ant-test", clinicianName = "Dr Example") }

        val reopened = repository()
        assertEquals("sk-ant-test", reopened.settings.value.apiKey)
        assertEquals("Dr Example", reopened.settings.value.clinicianName)
        assertFalse(Files.readString(workspaceFile).contains("sk-ant-test"))
    }

    @Test
    fun aCorruptWorkspaceIsQuarantinedRatherThanOverwritten() = runTest {
        Files.writeString(workspaceFile, "{ this is not json")
        val repo = repository()
        assertTrue(repo.workspace.value.notes.isEmpty())
        assertTrue(Files.exists(dir.resolve("workspace.json.corrupt")), "the unreadable file should be kept")
    }

    @Test
    fun savingLeavesNoTemporaryFileBehind() = runTest {
        val repo = repository()
        val generated = repo.generate(transcript, template, "J.S.")
        repo.createNote("J.S.", template.id, transcript, generated, 60)
        assertFalse(Files.exists(dir.resolve("workspace.json.tmp")))
        assertTrue(Files.exists(workspaceFile))
    }

    @Test
    fun preferencesAreScopedToTheirTemplate() = runTest {
        val repo = repository()
        repeat(3) {
            val generated = repo.generate(transcript, template, "P$it")
            val id = repo.createNote("P$it", template.id, transcript, generated, 60)
            repo.saveEdit(id, clinicianEdit(generated.note))
        }
        // The filler removal was learned for restorations only.
        val removal = repo.rules.first { it.pattern.contains("tolerated") }
        assertEquals("restorative", removal.scope)

        // Wording preferences are not template-specific and do carry across.
        val wording = repo.rules.first { it.pattern == "cavity" }
        assertEquals("*", wording.scope)
    }

    @Test
    fun styleContextCarriesApprovedNotesAsExamples() = runTest {
        val repo = repository()
        val generated = repo.generate(transcript, template, "J.S.")
        val id = repo.createNote("J.S.", template.id, transcript, generated, 60)
        repo.saveEdit(id, clinicianEdit(generated.note))

        val context = repo.styleContext(template.id)
        assertEquals(1, context.exemplars.size)
        assertFalse(context.exemplars.first().finalNote.contains("cavity", ignoreCase = true))
        assertTrue(context.profile.samples >= 1)
    }

    @Test
    fun rulesLoadedFromDiskKeepTheirEvidence() = runTest {
        val repo = repository()
        repeat(2) {
            val generated = repo.generate(transcript, template, "P$it")
            val id = repo.createNote("P$it", template.id, transcript, generated, 60)
            repo.saveEdit(id, clinicianEdit(generated.note))
        }
        val stored = repository().workspace.value.rules.first { it.pattern == "cavity" }.toDomain()
        assertEquals(2, stored.occurrences)
        assertTrue(stored.isActive)
        assertFalse(stored.isAutoApplied, "two sightings should steer the prompt but not rewrite output yet")
    }
}
