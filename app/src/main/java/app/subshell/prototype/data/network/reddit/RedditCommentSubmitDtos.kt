package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.Comment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class RedditCommentSubmitResponseDto(
    val json: RedditCommentSubmitJsonDto = RedditCommentSubmitJsonDto(),
)

@Serializable
internal data class RedditCommentSubmitJsonDto(
    val errors: List<JsonElement> = emptyList(),
    val data: RedditCommentSubmitDataDto = RedditCommentSubmitDataDto(),
)

@Serializable
internal data class RedditCommentSubmitDataDto(
    val things: List<RedditCommentThingDto> = emptyList(),
)

internal enum class RedditCommentApiErrorKind {
    RATE_LIMIT,
    VALIDATION,
    PARENT_UNAVAILABLE,
    PERMISSION,
    UNKNOWN,
}

internal data class RedditCommentApiError(
    val kind: RedditCommentApiErrorKind,
    val code: String? = null,
    val field: String? = null,
)

internal sealed interface RedditCommentSubmitPayload {
    data class Created(
        val comment: Comment,
    ) : RedditCommentSubmitPayload

    data class ApiFailure(
        val errors: List<RedditCommentApiError>,
    ) : RedditCommentSubmitPayload

    data object Malformed : RedditCommentSubmitPayload
}

internal fun RedditCommentSubmitResponseDto.toSubmitPayload(
    nowEpochSeconds: Long,
): RedditCommentSubmitPayload {
    if (json.errors.isNotEmpty()) {
        return RedditCommentSubmitPayload.ApiFailure(
            errors = json.errors
                .map { error -> error.toSanitizedApiError() }
                .distinct(),
        )
    }

    val commentThings = json.data.things.filter { thing -> thing.kind == REDDIT_COMMENT_KIND }
    if (commentThings.size != 1) return RedditCommentSubmitPayload.Malformed

    val data = commentThings.single().data
    val commentId = data.name
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: data.id
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { id -> "t1_$id" }
        ?: return RedditCommentSubmitPayload.Malformed
    val projectedBody = data.bodyWithRedditMediaMetadata()
    // COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: the comment id proves Reddit
    // created it. An image-only comment can come back with an empty body while
    // the image is processed; it is read back afterwards, not "malformed"
    // (which left a posted comment as outcome unknown).
    if (!commentId.startsWith("t1_")) {
        return RedditCommentSubmitPayload.Malformed
    }

    return RedditCommentSubmitPayload.Created(
        comment = Comment(
            id = commentId,
            author = data.author
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: "[deleted]",
            authorFlairText =
                data.authorFlairText
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
            authorFlairTextColor =
                data.authorFlairTextColor
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
            authorFlairBackgroundColor =
                data.authorFlairBackgroundColor
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
            authorFlairRichText =
                data.authorFlairRichText.toFlairRichTextSegments(),
            age = redditRelativeAgeLabel(
                nowEpochSeconds,
                data.createdUtc?.toLong(),
            ),
            body = projectedBody,
            score = data.score
                .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                .toInt(),
            // CORRECT01_SUBMITTED_COMMENT_KEEPS_VOTE_V1 (OCB-544): the author's
            // own upvote and saved state, as the listing mapper reads them.
            vote = when (data.likes) {
                true -> 1
                false -> -1
                null -> 0
            },
            saved = data.saved,
            depth = data.depth.coerceIn(0, REDDIT_SUBMIT_MAX_DEPTH),
            // LOW_NET_COMMENT_ACCENT_FOLLOWS_DEPTH_V1 (OCB-543)
            accent = redditCommentAccent(data.depth.coerceIn(0, REDDIT_SUBMIT_MAX_DEPTH)),
            award = data.totalAwardsReceived
                .takeIf { count -> count > 0 }
                ?.let { count -> if (count == 1) "1 award" else "$count awards" },
            isOp = data.isSubmitter,
            isModerator = data.distinguished == "moderator",
            createdEpochSeconds = data.createdUtc?.toLong(),
            parentId = data.parentId?.trim()?.takeIf(String::isNotEmpty),
        ),
    )
}

private fun JsonElement.toSanitizedApiError(): RedditCommentApiError {
    val tuple = this as? JsonArray
    val code = (tuple
        ?.getOrNull(0) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.uppercase()
    val field = (tuple
        ?.getOrNull(2) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf { candidate ->
            candidate.length in 1..MAX_API_ERROR_FIELD_LENGTH &&
                candidate.all { character ->
                    character.isLetterOrDigit() || character == '_'
                }
        }

    val kind = when (code) {
        "RATELIMIT" -> RedditCommentApiErrorKind.RATE_LIMIT

        "NO_TEXT",
        "TOO_LONG",
        "BAD_COMMENT",
        -> RedditCommentApiErrorKind.VALIDATION

        "DELETED_COMMENT",
        "THREAD_LOCKED",
        "TOO_OLD",
        "ARCHIVED",
        -> RedditCommentApiErrorKind.PARENT_UNAVAILABLE

        "USER_REQUIRED",
        "SUBREDDIT_NOTALLOWED",
        "USER_BLOCKED",
        -> RedditCommentApiErrorKind.PERMISSION

        else -> RedditCommentApiErrorKind.UNKNOWN
    }
    val safeCode = code?.takeIf { candidate ->
        candidate.length in 1..80 &&
            candidate.all { character ->
                character.isLetterOrDigit() || character == '_' || character == '-'
            }
    }
    return RedditCommentApiError(kind = kind, code = safeCode, field = field)
}

private const val REDDIT_COMMENT_KIND = "t1"
private const val REDDIT_SUBMIT_MAX_DEPTH = 10
private const val MAX_API_ERROR_FIELD_LENGTH = 64
