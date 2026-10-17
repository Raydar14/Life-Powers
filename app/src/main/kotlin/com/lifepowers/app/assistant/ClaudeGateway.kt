package com.lifepowers.app.assistant

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** What came back from one call, before it is turned into an AssistantResult. */
sealed interface CallOutcome {
    data class Text(val text: String) : CallOutcome
    /** stop_reason "refusal": the model or its safeguards declined, even after the server side fallback. */
    data object Refused : CallOutcome
    /** A plain sentence for the person. Never a stack trace and never the key. */
    data class Failed(val message: String) : CallOutcome
}

/**
 * The only place that talks to the Claude API, through the official Java SDK. Non-streaming
 * messages().create on Dispatchers.IO, one client cached per key.
 */
@Singleton
class ClaudeGateway @Inject constructor() {

    private var cachedKey: String? = null
    private var cachedClient: AnthropicClient? = null

    @Synchronized
    private fun client(apiKey: String): AnthropicClient {
        val existing = cachedClient
        if (existing != null && cachedKey == apiKey) return existing
        val created = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(120))
            .build()
        val old = existing
        cachedClient = created
        cachedKey = apiKey
        if (old != null) runCatching { old.close() }
        return created
    }

    /** Drops the cached client, for when the key is removed or replaced. */
    @Synchronized
    fun reset() {
        val old = cachedClient
        cachedClient = null
        cachedKey = null
        if (old != null) runCatching { old.close() }
    }

    /**
     * Sends one user turn. [schema] switches on structured outputs: the reply text is then JSON that
     * matches it. [jpeg] adds one base64 JPEG image block before the text.
     */
    suspend fun send(
        apiKey: String,
        system: String,
        userText: String,
        jpeg: ByteArray? = null,
        schema: Map<String, Any>? = null,
    ): CallOutcome = withContext(Dispatchers.IO) {
        try {
            val message = client(apiKey).messages().create(params(system, userText, jpeg, schema))
            read(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnauthorizedException) {
            CallOutcome.Failed(KEY_REJECTED)
        } catch (e: RateLimitException) {
            CallOutcome.Failed(RATE_LIMITED)
        } catch (e: AnthropicServiceException) {
            CallOutcome.Failed(UNREACHABLE)
        } catch (e: AnthropicIoException) {
            CallOutcome.Failed(UNREACHABLE)
        } catch (e: AnthropicException) {
            CallOutcome.Failed(UNREACHABLE)
        } catch (e: Exception) {
            CallOutcome.Failed(UNREACHABLE)
        }
    }

    private fun params(system: String, userText: String, jpeg: ByteArray?, schema: Map<String, Any>?): MessageCreateParams {
        val blocks = mutableListOf<ContentBlockParam>()
        if (jpeg != null) {
            val image = ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                        .data(java.util.Base64.getEncoder().encodeToString(jpeg))
                        .build(),
                )
                .build()
            blocks += ContentBlockParam.ofImage(image)
        }
        blocks += ContentBlockParam.ofText(TextBlockParam.builder().text(userText).build())

        val output = OutputConfig.builder().effort(OutputConfig.Effort.LOW)
        if (schema != null) {
            val schemaBuilder = JsonOutputFormat.Schema.builder()
            for ((name, value) in schema) schemaBuilder.putAdditionalProperty(name, JsonValue.from(value))
            output.format(JsonOutputFormat.builder().schema(schemaBuilder.build()).build())
        }

        return MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .system(system)
            .addUserMessageOfBlockParams(blocks)
            .outputConfig(output.build())
            // Server side refusal fallback: a declined request is re-run on Anthropic's recommended model.
            .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
    }

    /** Checks stop_reason before reading any content. */
    private fun read(message: Message): CallOutcome {
        val stop = message.stopReason().orElse(null)
        if (stop == StopReason.REFUSAL) return CallOutcome.Refused
        val text = message.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .trim()
        if (stop == StopReason.MAX_TOKENS) return CallOutcome.Failed(CUT_OFF)
        if (text.isEmpty()) return CallOutcome.Failed(EMPTY)
        return CallOutcome.Text(text)
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
        const val MAX_TOKENS = 4000L
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        const val KEY_REJECTED = "Your key was not accepted. Check it in Settings."
        const val RATE_LIMITED = "Too many requests right now. Try again in a minute."
        const val UNREACHABLE = "Could not reach the assistant. Check your connection."
        const val REFUSED = "The assistant declined to answer this one."
        const val CUT_OFF = "The answer was cut off before it finished. Try again."
        const val EMPTY = "The assistant sent back nothing to show. Try again."
        const val UNREADABLE = "The assistant's answer could not be read. Try again."
    }
}
