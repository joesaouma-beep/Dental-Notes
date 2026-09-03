import com.dentalstudio.notes.learning.*
import com.dentalstudio.notes.domain.NoteTemplate
import kotlin.test.*

class NoteStructureTest {
    @Test fun parsesMarkdownAndBoldHeadings() {
        val note = """
            ## PRESENTING COMPLAINT
            Sensitivity upper left on cold.

            **CLINICAL FINDINGS**
            Deep occlusal caries 26.

            TREATMENT PROVIDED
            Composite restoration placed.
        """.trimIndent()
        val s = NoteStructure.parse(note)
        assertEquals(listOf("PRESENTING COMPLAINT", "CLINICAL FINDINGS", "TREATMENT PROVIDED"), s.map { it.heading })
        assertEquals("Deep occlusal caries 26.", s[1].body)
    }

    @Test fun doesNotTreatSentencesAsHeadings() {
        val s = NoteStructure.parse("Patient reports pain on biting for three days now and it is worse.")
        assertEquals(1, s.size)
        assertEquals("NOTE", s[0].heading)
    }
}

class TextDiffTest {
    @Test fun findsSubstitution() {
        val b = TextDiff.blocks("Deep cavity noted on the tooth", "Deep caries noted on the tooth")
        assertEquals(1, b.size)
        assertEquals("cavity", b[0].removedText)
        assertEquals("caries", b[0].insertedText)
    }

    @Test fun findsDeletionAndInsertion() {
        val b = TextDiff.blocks("Local anaesthetic given. Patient tolerated the procedure well.", "Local anaesthetic given.")
        assertTrue(b.any { it.isDeletion && it.removedText.contains("tolerated") })
    }

    @Test fun similarityIsOneForIdenticalText() {
        assertEquals(1.0, TextDiff.similarity("same text here", "same text here"))
    }
}

class RuleMinerTest {

    private fun draft(body: String) = "## TREATMENT PROVIDED\n$body"

    @Test fun learnsTerminologySubstitution() {
        val mined = RuleMiner.mine(
            draft("Restored the cavity on the upper left molar."),
            draft("Restored the caries on the upper left molar."),
            "restorative",
        )
        val term = mined.rules.single { it.type == RuleType.TERM }
        assertEquals("cavity", term.pattern)
        assertEquals("caries", term.replacement)
    }

    @Test fun ignoresPatientSpecificNumbers() {
        val mined = RuleMiner.mine(
            draft("Probing depth of four millimetres distal."),
            draft("Probing depth of six millimetres distal."),
            "hygiene",
        )
        assertTrue(mined.rules.none { it.type == RuleType.TERM }, "numbers-as-words are still clinical facts: ${mined.rules}")
    }

    @Test fun ignoresToothNumbers() {
        val mined = RuleMiner.mine(draft("Caries on 26 restored."), draft("Caries on 27 restored."), "restorative")
        assertTrue(mined.rules.none { it.type == RuleType.TERM })
    }

    @Test fun learnsDeletedFiller() {
        val mined = RuleMiner.mine(
            draft("Composite placed. Patient tolerated the procedure well."),
            draft("Composite placed."),
            "restorative",
        )
        val rule = mined.rules.single { it.type == RuleType.REMOVE }
        assertEquals("patient tolerated the procedure well", rule.pattern)
    }

    @Test fun learnsAddedBoilerplate() {
        val mined = RuleMiner.mine(
            draft("Extraction completed."),
            draft("Extraction completed. Post-operative instructions given verbally and in writing."),
            "extraction",
        )
        val rule = mined.rules.single { it.type == RuleType.ADD }
        assertTrue(rule.pattern.startsWith("post-operative instructions given"))
    }

    @Test fun learnsSectionDrop() {
        val d = "## CLINICAL FINDINGS\nCaries noted.\n\n## RADIOGRAPHS\nNil taken this visit.\n\n## NEXT VISIT\nReview."
        val f = "## CLINICAL FINDINGS\nCaries noted.\n\n## NEXT VISIT\nReview."
        val mined = RuleMiner.mine(d, f, "exam")
        val rule = mined.rules.single { it.type == RuleType.SECTION_DROP }
        assertEquals("RADIOGRAPHS", rule.pattern)
        assertEquals("exam", rule.scope)
    }

    @Test fun countsContradictionWhenClinicianKeepsThePhrase() {
        val rule = LearnedRule(type = RuleType.TERM, pattern = "cavity", replacement = "caries", occurrences = 2)
        val kept = RuleMiner.contradictions(listOf(rule), draft("The cavity was restored."), draft("The cavity was restored today."))
        assertEquals(listOf("cavity"), kept)
        val corrected = RuleMiner.contradictions(listOf(rule), draft("The cavity was restored."), draft("The caries was restored."))
        assertTrue(corrected.isEmpty())
    }

    @Test fun observationCapturesTrimAndLayout() {
        val d = "## NOTE\nA fairly long sentence about the treatment that was carried out today for this patient."
        val f = "## NOTE\n- Treatment carried out."
        val o = RuleMiner.mine(d, f, "freeform").observation
        assertTrue(o.finalWords < o.draftWords)
        assertEquals(1.0, o.bulletRatio)
    }
}

class RuleApplierTest {

    private fun rule(type: RuleType, pattern: String, replacement: String = "", occ: Int = 4) =
        LearnedRule(type = type, pattern = pattern, replacement = replacement, occurrences = occ)

    @Test fun substitutesWholeWordsOnlyAndKeepsCase() {
        val r = RuleApplier.apply("Cavity found. The cavity is deep. Cavities elsewhere.", listOf(rule(RuleType.TERM, "cavity", "caries")), "exam")
        assertEquals("Caries found. The caries is deep. Cavities elsewhere.", r.text)
        assertEquals(1, r.applied.size)
    }

    @Test fun doesNotApplyLowConfidenceRules() {
        val weak = LearnedRule(type = RuleType.TERM, pattern = "cavity", replacement = "caries", occurrences = 1)
        val r = RuleApplier.apply("Cavity found.", listOf(weak), "exam")
        assertEquals("Cavity found.", r.text)
        assertTrue(r.applied.isEmpty())
    }

    @Test fun removesLearnedSentenceButKeepsTheRest() {
        val text = "Composite placed. Patient tolerated the procedure well. Occlusion checked."
        val r = RuleApplier.apply(text, listOf(rule(RuleType.REMOVE, "patient tolerated the procedure well")), "restorative")
        assertEquals("Composite placed. Occlusion checked.", r.text)
    }

    @Test fun removesBulletLineEntirely() {
        val text = "## TREATMENT\n- Composite placed.\n- Patient tolerated the procedure well.\n- Occlusion checked."
        val r = RuleApplier.apply(text, listOf(rule(RuleType.REMOVE, "patient tolerated the procedure well")), "restorative")
        assertEquals("## TREATMENT\n- Composite placed.\n- Occlusion checked.", r.text)
    }

    @Test fun dropsLearnedSection() {
        val text = "## FINDINGS\nCaries 26.\n\n## RADIOGRAPHS\nNil.\n\n## NEXT VISIT\nReview."
        val r = RuleApplier.apply(text, listOf(rule(RuleType.SECTION_DROP, "RADIOGRAPHS")), "exam")
        assertFalse(r.text.contains("RADIOGRAPHS"))
        assertTrue(r.text.contains("NEXT VISIT"))
    }

    @Test fun scopedRuleDoesNotLeakToOtherTemplates() {
        val scoped = LearnedRule(type = RuleType.REMOVE, scope = "endo", pattern = "rubber dam placed", occurrences = 5)
        val r = RuleApplier.apply("Rubber dam placed. Access cut.", listOf(scoped), "restorative")
        assertEquals("Rubber dam placed. Access cut.", r.text)
    }
}

class StyleGuideTest {
    @Test fun emptyWhenNothingLearned() {
        assertEquals("", StyleGuide.build(StyleContext(), NoteTemplate.DEFAULT))
    }

    @Test fun describesRulesAndLength() {
        val ctx = StyleContext(
            rules = listOf(
                LearnedRule(type = RuleType.TERM, pattern = "cavity", replacement = "caries", occurrences = 3),
                LearnedRule(type = RuleType.REMOVE, pattern = "patient tolerated the procedure well", occurrences = 4),
                LearnedRule(type = RuleType.SECTION_DROP, scope = "exam", pattern = "RADIOGRAPHS", occurrences = 3),
            ),
            profile = StyleProfile(samples = 6, avgDraftWords = 160.0, avgFinalWords = 100.0, avgBulletRatio = 0.8),
        )
        val guide = StyleGuide.build(ctx, NoteTemplate.byId("exam"))
        assertTrue(guide.contains("write \"caries\", never \"cavity\""))
        assertTrue(guide.contains("patient tolerated the procedure well"))
        assertTrue(guide.contains("RADIOGRAPHS"))
        assertTrue(guide.contains("about 100 words"))
        assertTrue(guide.contains("bullet"))
    }

    @Test fun profileTracksTrimOverTime() {
        var p = StyleProfile()
        repeat(3) {
            p = p.merge(StyleObservation("exam", 200, 120, 0.9, 8.0, listOf("A", "B"), 0.7), 0L)
        }
        assertEquals(3, p.samples)
        assertEquals(120, p.targetWords)
        assertTrue(p.trimRatio > 0.35)
        assertTrue(p.prefersBullets)
    }
}
