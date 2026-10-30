package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.POST_EDIT_TEXT_MAX_CHARS
import app.subshell.prototype.data.account.normalizePostEditFullname
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
internal data class RedditPostEditResponseDto(
    val json: RedditPostEditJsonDto = RedditPostEditJsonDto(),
)

@Serializable
internal data class RedditPostEditJsonDto(
    val errors: List<JsonElement> = emptyList(),
)

internal data class RedditPostEditApiError(
    val code: String?,
    val message: String?,
    val field: String?,
)

internal sealed interface RedditPostEditNetworkResult {
    data class Edited(
        val metadata: NetworkResponseMetadata,
    ) : RedditPostEditNetworkResult

    data class ApiFailure(
        val errors: List<RedditPostEditApiError>,
        val metadata: NetworkResponseMetadata,
    ) : RedditPostEditNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditPostEditNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditPostEditNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditPostEditNetworkResult

    /** ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: the rich-text edit could not be prepared; nothing was sent. */
    data class NotSent(
        val reason: String,
    ) : RedditPostEditNetworkResult
}

// H2_POST_EDIT_READBACK_V1: what Reddit shows for the edited post body now.
internal sealed interface RedditPostEditReadback {
    data class Body(
        val text: String,
        val metadata: NetworkResponseMetadata,
        val edited: Double? = null,
    ) : RedditPostEditReadback

    data object Absent : RedditPostEditReadback

    data object Unreadable : RedditPostEditReadback
}

internal class RedditPostEditNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    private suspend fun readPost(target: String): NetworkResult<RedditListingDto> =
        httpClient.getJson(
            // ISSUE281_READS_AVOID_API_INFO_V1: read back from the post's thread.
            request = redditPostReadRequest(target),
            deserializer = redditThreadListing(RedditListingDto.serializer(), REDDIT_THREAD_POST_LISTING),
        )

    private fun RedditListingDto.postsNamed(target: String): List<RedditThingDto> =
        data.children.filter { thing ->
            val name = thing.data.name?.trim()?.lowercase()
                ?: thing.data.id.trim().lowercase().takeIf(String::isNotEmpty)?.let { id -> "t3_$id" }
            name == target
        }

    // EDITREADBACK01_EDITED_STAMP_V1: read with the media metadata, so only
    // a rich-text edit has one.
    var richTextEditedBefore: Double? = null
        private set

    private suspend fun currentMediaMetadata(target: String): Map<String, RedditMediaMetadataDto>? =
        when (val result = readPost(target)) {
            is NetworkResult.Success -> result.value.postsNamed(target).singleOrNull()?.data
                ?.also { post -> richTextEditedBefore = redditEditedStamp(post.edited) }
                ?.mediaMetadata
            else -> null
        }

    suspend fun readBack(postFullname: String): RedditPostEditReadback {
        val target = normalizePostEditFullname(postFullname)
        return when (val result = readPost(target)) {
            is NetworkResult.Success -> {
                val children = result.value.data.children
                val matches = result.value.postsNamed(target)
                when {
                    children.isEmpty() -> RedditPostEditReadback.Absent
                    matches.size != 1 -> RedditPostEditReadback.Unreadable
                    else -> matches.single().data.let { post ->
                        RedditPostEditReadback.Body(post.selftext, result.metadata, redditEditedStamp(post.edited))
                    }
                }
            }
            else -> RedditPostEditReadback.Unreadable
        }
    }

    suspend fun edit(
        postFullname: String,
        text: String,
    ): RedditPostEditNetworkResult {
        val target = normalizePostEditFullname(postFullname)
        // ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: text with media placeholders goes as rich text.
        val request = when (
            val richText = prepareRedditMediaEditRichText(
                httpClient = httpClient,
                source = normalizeRedditPostEditText(text),
                mediaMetadata = { currentMediaMetadata(target) },
            )
        ) {
            RedditMediaEditRichText.NotNeeded -> redditPostEditFormRequest(postFullname, text)
            is RedditMediaEditRichText.Ready -> redditRichTextEditFormRequest(target, richText.richtextJson)
            is RedditMediaEditRichText.Unavailable -> return RedditPostEditNetworkResult.NotSent(richText.reason)
        }
        return when (
            val result = httpClient.postFormJson(
                request = request,
                deserializer = RedditPostEditResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> {
                val errors = parseRedditPostEditApiErrors(result.value.json.errors)
                if (result.value.json.errors.isEmpty()) {
                    RedditPostEditNetworkResult.Edited(result.metadata)
                } else {
                    RedditPostEditNetworkResult.ApiFailure(
                        errors = errors,
                        metadata = result.metadata,
                    )
                }
            }

            is NetworkResult.HttpFailure -> RedditPostEditNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

            is NetworkResult.TransportFailure -> RedditPostEditNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

            is NetworkResult.ProtocolFailure -> RedditPostEditNetworkResult.ProtocolFailure(
                kind = result.kind,
                jsonPath = result.jsonPath,
            )
        }
    }
}

internal fun parseRedditPostEditApiErrors(
    values: List<JsonElement>,
): List<RedditPostEditApiError> = values.mapNotNull { value ->
    val parts = value as? JsonArray ?: return@mapNotNull null
    fun part(index: Int): String? = (parts.getOrNull(index) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    RedditPostEditApiError(
        code = part(0),
        message = part(1),
        field = part(2),
    )
}

internal fun redditPostEditFormRequest(
    postFullname: String,
    text: String,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "editusertext"),
    fields = listOf(
        "api_type" to "json",
        "raw_json" to "1",
        "thing_id" to normalizePostEditFullname(postFullname),
        "text" to normalizeRedditPostEditText(text),
    ),
)

internal fun normalizeRedditPostEditText(value: String): String {
    // LOWUI8_POST_EDIT_KEEPS_LEADING_INDENTATION_V1 (OCB-319): leading
    // whitespace is Markdown (an indented code block or nested list on the
    // first line); only the end is trimmed. The read-back compares trimmed
    // text (sameEditedText), so it holds whether Reddit keeps it or not.
    val normalized = value.trimEnd()
    require(normalized.length <= POST_EDIT_TEXT_MAX_CHARS) {
        "Edited Reddit post body exceeds the supported limit"
    }
    return normalized
}
