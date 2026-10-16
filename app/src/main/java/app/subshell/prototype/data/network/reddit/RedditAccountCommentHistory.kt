package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// COMMENTREADBACK01_ACCOUNT_HISTORY_ABSENCE_V1 (#123): a thread listing is
// truncated (limit, depth, sort, unopened "more" nodes), so a comment missing
// from it is not proof that it was never posted. The account's own newest
// comments are: once they reach back before the attempt, every comment the
// account made since then is in them.

internal data class RedditAccountCommentCandidate(
    val author: String,
    val parentFullname: String,
    val createdUtcEpochSeconds: Long,
)

/** reachesEnd = Reddit has no older page, so the whole history is here. */
internal data class RedditAccountCommentHistory(
    val candidates: List<RedditAccountCommentCandidate>,
    val reachesEnd: Boolean,
)

@Serializable
internal data class RedditAccountCommentHistoryDto(
    val data: RedditAccountCommentHistoryDataDto = RedditAccountCommentHistoryDataDto(),
)

@Serializable
internal data class RedditAccountCommentHistoryDataDto(
    val after: String? = null,
    val children: List<RedditAccountCommentHistoryThingDto> = emptyList(),
)

@Serializable
internal data class RedditAccountCommentHistoryThingDto(
    val kind: String? = null,
    val data: RedditAccountCommentHistoryCommentDto = RedditAccountCommentHistoryCommentDto(),
)

@Serializable
internal data class RedditAccountCommentHistoryCommentDto(
    val author: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("created_utc") val createdUtc: Double? = null,
)

internal suspend fun readRedditAccountRecentComments(
    httpClient: SubshellHttpClient,
    username: String,
    limit: Int = REDDIT_ACCOUNT_COMMENT_HISTORY_LIMIT,
): RedditAccountCommentHistory? {
    val name = username.trim().removePrefix("/").removePrefix("u/").removePrefix("U/")
        .takeIf { value ->
            value.length in 1..100 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        }
        ?: return null
    val result = httpClient.getJson(
        request = JsonGetRequest(
            pathSegments = listOf("user", name, "comments"),
            queryParameters = listOf(
                "raw_json" to "1",
                "sort" to "new",
                "limit" to limit.coerceIn(1, 100).toString(),
            ),
        ),
        deserializer = RedditAccountCommentHistoryDto.serializer(),
    )
    if (result !is NetworkResult.Success) return null
    return result.value.toAccountCommentHistory()
}

internal fun RedditAccountCommentHistoryDto.toAccountCommentHistory(): RedditAccountCommentHistory =
    RedditAccountCommentHistory(
        candidates = data.children.mapNotNull { child ->
            if (child.kind != null && child.kind != "t1") return@mapNotNull null
            val comment = child.data
            RedditAccountCommentCandidate(
                author = comment.author?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null,
                parentFullname = comment.parentId?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null,
                createdUtcEpochSeconds = comment.createdUtc
                    ?.takeIf { value -> value.isFinite() && value >= 0.0 }
                    ?.toLong()
                    ?: return@mapNotNull null,
            )
        },
        reachesEnd = data.after == null,
    )

/**
 * True only when the history proves the attempt is absent: it reaches back
 * before [earliestCreatedEpochSeconds], and no comment of [username] under
 * [parentFullname] was made since then, whatever its text (Reddit and
 * communities may change a body). Anything that could be the attempt keeps it
 * unresolved.
 */
internal fun redditCommentHistoryShowsNoAttempt(
    username: String,
    parentFullname: String,
    earliestCreatedEpochSeconds: Long,
    history: RedditAccountCommentHistory,
): Boolean {
    val author = username.trim().takeIf(String::isNotEmpty) ?: return false
    val parent = parentFullname.trim().lowercase().takeIf(String::isNotEmpty) ?: return false
    val covered = history.reachesEnd ||
        history.candidates.any { candidate -> candidate.createdUtcEpochSeconds < earliestCreatedEpochSeconds }
    if (!covered) return false
    return history.candidates.none { candidate ->
        candidate.createdUtcEpochSeconds >= earliestCreatedEpochSeconds &&
            candidate.author.equals(author, ignoreCase = true) &&
            candidate.parentFullname == parent
    }
}

internal const val REDDIT_ACCOUNT_COMMENT_HISTORY_LIMIT = 100
