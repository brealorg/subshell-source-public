package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal data class RedditTextPostSubmitResponseDto(
    val json: RedditTextPostSubmitJsonDto = RedditTextPostSubmitJsonDto(),
)

@Serializable
internal data class RedditTextPostSubmitJsonDto(
    val errors: List<JsonElement> = emptyList(),
    val data: RedditTextPostSubmitDataDto = RedditTextPostSubmitDataDto(),
)

@Serializable
internal data class RedditTextPostSubmitDataDto(
    val id: String? = null,
    val name: String? = null,
    val url: String? = null,
    @SerialName("websocket_url") val webSocketUrl: String? = null,
)

internal enum class RedditTextPostApiErrorKind {
    RATE_LIMIT,
    VALIDATION,
    COMMUNITY_UNAVAILABLE,
    PERMISSION,
    SUBMISSION_RESTRICTED,
    CHALLENGE_REQUIRED,
    UNKNOWN,
}

internal data class RedditTextPostApiError(
    val kind: RedditTextPostApiErrorKind,
    val code: String? = null,
    val field: String? = null,
)

internal data class RedditSubmittedTextPost(
    val fullname: String,
    val id: String,
    val community: String,
    val title: String,
    val text: String,
    val permalink: String?,
    val remoteImageUri: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val galleryRemoteImageUris: List<String> = emptyList(),
    // Set only by source-verified listing reconciliation, not by the submit ACK.
    val verifiedCrosspostFullname: String? = null,
)

internal sealed interface RedditTextPostSubmitPayload {
    data class Created(
        val post: RedditSubmittedTextPost,
    ) : RedditTextPostSubmitPayload

    data class ApiFailure(
        val errors: List<RedditTextPostApiError>,
    ) : RedditTextPostSubmitPayload

    data class AwaitMediaCompletion(
        val webSocketUrl: String,
    ) : RedditTextPostSubmitPayload

    data object Malformed : RedditTextPostSubmitPayload
}

internal fun RedditTextPostSubmitResponseDto.toTextPostSubmitPayload(
    community: String,
    title: String,
    text: String,
    mediaSubmission: Boolean = false,
): RedditTextPostSubmitPayload {
    if (json.errors.isNotEmpty()) {
        return RedditTextPostSubmitPayload.ApiFailure(
            errors = json.errors
                .map { error -> error.toSanitizedTextPostApiError() }
                .distinct(),
        )
    }

    if (mediaSubmission) {
        json.data.webSocketUrl
            ?.trim()
            ?.takeIf { candidate -> candidate.length in 1..MAX_MEDIA_WEBSOCKET_URL_LENGTH }
            ?.let { webSocketUrl ->
                return RedditTextPostSubmitPayload.AwaitMediaCompletion(webSocketUrl)
            }
    }

    val fullname = normalizedSubmittedPostFullname(
        name = json.data.name,
        id = json.data.id,
    ) ?: return RedditTextPostSubmitPayload.Malformed

    return RedditTextPostSubmitPayload.Created(
        post = RedditSubmittedTextPost(
            fullname = fullname,
            id = fullname.removePrefix(REDDIT_POST_FULLNAME_PREFIX),
            community = community,
            title = title,
            text = text,
            permalink = json.data.url?.toSanitizedRedditPermalink(),
        ),
    )
}

internal fun RedditTextPostSubmitResponseDto.toGallerySubmitPayload(
    community: String,
    title: String,
    text: String,
): RedditTextPostSubmitPayload {
    if (json.errors.isNotEmpty()) {
        return RedditTextPostSubmitPayload.ApiFailure(
            errors = json.errors
                .map { error -> error.toSanitizedTextPostApiError() }
                .distinct(),
        )
    }
    val permalink = json.data.url?.toSanitizedRedditPermalink()
        ?: return RedditTextPostSubmitPayload.Malformed
    val permalinkId = permalink
        .split('/')
        .filter(String::isNotEmpty)
        .let { segments ->
            val commentsIndex = segments.indexOfFirst { segment ->
                segment.equals("comments", ignoreCase = true)
            }
            segments.getOrNull(commentsIndex + 1)
                ?.lowercase()
                ?.takeIf { candidate ->
                    commentsIndex >= 0 && isValidRedditThingIdentifier(candidate)
                }
        }
        ?: return RedditTextPostSubmitPayload.Malformed
    val responseFullname = normalizedSubmittedPostFullname(
        name = json.data.name,
        id = json.data.id,
    )
    val responseId = responseFullname?.removePrefix(REDDIT_POST_FULLNAME_PREFIX)
    if (responseId != null && responseId != permalinkId) {
        return RedditTextPostSubmitPayload.Malformed
    }
    val id = responseId ?: permalinkId
    return RedditTextPostSubmitPayload.Created(
        post = RedditSubmittedTextPost(
            fullname = "$REDDIT_POST_FULLNAME_PREFIX$id",
            id = id,
            community = community,
            title = title,
            text = text,
            permalink = permalink,
        ),
    )
}

private fun normalizedSubmittedPostFullname(
    name: String?,
    id: String?,
): String? {
    val nameIdentifier = name
        ?.trim()
        ?.lowercase()
        ?.takeIf { candidate -> candidate.startsWith(REDDIT_POST_FULLNAME_PREFIX) }
        ?.removePrefix(REDDIT_POST_FULLNAME_PREFIX)
        ?.takeIf(::isValidRedditThingIdentifier)
    val idIdentifier = id
        ?.trim()
        ?.lowercase()
        ?.removePrefix(REDDIT_POST_FULLNAME_PREFIX)
        ?.takeIf(::isValidRedditThingIdentifier)

    if (nameIdentifier != null && idIdentifier != null && nameIdentifier != idIdentifier) {
        return null
    }
    val identifier = nameIdentifier ?: idIdentifier ?: return null
    return "$REDDIT_POST_FULLNAME_PREFIX$identifier"
}

private fun isValidRedditThingIdentifier(value: String): Boolean =
    value.length in 1..REDDIT_THING_ID_MAX_LENGTH && value.all(Char::isLetterOrDigit)

private fun String.toSanitizedRedditPermalink(): String? {
    val parsed = trim().toHttpUrlOrNull() ?: return null
    if (!parsed.isHttps) return null
    if (parsed.host.lowercase() !in REDDIT_PERMALINK_HOSTS) return null
    return parsed.encodedPath.takeIf { path -> path.startsWith('/') }
}

private fun JsonElement.toSanitizedTextPostApiError(): RedditTextPostApiError {
    val tuple = this as? JsonArray
    val code = (tuple
        ?.getOrNull(0) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.uppercase()
        ?.takeIf { candidate ->
            candidate.length in 1..MAX_API_ERROR_CODE_LENGTH &&
                candidate.all { character ->
                    character.isLetterOrDigit() || character == '_'
                }
        }
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
        "RATELIMIT" -> RedditTextPostApiErrorKind.RATE_LIMIT

        "NO_TITLE",
        "BAD_TITLE",
        "TITLE_TOO_LONG",
        "TOO_LONG",
        "BAD_TEXT",
        "BAD_URL",
        "BAD_IMAGE",
        "BAD_MEDIA",
        "BAD_VIDEO",
        -> RedditTextPostApiErrorKind.VALIDATION

        else -> if (
            code?.startsWith("SUBMIT_VALIDATION_") == true ||
            code?.contains("GALLERY") == true ||
            code?.contains("IMAGE") == true ||
            code?.contains("MEDIA") == true ||
            code?.contains("VIDEO") == true ||
            field?.lowercase() in setOf(
                "gallery",
                "image",
                "image_url",
                "items",
                "media",
                "media_id",
                "video",
                "video_url",
                "video_poster_url",
            )
        ) {
            RedditTextPostApiErrorKind.VALIDATION
        } else {
            when (code) {
                "SUBREDDIT_NOEXIST",
                "INVALID_SUBREDDIT",
                "BAD_SR_NAME",
                "SUBREDDIT_REQUIRED",
                -> RedditTextPostApiErrorKind.COMMUNITY_UNAVAILABLE

                "USER_REQUIRED",
                "SUBREDDIT_NOTALLOWED",
                "USER_BLOCKED",
                "FORBIDDEN",
                -> RedditTextPostApiErrorKind.PERMISSION

                "SUBREDDIT_DISABLED",
                "SUBREDDIT_NOPOST",
                "POSTING_RESTRICTED",
                "RESTRICTED",
                -> RedditTextPostApiErrorKind.SUBMISSION_RESTRICTED

                "BAD_CAPTCHA",
                "CAPTCHA_REQUIRED",
                "CHALLENGE_REQUIRED",
                -> RedditTextPostApiErrorKind.CHALLENGE_REQUIRED

                else -> RedditTextPostApiErrorKind.UNKNOWN
            }
        }
    }
    return RedditTextPostApiError(kind = kind, code = code, field = field)
}

private const val REDDIT_POST_FULLNAME_PREFIX = "t3_"
private const val REDDIT_THING_ID_MAX_LENGTH = 16
private const val MAX_API_ERROR_CODE_LENGTH = 96
private const val MAX_API_ERROR_FIELD_LENGTH = 64
private const val MAX_MEDIA_WEBSOCKET_URL_LENGTH = 4_096
private val REDDIT_PERMALINK_HOSTS = setOf(
    "reddit.com",
    "www.reddit.com",
    "old.reddit.com",
    "new.reddit.com",
    "redd.it",
)
