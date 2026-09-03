package com.dentalstudio.notes.generate

import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.learning.StyleContext

/** What the generator is asked to turn into a note. */
data class GenerationRequest(
    val transcript: String,
    val template: NoteTemplate,
    val patientLabel: String,
    val style: StyleContext,
    val clinicianName: String = "",
)

/** The generated note plus a label describing what produced it. */
data class GenerationResult(
    val note: String,
    val generatorLabel: String,
)

/** A source of clinical notes. Implementations must never throw for empty input. */
interface NoteGenerator {
    suspend fun generate(request: GenerationRequest): GenerationResult
}

/** Raised for problems worth showing the clinician verbatim. */
class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
