package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.COMMENT_EDIT_TEXT_MAX_CHARS
import app.subshell.prototype.data.account.normalizeCommentEditFullname
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
internal data class RedditCommentEditResponseDto(
    val json: RedditCommentEditJsonDto = RedditCommentEditJsonDto(),
)

@Serializable
internal data class RedditCommentEditJsonDto(
    val errors: List<JsonElement> = emptyList(),
)

internal data class RedditCommentEditApiError(
    val code: String?,
    val message: String?,
    val field: String?,
)

internal sealed interface RedditCommentEditNetworkResult {
    data class Edited(
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentEditNetworkResult

    data class ApiFailure(
        val errors: List<RedditCommentEditApiError>,
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentEditNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditCommentEditNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditCommentEditNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditCommentEditNetworkResult

    /** ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: the rich-text edit could not be prepared; nothing was sent. */
    data class NotSent(
        val reason: String,
    ) : RedditCommentEditNetworkResult
}

// H1_COMMENT_EDIT_READBACK_V1: what Reddit shows for the edited comment now.
internal sealed interface RedditCommentEditReadback {
    data class Body(
        val text: String,
        val metadata: NetworkResponseMetadata,
        val edited: Double? = null,
    ) : RedditCommentEditReadback

    data object Absent : RedditCommentEditReadback

    data object Unreadable : RedditCommentEditReadback
}

// COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: one comment read back by fullname,
// its media placeholders resolved from media_metadata; null when Reddit does
// not return exactly that comment.
internal suspend fun readRedditCommentResolvedBody(
    httpClient: SubshellHttpClient,
    postId: String,
    commentFullname: String,
): String? {
    val target = normalizeCommentEditFullname(commentFullname)
    val result = httpClient.getJson(
        request = redditCommentDeleteInfoRequest(postId, target),
        deserializer = redditCommentReadListing(),
    )
    if (result !is NetworkResult.Success) return null
    return result.value.data.children
        .filter { thing -> redditCommentThingFullname(thing.data) == target }
        .singleOrNull()
        ?.data
        ?.bodyWithRedditMediaMetadata()
}

internal class RedditCommentEditNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    // ISSUE281_READS_AVOID_API_INFO_V1: the comment is read back from this post's thread.
    private val postId: String,
) {
    suspend fun readBack(commentFullname: String): RedditCommentEditReadback {
        val target = normalizeCommentEditFullname(commentFullname)
        return when (
            val result = httpClient.getJson(
                request = redditCommentDeleteInfoRequest(postId, target),
                deserializer = redditCommentReadListing(),
            )
        ) {
            is NetworkResult.Success -> {
                val children = result.value.data.children
                val matches = children.filter { thing -> redditCommentThingFullname(thing.data) == target }
                when {
                    children.isEmpty() -> RedditCommentEditReadback.Absent
                    matches.size != 1 -> RedditCommentEditReadback.Unreadable
                    else -> matches.single().data.let { comment ->
                        RedditCommentEditReadback.Body(comment.body, result.metadata, redditEditedStamp(comment.edited))
                    }
                }
            }
            else -> RedditCommentEditReadback.Unreadable
        }
    }

    // EDITREADBACK01_EDITED_STAMP_V1: read with the media metadata, so only
    // a rich-text edit has one.
    var richTextEditedBefore: Double? = null
        private set

    private suspend fun currentMediaMetadata(target: String): Map<String, RedditMediaMetadataDto>? =
        when (
            val result = httpClient.getJson(
                request = redditCommentDeleteInfoRequest(postId, target),
                deserializer = redditCommentReadListing(),
            )
        ) {
            is NetworkResult.Success -> result.value.data.children
                .singleOrNull { thing -> redditCommentThingFullname(thing.data) == target }
                ?.data
                ?.also { comment -> richTextEditedBefore = redditEditedStamp(comment.edited) }
                ?.mediaMetadata
            else -> null
        }

    suspend fun edit(
        commentFullname: String,
        text: String,
    ): RedditCommentEditNetworkResult {
        val target = normalizeCommentEditFullname(commentFullname)
        // ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: text with media placeholders goes as rich text.
        val request = when (
            val richText = prepareRedditMediaEditRichText(
                httpClient = httpClient,
                source = normalizeRedditCommentEditText(text),
                mediaMetadata = { currentMediaMetadata(target) },
            )
        ) {
            RedditMediaEditRichText.NotNeeded -> redditCommentEditFormRequest(commentFullname, text)
            is RedditMediaEditRichText.Ready -> redditRichTextEditFormRequest(target, richText.richtextJson)
            is RedditMediaEditRichText.Unavailable -> return RedditCommentEditNetworkResult.NotSent(richText.reason)
        }
        return when (
            val result = httpClient.postFormJson(
                request = request,
                deserializer = RedditCommentEditResponseDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> {
                val errors = parseRedditCommentEditApiErrors(result.value.json.errors)
                if (result.value.json.errors.isEmpty()) {
                    RedditCommentEditNetworkResult.Edited(result.metadata)
                } else {
                    RedditCommentEditNetworkResult.ApiFailure(
                        errors = errors,
                        metadata = result.metadata,
                    )
                }
            }

            is NetworkResult.HttpFailure -> RedditCommentEditNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
                providerReason = result.providerReason,
            )

            is NetworkResult.TransportFailure -> RedditCommentEditNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

            is NetworkResult.ProtocolFailure -> RedditCommentEditNetworkResult.ProtocolFailure(
                kind = result.kind,
                jsonPath = result.jsonPath,
            )
        }
    }
}

internal fun parseRedditCommentEditApiErrors(
    values: List<JsonElement>,
): List<RedditCommentEditApiError> = values.mapNotNull { value ->
    val parts = value as? JsonArray ?: return@mapNotNull null
    fun part(index: Int): String? = (parts.getOrNull(index) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    RedditCommentEditApiError(
        code = part(0),
        message = part(1),
        field = part(2),
    )
}

internal fun redditCommentEditFormRequest(
    commentFullname: String,
    text: String,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "editusertext"),
    fields = listOf(
        "api_type" to "json",
        "raw_json" to "1",
        "thing_id" to normalizeCommentEditFullname(commentFullname),
        "text" to normalizeRedditCommentEditText(text),
    ),
)

internal fun normalizeRedditCommentEditText(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Edited Reddit comment text cannot be blank" }
    require(normalized.length <= COMMENT_EDIT_TEXT_MAX_CHARS) {
        "Edited Reddit comment text exceeds the supported limit"
    }
    return normalized
}
