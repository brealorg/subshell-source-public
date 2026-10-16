package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.MESSAGE_REPLY_TEXT_MAX_LENGTH
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class RedditMessageReplyResponseDto(
    val json: RedditMessageReplyJsonDto = RedditMessageReplyJsonDto(),
)

@Serializable
internal data class RedditMessageReplyJsonDto(
    val errors: List<JsonElement> = emptyList(),
)

internal sealed interface RedditMessageReplyNetworkResult {
    data class Accepted(
        val metadata: NetworkResponseMetadata,
    ) : RedditMessageReplyNetworkResult

    data class ApiFailure(
        val providerCode: String?,
        val metadata: NetworkResponseMetadata,
    ) : RedditMessageReplyNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
    ) : RedditMessageReplyNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditMessageReplyNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditMessageReplyNetworkResult
}

internal class RedditMessageReplyNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun reply(
        parentFullname: String,
        text: String,
        richtextJson: String? = null,
    ): RedditMessageReplyNetworkResult {
        val request = redditPrivateMessageReplyFormRequest(
            parentFullname = parentFullname,
            text = text,
            richtextJson = richtextJson,
        )
        return when (
            val result = httpClient.postFormJson(
                request = request,
                deserializer = RedditMessageReplyResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> if (result.value.json.errors.isEmpty()) {
                RedditMessageReplyNetworkResult.Accepted(result.metadata)
            } else {
                RedditMessageReplyNetworkResult.ApiFailure(
                    providerCode = result.value.json.errors.firstNotNullOfOrNull(
                        ::redditMessageReplyProviderCode,
                    ),
                    metadata = result.metadata,
                )
            }

            is NetworkResult.HttpFailure -> RedditMessageReplyNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
            )

            is NetworkResult.TransportFailure -> RedditMessageReplyNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

            is NetworkResult.ProtocolFailure -> RedditMessageReplyNetworkResult.ProtocolFailure(
                kind = result.kind,
                jsonPath = result.jsonPath,
            )
        }
    }
}

internal fun redditPrivateMessageReplyFormRequest(
    parentFullname: String,
    text: String,
    richtextJson: String? = null,
): FormPostRequest {
    val normalizedRichtext = richtextJson
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val bodyField = if (normalizedRichtext != null) {
        require(normalizedRichtext.length <= REDDIT_PRIVATE_MESSAGE_RICHTEXT_MAX_LENGTH) {
            "Reddit private-message rich-text payload exceeds the supported limit"
        }
        "richtext_json" to normalizedRichtext
    } else {
        "text" to normalizeRedditPrivateMessageReplyText(text)
    }
    return FormPostRequest(
        pathSegments = listOf("api", "comment"),
        fields = listOf(
            "api_type" to "json",
            "raw_json" to "1",
            "thing_id" to normalizeRedditPrivateMessageFullname(parentFullname),
            bodyField,
        ),
    )
}

internal fun normalizeRedditPrivateMessageFullname(value: String): String {
    val normalized = value.trim().lowercase()
    require(normalized.startsWith(REDDIT_PRIVATE_MESSAGE_FULLNAME_PREFIX)) {
        "Reddit private-message reply target must be a t4 fullname"
    }
    val identifier = normalized.removePrefix(REDDIT_PRIVATE_MESSAGE_FULLNAME_PREFIX)
    require(identifier.length in 1..REDDIT_PRIVATE_MESSAGE_ID_MAX_LENGTH) {
        "Reddit private-message ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Reddit private-message ID contains unsupported characters"
    }
    return "$REDDIT_PRIVATE_MESSAGE_FULLNAME_PREFIX$identifier"
}

internal fun normalizeRedditPrivateMessageReplyText(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Reddit private-message reply cannot be blank" }
    require(normalized.length <= MESSAGE_REPLY_TEXT_MAX_LENGTH) {
        "Reddit private-message reply exceeds the supported limit"
    }
    return normalized
}

internal fun redditMessageReplyProviderCode(error: JsonElement): String? {
    val array = error as? JsonArray ?: return null
    val raw = (array.getOrNull(0) as? JsonPrimitive)?.contentOrNull
        ?.trim()
        ?.uppercase()
        ?: return null
    return raw.takeIf { value ->
        value.length in 1..64 && value.all { character ->
            character.isLetterOrDigit() || character == '_'
        }
    }
}

private const val REDDIT_PRIVATE_MESSAGE_FULLNAME_PREFIX = "t4_"
private const val REDDIT_PRIVATE_MESSAGE_ID_MAX_LENGTH = 32
private const val REDDIT_PRIVATE_MESSAGE_RICHTEXT_MAX_LENGTH = 100_000
