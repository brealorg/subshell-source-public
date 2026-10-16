package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.Comment
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface RedditCommentSubmitNetworkResult {
    data class Created(
        val comment: Comment,
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentSubmitNetworkResult

    data class ApiFailure(
        val errors: List<RedditCommentApiError>,
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentSubmitNetworkResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
    ) : RedditCommentSubmitNetworkResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditCommentSubmitNetworkResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditCommentSubmitNetworkResult

    // COMPOSER11_COMMENT_REPLY_DIAGNOSTIC_V1: what did not fit (error type,
    // comment count, key names; never values), shown with the failure.
    data class MalformedPayload(val diagnostic: String? = null) : RedditCommentSubmitNetworkResult
}

internal class RedditCommentSubmitNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    private val clock: EpochSecondsClock = SystemEpochSecondsClock,
    private val json: Json = redditCommentSubmitReplyJson(SubshellNetworkJson.providerJson),
) {
    suspend fun submit(
        parentFullname: String,
        text: String,
        richtextJson: String? = null,
    ): RedditCommentSubmitNetworkResult {
        val request = redditCommentSubmitFormRequest(
            parentFullname = parentFullname,
            text = text,
            richtextJson = richtextJson,
        )
        return when (
            val result = httpClient.postFormJson(
                request = request,
                deserializer = JsonElement.serializer(),
            )
        ) {
            is NetworkResult.Success -> when (
                val payload = try {
                    redditCommentSubmitResponse(json, result.value)
                        .toSubmitPayload(clock.epochSeconds())
                } catch (error: SerializationException) {
                    return RedditCommentSubmitNetworkResult.MalformedPayload(
                        redditCommentReplyDiagnostic(result.value, error),
                    )
                } catch (error: IllegalArgumentException) {
                    return RedditCommentSubmitNetworkResult.MalformedPayload(
                        redditCommentReplyDiagnostic(result.value, error),
                    )
                }
            ) {
                is RedditCommentSubmitPayload.Created ->
                    RedditCommentSubmitNetworkResult.Created(
                        comment = payload.comment,
                        metadata = result.metadata,
                    )

                is RedditCommentSubmitPayload.ApiFailure ->
                    RedditCommentSubmitNetworkResult.ApiFailure(
                        errors = payload.errors,
                        metadata = result.metadata,
                    )

                RedditCommentSubmitPayload.Malformed ->
                    RedditCommentSubmitNetworkResult.MalformedPayload(
                        redditCommentReplyDiagnostic(result.value, null),
                    )
            }

            is NetworkResult.HttpFailure -> RedditCommentSubmitNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
                metadata = result.metadata,
            )

            is NetworkResult.TransportFailure -> RedditCommentSubmitNetworkResult.TransportFailure(
                kind = result.kind,
                retryable = result.retryable,
            )

            is NetworkResult.ProtocolFailure -> RedditCommentSubmitNetworkResult.ProtocolFailure(
                kind = result.kind,
                jsonPath = result.jsonPath,
            )
        }
    }
}

internal fun redditCommentSubmitFormRequest(
    parentFullname: String,
    text: String,
    richtextJson: String? = null,
): FormPostRequest {
    val normalizedRichtext = richtextJson
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val bodyField = if (normalizedRichtext != null) {
        require(normalizedRichtext.length <= REDDIT_COMMENT_RICHTEXT_MAX_LENGTH) {
            "Reddit comment rich-text payload exceeds the supported limit"
        }
        "richtext_json" to normalizedRichtext
    } else {
        "text" to normalizeRedditCommentText(text)
    }
    return FormPostRequest(
        pathSegments = listOf("api", "comment"),
        fields = listOf(
            "api_type" to "json",
            "raw_json" to "1",
            "thing_id" to normalizeRedditCommentParentFullname(parentFullname),
            bodyField,
        ),
    )
}

internal fun normalizeRedditCommentParentFullname(value: String): String {
    val normalized = value.trim().lowercase()
    val prefix = when {
        normalized.startsWith(REDDIT_COMMENT_FULLNAME_PREFIX) ->
            REDDIT_COMMENT_FULLNAME_PREFIX

        normalized.startsWith(REDDIT_POST_FULLNAME_PREFIX) ->
            REDDIT_POST_FULLNAME_PREFIX

        else -> throw IllegalArgumentException(
            "Reddit comment parent must be a t1 or t3 fullname",
        )
    }
    val identifier = normalized.removePrefix(prefix)
    require(identifier.length in 1..REDDIT_THING_ID_MAX_LENGTH) {
        "Reddit comment parent ID length is outside the supported range"
    }
    require(identifier.all(Char::isLetterOrDigit)) {
        "Reddit comment parent ID contains unsupported characters"
    }
    return "$prefix$identifier"
}

internal fun normalizeRedditCommentText(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Reddit comment text cannot be blank" }
    require(normalized.length <= REDDIT_COMMENT_TEXT_MAX_LENGTH) {
        "Reddit comment text exceeds the supported limit"
    }
    return normalized
}

internal const val REDDIT_COMMENT_TEXT_MAX_LENGTH = 10_000
private const val REDDIT_THING_ID_MAX_LENGTH = 16
private const val REDDIT_COMMENT_FULLNAME_PREFIX = "t1_"
private const val REDDIT_POST_FULLNAME_PREFIX = "t3_"

private const val REDDIT_COMMENT_RICHTEXT_MAX_LENGTH = 100_000

// COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: the comment-send reply is read with
// nulls taking the field defaults: an image comment's first reply can carry
// nulls where a finished comment has values.
internal fun redditCommentSubmitReplyJson(base: Json): Json = Json(from = base) {
    coerceInputValues = true
}

// COMPOSER11_COMMENT_REPLY_DIAGNOSTIC_V1: the shape of an unreadable reply,
// from key names and counts only (no values, no content, no exception text).
internal fun redditCommentReplyDiagnostic(reply: JsonElement, error: Exception?): String {
    val parts = mutableListOf<String>()
    // ERROR01A5: never a throwable's text; the error type only.
    error?.let { parts += "decode ${it::class.simpleName}" }
    val root = reply as? JsonObject
    parts += "keys ${root?.keys?.sorted() ?: reply::class.simpleName}"
    val things = (((root?.get("json") as? JsonObject)?.get("data") as? JsonObject)?.get("things") as? JsonArray)
    if (things != null) {
        val kinds = things.map { thing -> ((thing as? JsonObject)?.get("kind") as? JsonPrimitive)?.content }
        parts += "things ${things.size} kinds $kinds"
        val data = (things.firstOrNull() as? JsonObject)?.get("data") as? JsonObject
        if (data != null) {
            val nulls = data.filterValues { it is kotlinx.serialization.json.JsonNull }.keys.sorted()
            parts += "first has id=${"id" in data} name=${"name" in data} nulls $nulls"
        }
    }
    return parts.joinToString("; ")
}

// COMPOSER11_FLAT_RICHTEXT_COMMENT_REPLY_V1: a richtext_json comment (every
// image/GIF comment) is answered with the comment object itself, not the
// api_type=json envelope a text comment gets (device-verified 2026-09-29: the
// reply's top-level keys were the comment's own, all_awardings, author, ...).
// A flat comment is read through the same envelope mapping as a wrapped one.
internal fun redditCommentSubmitResponse(json: Json, reply: JsonElement): RedditCommentSubmitResponseDto {
    val root = reply as? JsonObject
    val flatComment = root != null && "json" !in root && ("name" in root || "id" in root)
    if (!flatComment) {
        return json.decodeFromJsonElement(RedditCommentSubmitResponseDto.serializer(), reply)
    }
    return RedditCommentSubmitResponseDto(
        json = RedditCommentSubmitJsonDto(
            data = RedditCommentSubmitDataDto(
                things = listOf(
                    RedditCommentThingDto(
                        kind = "t1",
                        data = json.decodeFromJsonElement(RedditCommentDataDto.serializer(), reply),
                    ),
                ),
            ),
        ),
    )
}
