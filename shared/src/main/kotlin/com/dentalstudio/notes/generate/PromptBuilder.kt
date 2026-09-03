package com.dentalstudio.notes.generate

import com.dentalstudio.notes.learning.StyleGuide

/**
 * Builds the instructions sent with each dictation.
 *
 * The base prompt states the house rules that never bend — no invented findings,
 * explicit gaps, the fixed heading format. Everything the app has learned is
 * appended after it and is allowed to override tone, length, wording and which
 * headings appear, but never the safety rules.
 */
object PromptBuilder {

    fun system(request: GenerationRequest): String {
        val t = request.template
        val sb = StringBuilder()

        sb.appendLine(
            "You are a dental clinical documentation assistant. You convert a dictated " +
                "chairside recording into a clean treatment note for the patient record."
        )
        if (request.clinicianName.isNotBlank()) {
            sb.appendLine("The dictating clinician is ${request.clinicianName}.")
        }
        sb.appendLine()
        sb.appendLine("RULES — these are absolute:")
        sb.appendLine("1. Use only what is in the dictation. Never invent findings, teeth, materials, doses or consent.")
        sb.appendLine("2. If something a heading expects was not dictated, omit the heading rather than padding it.")
        sb.appendLine("3. Keep every tooth number, material, shade, dose and measurement exactly as dictated.")
        sb.appendLine("4. Correct speech-recognition errors in dental terms only when the intent is unambiguous.")
        sb.appendLine("5. Write in the past tense, third person, no filler, no praise of the clinician.")
        sb.appendLine("6. Output the note only. No preamble, no commentary, no closing summary.")
        sb.appendLine()
        sb.appendLine("FORMAT — each section is a heading line then its content:")
        sb.appendLine("## HEADING IN CAPITALS")
        sb.appendLine("content")
        sb.appendLine()
        sb.appendLine("Template: ${t.name}. Headings available, in this order:")
        sb.appendLine(t.sections.joinToString("\n") { "  ## $it" })
        sb.appendLine("Clinical focus for this template: ${t.focus}")

        val guide = StyleGuide.build(request.style, t)
        if (guide.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(guide)
        }
        val examples = StyleGuide.exemplarBlock(request.style)
        if (examples.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(examples)
        }
        return sb.toString().trim()
    }

    fun user(request: GenerationRequest): String = buildString {
        if (request.patientLabel.isNotBlank()) appendLine("Patient reference: ${request.patientLabel}")
        appendLine("Dictation:")
        append(request.transcript.trim())
    }
}
