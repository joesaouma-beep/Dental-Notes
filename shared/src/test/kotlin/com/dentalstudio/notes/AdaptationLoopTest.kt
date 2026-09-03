import com.dentalstudio.notes.generate.GenerationRequest
import com.dentalstudio.notes.generate.OnDeviceNoteGenerator
import com.dentalstudio.notes.generate.PromptBuilder
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.learning.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * A stand-in for the Room-backed repository: it holds rules and profiles in
 * memory and merges them exactly the way NoteRepository.absorb does, so the
 * whole learn-then-apply cycle can be exercised without Android.
 */
/**
 * A stand-in for the platform stores: it holds rules in memory but delegates
 * every merge decision to the real LearningMerge, so this exercises the same
 * logic that Room and the desktop JSON store run.
 */
private class LearningStore {
    val rules = mutableListOf<LearnedRule>()
    var global = StyleProfile()
    private var nextId = 1L

    fun absorb(draft: String, final: String, templateId: String) {
        val mined = RuleMiner.mine(draft, final, templateId, rules.toList())
        val plan = LearningMerge.plan(mined, rules.toList())

        plan.newRules.forEach { candidate ->
            rules += LearnedRule(
                id = nextId++,
                type = candidate.type,
                scope = candidate.scope,
                pattern = candidate.pattern,
                replacement = candidate.replacement,
                exampleBefore = candidate.exampleBefore,
                exampleAfter = candidate.exampleAfter,
            )
        }
        rules.replaceAll { rule ->
            when (rule.id) {
                in plan.reinforcedIds -> rule.copy(occurrences = rule.occurrences + 1)
                in plan.contradictedIds -> rule.copy(contradictions = rule.contradictions + 1)
                else -> rule
            }
        }
        rules.replaceAll { rule -> if (rule.id in plan.retiredIds) rule.copy(enabled = false) else rule }
        global = global.merge(mined.observation, 0L)
    }

    fun context() = StyleContext(rules = rules.toList(), profile = global, templateProfile = global)
}

class AdaptationLoopTest {

    private val draftFromModel = """
        ## CLINICAL FINDINGS
        Deep occlusal cavity present.

        ## TREATMENT PROVIDED
        Composite restoration placed. Patient tolerated the procedure well.

        ## RADIOGRAPHS
        Nil taken this visit.
    """.trimIndent()

    private val clinicianVersion = """
        ## CLINICAL FINDINGS
        Deep occlusal caries present.

        ## TREATMENT PROVIDED
        Composite restoration placed.
    """.trimIndent()

    @Test
    fun repeatedCorrectionsBecomeAutomatic() {
        val store = LearningStore()

        // The same three corrections across three visits: a word, a stock
        // sentence, and a heading that is never wanted.
        repeat(3) { store.absorb(draftFromModel, clinicianVersion, "restorative") }

        val term = store.rules.single { it.type == RuleType.TERM }
        assertEquals("cavity", term.pattern)
        assertEquals("caries", term.replacement)
        assertEquals(3, term.occurrences)
        assertTrue(term.isAutoApplied, "three identical corrections should be applied without asking")

        assertTrue(store.rules.any { it.type == RuleType.REMOVE && it.pattern.contains("tolerated") })
        assertTrue(store.rules.any { it.type == RuleType.SECTION_DROP && it.pattern == "RADIOGRAPHS" })

        // The fourth draft is corrected before the clinician ever sees it.
        val result = RuleApplier.apply(draftFromModel, store.rules, "restorative")
        assertTrue(result.text.contains("caries"), "learned wording should be applied")
        assertFalse(result.text.contains("cavity"), "the rejected wording should be gone")
        assertFalse(result.text.contains("tolerated"), "the deleted sentence should not come back")
        assertFalse(result.text.contains("RADIOGRAPHS"), "the dropped heading should not come back")
        assertEquals(3, result.applied.size)
    }

    @Test
    fun fourthDraftNeedsNoEdit() {
        val store = LearningStore()
        repeat(3) { store.absorb(draftFromModel, clinicianVersion, "restorative") }
        val applied = RuleApplier.apply(draftFromModel, store.rules, "restorative").text
        assertEquals(
            TextTokens.normalize(clinicianVersion),
            TextTokens.normalize(applied),
            "after three consistent edits the draft should already match what the clinician writes",
        )
    }

    @Test
    fun aOneOffEditFadesWhenContradicted() {
        val store = LearningStore()
        store.absorb(draftFromModel, clinicianVersion, "restorative")
        store.absorb(draftFromModel, clinicianVersion, "restorative")
        assertTrue(store.rules.first { it.type == RuleType.TERM }.isActive)

        // Now the clinician keeps "cavity" three times running.
        val keptAsIs = draftFromModel.replace("Patient tolerated the procedure well.", "")
        repeat(3) { store.absorb(draftFromModel, keptAsIs, "restorative") }

        val term = store.rules.first { it.type == RuleType.TERM }
        assertEquals(3, term.contradictions)
        assertTrue(term.confidence < 0.55, "a contradicted preference should lose confidence: ${term.confidence}")
        assertFalse(term.isActive, "it should stop steering new notes")
    }

    @Test
    fun learnedStyleReachesThePrompt() {
        val store = LearningStore()
        repeat(3) { store.absorb(draftFromModel, clinicianVersion, "restorative") }

        val prompt = PromptBuilder.system(
            GenerationRequest(
                transcript = "irrelevant",
                template = NoteTemplate.byId("restorative"),
                patientLabel = "J.S.",
                style = store.context(),
            )
        )
        assertTrue(prompt.contains("write \"caries\", never \"cavity\""))
        assertTrue(prompt.contains("patient tolerated the procedure well"))
        assertTrue(prompt.contains("RADIOGRAPHS"))
        assertTrue(prompt.contains("Never invent findings"), "safety rules must survive personalisation")
    }

    @Test
    fun clinicalFactsAreNeverGeneralised() {
        val store = LearningStore()
        // Same shape of edit each visit, but the numbers differ per patient.
        store.absorb("## TREATMENT PROVIDED\nRestored 26 with composite.", "## TREATMENT PROVIDED\nRestored 27 with composite.", "restorative")
        store.absorb("## TREATMENT PROVIDED\nProbing depth four millimetres.", "## TREATMENT PROVIDED\nProbing depth six millimetres.", "hygiene")
        store.absorb("## TREATMENT PROVIDED\nShade A2 selected.", "## TREATMENT PROVIDED\nShade A3 selected.", "restorative")

        assertTrue(
            store.rules.none { it.type == RuleType.TERM },
            "tooth numbers, depths and shades are patient facts, not style: ${store.rules}",
        )
    }
}

class OnDeviceGeneratorTest {

    @Test
    fun routesSentencesToTheRightHeadings() = runTest {
        val result = OnDeviceNoteGenerator().generate(
            GenerationRequest(
                transcript = "Patient complains of pain in the upper left. " +
                    "Deep caries noted on the occlusal surface. " +
                    "Lignocaine infiltration given. " +
                    "Composite restoration placed and polished. " +
                    "Review in six months.",
                template = NoteTemplate.byId("restorative"),
                patientLabel = "J.S.",
                style = StyleContext(),
            )
        )
        val sections = NoteStructure.parse(result.note).associate { it.heading to it.body }
        assertTrue(sections.getValue("PRESENTING COMPLAINT").contains("complains"))
        assertTrue(sections.getValue("CLINICAL FINDINGS").contains("caries"))
        assertTrue(sections.getValue("ANAESTHETIC").contains("Lignocaine"))
        assertTrue(sections.getValue("TREATMENT PROVIDED").contains("Composite"))
        assertTrue(sections.getValue("NEXT VISIT").contains("Review"))
    }

    @Test
    fun neverLosesTheDictation() = runTest {
        val result = OnDeviceNoteGenerator().generate(
            GenerationRequest("Mmm.", NoteTemplate.DEFAULT, "", StyleContext())
        )
        assertTrue(result.note.contains("Mmm"))
    }
}
