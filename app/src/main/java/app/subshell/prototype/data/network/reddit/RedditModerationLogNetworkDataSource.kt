package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorLogEntry
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MODERATION02A4_CANONICAL_MOD_LOG_TRANSPORT_V1
internal class RedditModerationLogNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun page(
        communityName: String,
        after: String?,
    ): NetworkResult<NetworkPage<RedditModeratorLogEntry>> =
        httpClient.getJson(
            request = redditModerationLogRequest(communityName, after),
            deserializer = RedditModerationLogListingDto.serializer(),
        ).map { listing -> listing.toModeratorLogPage(communityName) }
}

internal fun redditModerationLogRequest(
    rawCommunityName: String,
    after: String? = null,
): JsonGetRequest {
    val communityName = normalizeRedditCommunityName(rawCommunityName)
    return JsonGetRequest(
        pathSegments = listOf("r", communityName, "about", "log"),
        queryParameters = buildList {
            add("limit" to MODERATION_LOG_PAGE_LIMIT.toString())
            add("raw_json" to "1")
            after
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { cursor -> add("after" to cursor) }
        },
    )
}

@Serializable
internal data class RedditModerationLogListingDto(
    val data: RedditModerationLogListingDataDto = RedditModerationLogListingDataDto(),
)

@Serializable
internal data class RedditModerationLogListingDataDto(
    val after: String? = null,
    val children: List<RedditModerationLogThingDto?> = emptyList(),
)

@Serializable
internal data class RedditModerationLogThingDto(
    val kind: String? = null,
    val data: RedditModerationLogDataDto = RedditModerationLogDataDto(),
)

@Serializable
internal data class RedditModerationLogDataDto(
    val id: String? = null,
    val mod: String? = null,
    val action: String? = null,
    val subreddit: String? = null,
    @SerialName("target_author") val targetAuthor: String? = null,
    @SerialName("target_fullname") val targetFullname: String? = null,
    @SerialName("target_title") val targetTitle: String? = null,
    @SerialName("target_permalink") val targetPermalink: String? = null,
    val description: String? = null,
    val details: String? = null,
    @SerialName("created_utc") val createdUtc: Double? = null,
)

internal fun RedditModerationLogListingDto.toModeratorLogPage(
    rawCommunityName: String,
): NetworkPage<RedditModeratorLogEntry> {
    val communityName = normalizeRedditCommunityName(rawCommunityName)
    val items = data.children.asSequence()
        .mapNotNull { thing -> thing?.toModeratorLogEntry(communityName) }
        .distinctBy(RedditModeratorLogEntry::id)
        .take(MODERATION_LOG_PAGE_LIMIT)
        .toList()
    return NetworkPage(
        items = items,
        nextCursor = data.after
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_MOD_ACTION_ID_CHARS),
    )
}

private fun RedditModerationLogThingDto.toModeratorLogEntry(
    requestedCommunityName: String,
): RedditModeratorLogEntry? {
    val raw = data
    val id = raw.id
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(MAX_MOD_ACTION_ID_CHARS)
        ?: return null
    val action = raw.action
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
        ?.take(MAX_MOD_ACTION_CHARS)
        ?: return null
    val responseCommunity = raw.subreddit
        ?.let { value ->
            runCatching { normalizeRedditCommunityName(value) }.getOrNull()
        }
        ?: requestedCommunityName
    if (responseCommunity != requestedCommunityName) return null

    return RedditModeratorLogEntry(
        id = id,
        communityName = responseCommunity,
        moderatorName = raw.mod
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_MOD_USERNAME_CHARS)
            ?: "reddit",
        action = action,
        targetAuthor = raw.targetAuthor.bounded(MAX_MOD_TARGET_AUTHOR_CHARS),
        targetFullname = raw.targetFullname.bounded(MAX_MOD_TARGET_FULLNAME_CHARS),
        targetTitle = raw.targetTitle.bounded(MAX_MOD_TARGET_TITLE_CHARS),
        targetPermalink = raw.targetPermalink.bounded(MAX_MOD_TARGET_PERMALINK_CHARS),
        description = raw.description.bounded(MAX_MOD_DESCRIPTION_CHARS),
        details = raw.details.bounded(MAX_MOD_DETAILS_CHARS),
        createdEpochSeconds = raw.createdUtc
            ?.toLong()
            ?.coerceAtLeast(0L)
            ?: 0L,
    )
}

private fun String?.bounded(maxChars: Int): String? =
    this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(maxChars)

private const val MODERATION_LOG_PAGE_LIMIT = 100
private const val MAX_MOD_ACTION_ID_CHARS = 160
private const val MAX_MOD_ACTION_CHARS = 96
private const val MAX_MOD_USERNAME_CHARS = 100
private const val MAX_MOD_TARGET_AUTHOR_CHARS = 100
private const val MAX_MOD_TARGET_FULLNAME_CHARS = 80
private const val MAX_MOD_TARGET_TITLE_CHARS = 2_000
private const val MAX_MOD_TARGET_PERMALINK_CHARS = 2_000
private const val MAX_MOD_DESCRIPTION_CHARS = 4_000
private const val MAX_MOD_DETAILS_CHARS = 4_000
