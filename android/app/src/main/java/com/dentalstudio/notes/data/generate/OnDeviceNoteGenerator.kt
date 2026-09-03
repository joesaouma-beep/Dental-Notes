package com.dentalstudio.notes.data.generate

import com.dentalstudio.notes.learning.NoteSection
import com.dentalstudio.notes.learning.NoteStructure
import com.dentalstudio.notes.learning.TextTokens

/**
 * Structures a dictation without leaving the device.
 *
 * This is the fallback when no API key is configured or the network is down at
 * the chair. It never rewrites clinical content — it only routes each dictated
 * sentence to the heading it belongs under and tidies capitalisation, so the
 * clinician always leaves the appointment with a usable note.
 */
class OnDeviceNoteGenerator : NoteGenerator {

    override suspend fun generate(request: GenerationRequest): GenerationResult {
        val sentences = TextTokens.sentences(request.transcript)
            .map { it.trim() }
            .filter { TextTokens.wordCount(it) >= 2 }

        if (sentences.isEmpty()) {
            return GenerationResult(
                note = "## NOTE\n${request.transcript.trim().ifBlank { "No dictation captured." }}",
                generatorLabel = LABEL,
            )
        }

        val headings = request.template.sections
        val buckets = linkedMapOf<String, MutableList<String>>()
        headings.forEach { buckets[it] = mutableListOf() }
        val fallback = headings.firstOrNull() ?: "NOTE"

        sentences.forEach { sentence ->
            val heading = bestHeading(sentence, headings) ?: fallback
            buckets.getValue(heading) += polish(sentence)
        }

        val sections = buckets
            .filterValues { it.isNotEmpty() }
            .map { (heading, lines) -> NoteSection(heading, lines.joinToString("\n") { "- $it" }) }

        return GenerationResult(note = NoteStructure.render(sections), generatorLabel = LABEL)
    }

    private fun bestHeading(sentence: String, headings: List<String>): String? {
        val words = TextTokens.words(sentence.lowercase()).toSet()
        var best: String? = null
        var bestScore = 0
        headings.forEach { heading ->
            val score = KEYWORDS[heading].orEmpty().count { it in words }
            if (score > bestScore) { bestScore = score; best = heading }
        }
        return best
    }

    private fun polish(sentence: String): String {
        val trimmed = sentence.trim().trimEnd('.', ',', ' ')
        if (trimmed.isEmpty()) return trimmed
        return trimmed.replaceFirstChar { it.uppercaseChar() } + "."
    }

    private companion object {
        const val LABEL = "On-device"

        /** Words that place a dictated sentence under a heading. */
        val KEYWORDS: Map<String, Set<String>> = mapOf(
            "PRESENTING COMPLAINT" to setOf("complains", "complaining", "presents", "presenting", "reports", "reported", "concerned", "chief", "wants", "asking"),
            "HISTORY OF PAIN" to setOf("pain", "ache", "aching", "throbbing", "sensitive", "sensitivity", "hot", "cold", "sweet", "biting", "spontaneous", "keeping", "awake"),
            "MEDICAL HISTORY" to setOf("medical", "medication", "medications", "allergy", "allergic", "diabetic", "diabetes", "warfarin", "bisphosphonate", "pregnant", "asthma", "history"),
            "BEHAVIOUR" to setOf("behaviour", "behavior", "cooperative", "anxious", "nervous", "settled", "distressed", "tell", "show"),
            "EXTRA-ORAL EXAM" to setOf("extraoral", "extra", "lymph", "tmj", "facial", "swelling", "asymmetry", "palpation", "muscles"),
            "INTRA-ORAL EXAM" to setOf("intraoral", "intra", "mucosa", "tongue", "palate", "soft", "tissues", "occlusion", "wear", "erosion"),
            "CLINICAL FINDINGS" to setOf("caries", "cavity", "fracture", "fractured", "restoration", "decay", "lesion", "found", "noted", "cracked", "mobile", "percussion", "tender", "tests", "vital", "necrotic"),
            "PERIODONTAL" to setOf("pocket", "pockets", "probing", "bleeding", "calculus", "plaque", "gingivitis", "periodontitis", "recession", "furcation", "bpe"),
            "PERIODONTAL FINDINGS" to setOf("pocket", "pockets", "probing", "bleeding", "calculus", "plaque", "gingivitis", "periodontitis", "recession", "furcation", "bpe"),
            "RADIOGRAPHS" to setOf("radiograph", "radiographs", "xray", "x", "ray", "opg", "bitewing", "bitewings", "periapical", "cbct", "imaging", "film"),
            "DIAGNOSIS" to setOf("diagnosis", "diagnosed", "pulpitis", "periodontitis", "abscess", "necrosis", "impression", "consistent", "provisional"),
            "CONSENT" to setOf("consent", "consented", "risks", "explained", "alternatives", "discussed", "warned", "agreed"),
            "ANAESTHETIC" to setOf("anaesthetic", "anesthetic", "lignocaine", "lidocaine", "articaine", "block", "infiltration", "numb", "carpule", "cartridge"),
            "TREATMENT PROVIDED" to setOf("placed", "restored", "prepared", "removed", "extracted", "cleaned", "scaled", "polished", "filled", "obturated", "cemented", "adjusted", "sutured", "irrigated", "isolated", "dam", "bonded", "etched"),
            "MATERIALS" to setOf("composite", "amalgam", "glass", "ionomer", "shade", "resin", "cement", "gutta", "percha", "sealer", "liner", "matrix", "bond"),
            "IMPRESSION / SCAN" to setOf("impression", "scan", "scanned", "silicone", "alginate", "digital", "trios", "itero"),
            "TEMPORARY" to setOf("temporary", "temp", "provisional", "temporised", "protemp"),
            "LABORATORY" to setOf("lab", "laboratory", "technician", "zirconia", "emax", "porcelain", "sent"),
            "COMPONENTS" to setOf("fixture", "abutment", "torque", "ncm", "healing", "cover", "screw", "graft", "membrane"),
            "APPLIANCE" to setOf("bracket", "brackets", "wire", "archwire", "elastics", "ligature", "aligner", "retainer", "band"),
            "ADJUSTMENTS" to setOf("adjusted", "relieved", "sore", "spot", "pressure", "reline", "ease"),
            "MEDICATIONS" to setOf("prescribed", "prescription", "amoxicillin", "antibiotic", "antibiotics", "ibuprofen", "paracetamol", "metronidazole", "analgesia", "script"),
            "POST-OPERATIVE INSTRUCTIONS" to setOf("post", "operative", "postoperative", "instructions", "advised", "avoid", "rinse", "salt", "bleeding", "aftercare", "written"),
            "ORAL HYGIENE INSTRUCTION" to setOf("brushing", "brush", "floss", "flossing", "interdental", "technique", "toothpaste", "fluoride", "hygiene"),
            "PREVENTIVE ADVICE" to setOf("diet", "sugar", "fluoride", "varnish", "fissure", "sealant", "prevention", "preventive"),
            "GUARDIAN COMMUNICATION" to setOf("mother", "father", "parent", "guardian", "carer", "informed", "explained"),
            "ADVICE" to setOf("advised", "advice", "instructed", "warned", "told", "return", "worsens"),
            "INSTRUCTIONS" to setOf("instructed", "instructions", "wear", "clean", "advised", "told"),
            "PROGRESS" to setOf("progress", "progressing", "improved", "tracking", "alignment", "since", "last"),
            "TREATMENT PLAN" to setOf("plan", "planned", "quote", "quoted", "options", "stage", "proposed", "recommend", "recommended"),
            "NEXT VISIT" to setOf("review", "recall", "book", "booked", "return", "next", "weeks", "months", "appointment", "follow"),
        )
    }
}
