package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RedditProfileCommentsListingDto(
    val data: RedditProfileCommentsListingDataDto =
        RedditProfileCommentsListingDataDto(),
)

@Serializable
internal data class RedditProfileCommentsListingDataDto(
    val after: String? = null,
    val children: List<RedditProfileCommentThingDto> = emptyList(),
)

@Serializable
internal data class RedditProfileCommentThingDto(
    val kind: String? = null,
    val data: RedditProfileCommentDataDto =
        RedditProfileCommentDataDto(),
)

@Serializable
internal data class RedditProfileCommentDataDto(
    val id: String = "",
    val name: String? = null,
    val author: String? = null,
    val subreddit: String? = null,
    @SerialName("subreddit_name_prefixed")
    val subredditNamePrefixed: String? = null,
    @SerialName("link_title")
    val linkTitle: String? = null,
    val body: String = "",
    val score: Long = 0L,
    @SerialName("created_utc")
    val createdUtc: Double? = null,
    @SerialName("link_id")
    val linkId: String? = null,
    // ISSUE8_INLINE_REDDIT_MEDIA_V1: images, Giphy and emotes resolve like a
    // thread comment's (the body alone says "Processing img …").
    @SerialName("media_metadata")
    val mediaMetadata: Map<String, RedditMediaMetadataDto> = emptyMap(),
)

internal fun redditProfileCommentsJsonRequest(
    username: String,
    cursor: String?,
    limit: Int = REDDIT_PROFILE_COMMENTS_PAGE_SIZE,
): JsonGetRequest {
    val normalizedUsername =
        normalizeRedditProfileCommentsUsername(username)

    require(limit in 1..REDDIT_PROFILE_COMMENTS_MAX_PAGE_SIZE) {
        "Reddit profile comment limit is outside the supported range"
    }

    val after =
        cursor
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    return JsonGetRequest(
        pathSegments =
            listOf(
                "user",
                normalizedUsername,
                "comments",
            ),
        queryParameters =
            buildList {
                add("raw_json" to "1")
                add("sort" to "new")
                add("limit" to limit.toString())

                if (after != null) {
                    add("after" to after)
                }
            },
    )
}

internal fun RedditProfileCommentsListingDto.toProfileCommentsPage(
    nowEpochSeconds: Long,
    limit: Int = REDDIT_PROFILE_COMMENTS_PAGE_SIZE,
): NetworkPage<ProfileCommentFixture> {
    require(limit in 1..REDDIT_PROFILE_COMMENTS_MAX_PAGE_SIZE)

    val mapped =
        data.children
            .asSequence()
            .filter { thing ->
                thing.kind == null ||
                    thing.kind == REDDIT_PROFILE_COMMENT_KIND
            }
            .mapNotNull { thing ->
                thing.toProfileComment(nowEpochSeconds)
            }
            .distinctBy(ProfileCommentFixture::id)
            .take(limit)
            .toList()

    return NetworkPage(
        items = mapped,
        nextCursor =
            data.after
                ?.trim()
                ?.takeIf(String::isNotEmpty),
    )
}

private fun RedditProfileCommentThingDto.toProfileComment(
    nowEpochSeconds: Long,
): ProfileCommentFixture? {
    val rawId =
        data.id
            .trim()
            .takeIf { value ->
                value.isNotEmpty() &&
                    value.length <= REDDIT_PROFILE_THING_ID_MAX_LENGTH &&
                    value.all(Char::isLetterOrDigit)
            }
            ?: return null

    val fullname =
        data.name
            ?.trim()
            ?.takeIf { value ->
                value.equals(
                    "$REDDIT_PROFILE_COMMENT_PREFIX$rawId",
                    ignoreCase = true,
                )
            }
            ?.lowercase()
            ?: "$REDDIT_PROFILE_COMMENT_PREFIX${rawId.lowercase()}"

    val linkId =
        data.linkId
            ?.trim()
            ?.lowercase()
            ?.takeIf { value ->
                value.startsWith(REDDIT_PROFILE_POST_PREFIX)
            }
            ?: return null

    val postId =
        linkId
            .removePrefix(REDDIT_PROFILE_POST_PREFIX)
            .takeIf { value ->
                value.isNotEmpty() &&
                    value.length <= REDDIT_PROFILE_THING_ID_MAX_LENGTH &&
                    value.all(Char::isLetterOrDigit)
            }
            ?: return null

    val body =
        data.body
            .withRedditMediaAnnotations(data.mediaMetadata, appendUnplaced = true)
            .takeIf(String::isNotBlank)
            ?: return null

    val createdEpochSeconds =
        data.createdUtc
            ?.takeIf { value ->
                value.isFinite() && value >= 0.0
            }
            ?.toLong()

    return ProfileCommentFixture(
        id = fullname,
        author =
            data.author
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: "[deleted]",
        postId = postId,
        age =
            redditRelativeAgeLabel(
                nowEpochSeconds,
                createdEpochSeconds,
            ),
        body = body,
        score =
            data.score
                .coerceIn(
                    Int.MIN_VALUE.toLong(),
                    Int.MAX_VALUE.toLong(),
                )
                .toInt(),
        placement = ThreadPlacement.TOP,
        community = data.profileCommentCommunity(),
        postTitle = data.profileCommentPostTitle(),
    )
}

internal fun RedditProfileCommentDataDto.profileCommentCommunity(): String? =
    subredditNamePrefixed
        ?.trim()
        ?.removePrefix("r/")
        ?.takeIf(String::isNotEmpty)
        ?: subreddit
            ?.trim()
            ?.removePrefix("r/")
            ?.takeIf(String::isNotEmpty)

internal fun RedditProfileCommentDataDto.profileCommentPostTitle(): String? =
    linkTitle
        ?.trim()
        ?.takeIf(String::isNotEmpty)

internal fun normalizeRedditProfileCommentsUsername(
    value: String,
): String {
    val trimmed = value.trim()

    val withoutPrefix =
        if (trimmed.startsWith("u/", ignoreCase = true)) {
            trimmed.drop(2)
        } else {
            trimmed
        }

    val normalized = withoutPrefix.trim()

    require(normalized.isNotEmpty()) {
        "Reddit profile username cannot be blank"
    }

    require(normalized.length <= REDDIT_PROFILE_USERNAME_MAX_LENGTH) {
        "Reddit profile username is too long"
    }

    require(
        normalized.none { character ->
            character == '/' ||
                character == '?' ||
                character == '#' ||
                character.isWhitespace()
        },
    ) {
        "Reddit profile username contains unsupported characters"
    }

    return normalized
}

internal const val REDDIT_PROFILE_COMMENTS_PAGE_SIZE = 25
internal const val REDDIT_PROFILE_COMMENTS_MAX_PAGE_SIZE = 100

private const val REDDIT_PROFILE_USERNAME_MAX_LENGTH = 64
private const val REDDIT_PROFILE_THING_ID_MAX_LENGTH = 16
private const val REDDIT_PROFILE_COMMENT_KIND = "t1"
private const val REDDIT_PROFILE_COMMENT_PREFIX = "t1_"
private const val REDDIT_PROFILE_POST_PREFIX = "t3_"
