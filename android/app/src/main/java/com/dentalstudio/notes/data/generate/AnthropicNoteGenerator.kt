package com.dentalstudio.notes.data.generate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Calls the Claude Messages API to turn a dictation into a structured note. */
class AnthropicNoteGenerator(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String,
    private val client: OkHttpClient = defaultClient(),
) : NoteGenerator {

    override suspend fun generate(request: GenerationRequest): GenerationResult = withContext(Dispatchers.IO) {
        val key = apiKeyProvider().trim()
        if (key.isBlank()) throw GenerationException("No Claude API key set. Add one in Settings.")

        val model = modelProvider()
        val payload = MessagesRequest(
            model = model,
            maxTokens = 1600,
            system = PromptBuilder.system(request),
            messages = listOf(Message("user", PromptBuilder.user(request))),
        )

        val http = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("content-type", "application/json")
            .post(json.encodeToString(MessagesRequest.serializer(), payload).toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            client.newCall(http).execute()
        } catch (e: IOException) {
            throw GenerationException("Could not reach Claude. Check your connection.", e)
        }

        response.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw GenerationException(describe(r.code, body))
            val parsed = runCatching { json.decodeFromString(MessagesResponse.serializer(), body) }
                .getOrElse { throw GenerationException("Claude returned a response the app could not read.", it) }
            val text = parsed.content.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }.trim()
            if (text.isEmpty()) throw GenerationException("Claude returned an empty note. Try dictating again.")
            GenerationResult(note = text, generatorLabel = model)
        }
    }

    private fun describe(code: Int, body: String): String = when (code) {
        401 -> "Claude rejected the API key. Check it in Settings."
        403 -> "This API key is not permitted to use that model."
        429 -> "Rate limited by Claude. Wait a moment and try again."
        in 500..599 -> "Claude is temporarily unavailable. Your dictation has been kept."
        else -> {
            val detail = runCatching {
                json.decodeFromString(ErrorResponse.serializer(), body).error?.message
            }.getOrNull()
            detail ?: "Claude returned an error ($code)."
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<Message>,
    val temperature: Double = 0.2,
)

@Serializable
private data class Message(val role: String, val content: String)

@Serializable
private data class MessagesResponse(val content: List<ContentBlock> = emptyList())

@Serializable
private data class ContentBlock(val type: String = "text", val text: String? = null)

@Serializable
private data class ErrorResponse(val error: ErrorDetail? = null)

@Serializable
private data class ErrorDetail(val type: String = "", val message: String = "")
