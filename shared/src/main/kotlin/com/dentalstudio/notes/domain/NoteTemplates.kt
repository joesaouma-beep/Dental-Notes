package com.dentalstudio.notes.domain

/** Tooth numbering systems a practice might use. */
enum class ToothNotation(val label: String, val example: String) {
    FDI("FDI / ISO", "e.g. 26, 46, 11"),
    PALMER("Palmer", "e.g. UR6, LL6"),
    UNIVERSAL("Universal", "e.g. #14, #30"),
}

/** A dictation template: the shape of note the clinician expects back. */
data class NoteTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    val blurb: String,
    val sections: List<String>,
    val focus: String,
) {
    companion object {
        val ALL: List<NoteTemplate> = listOf(
            NoteTemplate(
                id = "exam",
                name = "Exam & Check-up",
                emoji = "🦷",
                blurb = "Comprehensive oral examination",
                sections = listOf(
                    "PRESENTING COMPLAINT", "MEDICAL HISTORY", "EXTRA-ORAL EXAM",
                    "INTRA-ORAL EXAM", "PERIODONTAL", "RADIOGRAPHS", "DIAGNOSIS",
                    "TREATMENT PLAN", "NEXT VISIT",
                ),
                focus = "Charting, caries risk, soft tissue and oral cancer screen, perio screening.",
            ),
            NoteTemplate(
                id = "hygiene",
                name = "Scale & Clean",
                emoji = "🪥",
                blurb = "Hygiene and periodontal therapy",
                sections = listOf(
                    "PRESENTING COMPLAINT", "PERIODONTAL FINDINGS", "TREATMENT PROVIDED",
                    "ORAL HYGIENE INSTRUCTION", "NEXT VISIT",
                ),
                focus = "Calculus distribution, bleeding on probing, pocket depths, instrumentation used.",
            ),
            NoteTemplate(
                id = "restorative",
                name = "Restoration",
                emoji = "🔧",
                blurb = "Fillings and direct restorations",
                sections = listOf(
                    "PRESENTING COMPLAINT", "CLINICAL FINDINGS", "ANAESTHETIC",
                    "TREATMENT PROVIDED", "MATERIALS", "POST-OPERATIVE INSTRUCTIONS", "NEXT VISIT",
                ),
                focus = "Tooth and surfaces, caries depth, isolation, liner or base, material and shade, occlusion checked.",
            ),
            NoteTemplate(
                id = "endo",
                name = "Root Canal",
                emoji = "🔬",
                blurb = "Endodontic therapy",
                sections = listOf(
                    "PRESENTING COMPLAINT", "CLINICAL FINDINGS", "RADIOGRAPHS", "DIAGNOSIS",
                    "ANAESTHETIC", "TREATMENT PROVIDED", "MATERIALS",
                    "POST-OPERATIVE INSTRUCTIONS", "NEXT VISIT",
                ),
                focus = "Pulp testing, canals located, working lengths, irrigation protocol, obturation, temporary seal.",
            ),
            NoteTemplate(
                id = "extraction",
                name = "Extraction",
                emoji = "🩻",
                blurb = "Exodontia and minor oral surgery",
                sections = listOf(
                    "PRESENTING COMPLAINT", "CLINICAL FINDINGS", "RADIOGRAPHS", "CONSENT",
                    "ANAESTHETIC", "TREATMENT PROVIDED", "POST-OPERATIVE INSTRUCTIONS", "NEXT VISIT",
                ),
                focus = "Consent and risks discussed, technique, socket condition, haemostasis, sutures, written post-op advice.",
            ),
            NoteTemplate(
                id = "crown",
                name = "Crown & Bridge",
                emoji = "👑",
                blurb = "Indirect restorations",
                sections = listOf(
                    "PRESENTING COMPLAINT", "CLINICAL FINDINGS", "ANAESTHETIC", "TREATMENT PROVIDED",
                    "IMPRESSION / SCAN", "TEMPORARY", "LABORATORY", "NEXT VISIT",
                ),
                focus = "Preparation design, margin placement, shade, scan or impression material, temporisation, lab instructions.",
            ),
            NoteTemplate(
                id = "emergency",
                name = "Emergency",
                emoji = "🚨",
                blurb = "Pain and trauma presentations",
                sections = listOf(
                    "PRESENTING COMPLAINT", "HISTORY OF PAIN", "CLINICAL FINDINGS", "RADIOGRAPHS",
                    "DIAGNOSIS", "TREATMENT PROVIDED", "MEDICATIONS", "ADVICE", "NEXT VISIT",
                ),
                focus = "Pain character and duration, special tests, provisional diagnosis, immediate management, review arrangements.",
            ),
            NoteTemplate(
                id = "paediatric",
                name = "Paediatric",
                emoji = "🧒",
                blurb = "Children and adolescents",
                sections = listOf(
                    "PRESENTING COMPLAINT", "BEHAVIOUR", "CLINICAL FINDINGS", "TREATMENT PROVIDED",
                    "PREVENTIVE ADVICE", "GUARDIAN COMMUNICATION", "NEXT VISIT",
                ),
                focus = "Behaviour management, guardian present, eruption stage, preventive advice, fluoride application.",
            ),
            NoteTemplate(
                id = "ortho",
                name = "Ortho Review",
                emoji = "📐",
                blurb = "Orthodontic adjustment visits",
                sections = listOf(
                    "PROGRESS", "CLINICAL FINDINGS", "TREATMENT PROVIDED", "APPLIANCE",
                    "INSTRUCTIONS", "NEXT VISIT",
                ),
                focus = "Wire sequence, elastics, appliance condition, oral hygiene with appliance, tracking of progress.",
            ),
            NoteTemplate(
                id = "implant",
                name = "Implant",
                emoji = "🦾",
                blurb = "Surgical and restorative implant visits",
                sections = listOf(
                    "PRESENTING COMPLAINT", "CLINICAL FINDINGS", "RADIOGRAPHS", "CONSENT",
                    "ANAESTHETIC", "TREATMENT PROVIDED", "COMPONENTS",
                    "POST-OPERATIVE INSTRUCTIONS", "NEXT VISIT",
                ),
                focus = "Site, fixture make and dimensions, torque achieved, grafting, healing abutment, review schedule.",
            ),
            NoteTemplate(
                id = "prosthetics",
                name = "Dentures",
                emoji = "😁",
                blurb = "Removable prosthetics",
                sections = listOf(
                    "PRESENTING COMPLAINT", "CLINICAL FINDINGS", "TREATMENT PROVIDED",
                    "ADJUSTMENTS", "INSTRUCTIONS", "NEXT VISIT",
                ),
                focus = "Stage of construction, fit and retention, occlusion, sore spots relieved, hygiene instructions.",
            ),
            NoteTemplate(
                id = "freeform",
                name = "Free Form",
                emoji = "✍️",
                blurb = "Let the structure follow the dictation",
                sections = listOf("NOTE"),
                focus = "Keep the clinician's own structure and only tidy the language.",
            ),
        )

        val DEFAULT: NoteTemplate = ALL.first()

        fun byId(id: String?): NoteTemplate = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
