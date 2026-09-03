package com.dentalstudio.notes.speech

/**
 * The state of a dictation session, shared by the Android and desktop apps so
 * both drive the same timer, waveform and transcript UI.
 */
enum class DictationStatus { IDLE, LISTENING, PAUSED, ERROR }

data class DictationState(
    val status: DictationStatus = DictationStatus.IDLE,
    /** Everything committed so far. */
    val finalText: String = "",
    /** The phrase currently being recognised, not yet committed. */
    val partialText: String = "",
    /** Microphone level, 0f to 1f, for the waveform. */
    val amplitude: Float = 0f,
    val error: String? = null,
) {
    val fullText: String
        get() = listOf(finalText, partialText).filter { it.isNotBlank() }.joinToString(" ").trim()

    val hasContent: Boolean get() = fullText.isNotBlank()
}
