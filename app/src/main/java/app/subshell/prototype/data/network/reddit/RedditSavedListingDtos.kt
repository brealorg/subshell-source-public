package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

internal sealed interface RedditSavedHistoryItem {
    val fullname: String

    data class PostItem(
        val post: Post,
    ) : RedditSavedHistoryItem {
        override val fullname: String = normalizedSavedPostFullname(post.id)
    }

    data class CommentItem(
        val comment: ProfileCommentFixture,
    ) : RedditSavedHistoryItem {
        override val fullname: String = comment.id
    }
}

@Serializable
internal data class RedditSavedListingDto(
    val data: RedditSavedListingDataDto = RedditSavedListingDataDto(),
)

@Serializable
internal data class RedditSavedListingDataDto(
    val after: String? = null,
    val children: List<RedditSavedThingDto> = emptyList(),
)

@Serializable
internal data class RedditSavedThingDto(
    val kind: String? = null,
    val data: JsonElement,
)

@Serializable
internal data class RedditSavedCommentDataDto(
    val id: String = "",
    val name: String? = null,
    val author: String? = null,
    val body: String = "",
    val score: Long = 0L,
    @SerialName("created_utc") val createdUtc: Double? = null,
    @SerialName("link_id") val linkId: String? = null,
    val subreddit: String? = null,
    @SerialName("subreddit_name_prefixed") val subredditNamePrefixed: String? = null,
    @SerialName("link_title") val linkTitle: String? = null,
    // ISSUE8_INLINE_REDDIT_MEDIA_V1
    @SerialName("media_metadata") val mediaMetadata: Map<String, RedditMediaMetadataDto> = emptyMap(),
)

internal fun RedditSavedListingDto.toSavedHistoryPage(
    json: Json,
    nowEpochSeconds: Long,
): NetworkPage<RedditSavedHistoryItem> =
    NetworkPage(
        items = data.children.mapNotNull { child ->
            when (child.kind?.trim()?.lowercase(Locale.ROOT)) {
                REDDIT_SAVED_POST_KIND -> child.toSavedPostItem(
                    json = json,
                    nowEpochSeconds = nowEpochSeconds,
                )
                REDDIT_SAVED_COMMENT_KIND -> child.toSavedCommentItem(
                    json = json,
                    nowEpochSeconds = nowEpochSeconds,
                )
                else -> null
            }
        },
        nextCursor = data.after?.trim()?.takeIf(String::isNotEmpty),
    )

private fun RedditSavedThingDto.toSavedPostItem(
    json: Json,
    nowEpochSeconds: Long,
): RedditSavedHistoryItem.PostItem? {
    val postData = runCatching {
        json.decodeFromJsonElement(
            RedditPostDataDto.serializer(),
            data,
        )
    }.getOrNull() ?: return null

    val post = RedditListingDto(
        data = RedditListingDataDto(
            children = listOf(
                RedditThingDto(
                    kind = REDDIT_SAVED_POST_KIND,
                    data = postData,
                ),
            ),
        ),
    ).toNetworkPage(nowEpochSeconds)
        .items
        .singleOrNull()
        ?: return null

    return RedditSavedHistoryItem.PostItem(post)
}

private fun RedditSavedThingDto.toSavedCommentItem(
    json: Json,
    nowEpochSeconds: Long,
): RedditSavedHistoryItem.CommentItem? {
    val commentData = runCatching {
        json.decodeFromJsonElement(
            RedditSavedCommentDataDto.serializer(),
            data,
        )
    }.getOrNull() ?: return null

    val comment = commentData.toProfileComment(nowEpochSeconds)
        ?: return null
    return RedditSavedHistoryItem.CommentItem(comment)
}

private fun RedditSavedCommentDataDto.toProfileComment(
    nowEpochSeconds: Long,
): ProfileCommentFixture? {
    val commentFullname = normalizedSavedCommentFullname(
        name = name,
        id = id,
    ) ?: return null
    val postId = normalizedSavedParentPostId(linkId)
        ?: return null
    val commentBody = body
        .withRedditMediaAnnotations(mediaMetadata, appendUnplaced = true)
        .trim()
        .takeIf(String::isNotEmpty)
        ?: return null

    return ProfileCommentFixture(
        id = commentFullname,
        author = author?.trim()?.takeIf(String::isNotEmpty) ?: "[deleted]",
        postId = postId,
        age = redditRelativeAgeLabel(
            nowEpochSeconds = nowEpochSeconds,
            createdEpochSeconds = createdUtc?.toLong(),
        ),
        body = commentBody,
        score = score.coerceIn(
            Int.MIN_VALUE.toLong(),
            Int.MAX_VALUE.toLong(),
        ).toInt(),
        placement = ThreadPlacement.TOP,
        community = normalizedSavedCommunityName(
            subredditNamePrefixed = subredditNamePrefixed,
            subreddit = subreddit,
        ),
        postTitle = linkTitle?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun normalizedSavedPostFullname(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return if (normalized.startsWith("t3_")) normalized else "t3_$normalized"
}

private fun normalizedSavedCommentFullname(
    name: String?,
    id: String,
): String? {
    val raw = name?.trim()?.takeIf(String::isNotEmpty)
        ?: id.trim().takeIf(String::isNotEmpty)
        ?: return null
    val identifier = raw
        .lowercase(Locale.ROOT)
        .removePrefix("t1_")
        .takeIf { candidate ->
            candidate.isNotEmpty() &&
                candidate.length <= 32 &&
                candidate.all(Char::isLetterOrDigit)
        }
        ?: return null
    return "t1_$identifier"
}

private fun normalizedSavedParentPostId(value: String?): String? {
    val identifier = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.removePrefix("t3_")
        ?.takeIf { candidate ->
            candidate.isNotEmpty() &&
                candidate.length <= 32 &&
                candidate.all(Char::isLetterOrDigit)
        }
        ?: return null
    return identifier
}

private fun normalizedSavedCommunityName(
    subredditNamePrefixed: String?,
    subreddit: String?,
): String? =
    sequenceOf(subredditNamePrefixed, subreddit)
        .mapNotNull { candidate ->
            candidate
                ?.trim()
                ?.removePrefix("/r/")
                ?.removePrefix("r/")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }
        .firstOrNull()

private const val REDDIT_SAVED_POST_KIND = "t3"
private const val REDDIT_SAVED_COMMENT_KIND = "t1"
