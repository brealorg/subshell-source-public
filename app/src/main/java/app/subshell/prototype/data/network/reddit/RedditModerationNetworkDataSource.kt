package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.REDDIT_MODERATION_AGGREGATE_QUEUE
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// MOD01A1_CLASSIC_REDDIT_MODERATOR_DIRECTORY_AND_MODQUEUE_TRANSPORT_V1
// MOD01A22_AGGREGATE_R_MOD_MODQUEUE_TRANSPORT_V1
internal interface RedditModerationSource {
    suspend fun moderatedCommunities(
        after: String? = null,
    ): NetworkResult<NetworkPage<String>>

    suspend fun modQueue(
        communityName: String,
        after: String? = null,
    ): NetworkResult<NetworkPage<RedditModeratorQueueItem>>
}

internal class RedditModerationNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditModerationSource {
    override suspend fun moderatedCommunities(
        after: String?,
    ): NetworkResult<NetworkPage<String>> =
        httpClient.getJson(
            request = redditModeratedCommunitiesRequest(after),
            deserializer = RedditModerationListingDto.serializer(),
        ).map { listing -> listing.toModeratedCommunityPage() }

    override suspend fun modQueue(
        communityName: String,
        after: String?,
    ): NetworkResult<NetworkPage<RedditModeratorQueueItem>> =
        httpClient.getJson(
            request = redditModQueueRequest(communityName, after),
            deserializer = RedditModerationListingDto.serializer(),
        ).map { listing -> listing.toModeratorQueuePage(communityName) }

    // MODERATION02A2_EXACT_REPORT_TARGET_READ_OUTSIDE_QUEUE_V1
    // ISSUE281_READS_AVOID_API_INFO_V1: read from the post's thread; the caller
    // makes sure the target has its post.
    suspend fun reportTarget(target: ModeratorReportTarget): NetworkResult<RedditModeratorQueueItem?> {
        val post = requireNotNull(target.postFullname) { "A report target is read from its post" }
        return httpClient.getJson(
            request = redditModeratorReportTargetRequest(target, post),
            deserializer = redditThreadListing(
                RedditModerationListingDto.serializer(),
                if (target.fullname.startsWith("t3_")) REDDIT_THREAD_POST_LISTING else REDDIT_THREAD_COMMENT_LISTING,
            ),
        ).map { listing -> listing.toModeratorReportTarget(target) }
    }
}

internal fun redditModeratedCommunitiesRequest(
    after: String? = null,
): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf("subreddits", "mine", "moderator"),
    queryParameters = buildList {
        add("limit" to MODERATOR_DIRECTORY_PAGE_LIMIT.toString())
        add("raw_json" to "1")
        after
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { cursor -> add("after" to cursor) }
    },
)

internal fun redditModQueueRequest(
    rawCommunityName: String,
    after: String? = null,
): JsonGetRequest {
    val communityName = normalizeRedditCommunityName(rawCommunityName)
    return JsonGetRequest(
        pathSegments = listOf("r", communityName, "about", "modqueue"),
        queryParameters = buildList {
            add("limit" to MODQUEUE_PAGE_LIMIT.toString())
            add("raw_json" to "1")
            after
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { cursor -> add("after" to cursor) }
        },
    )
}

@Serializable
internal data class RedditModerationListingDto(
    val data: RedditModerationListingDataDto = RedditModerationListingDataDto(),
)

@Serializable
internal data class RedditModerationListingDataDto(
    val after: String? = null,
    val children: List<RedditModerationThingDto?> = emptyList(),
)

@Serializable
internal data class RedditModerationThingDto(
    val kind: String? = null,
    val data: RedditModerationThingDataDto = RedditModerationThingDataDto(),
)

@Serializable
internal data class RedditModerationThingDataDto(
    val name: String? = null,
    val author: String? = null,
    val subreddit: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("num_reports") val numReports: Int? = null,
    @SerialName("user_reports") val userReports: JsonElement? = null,
    @SerialName("mod_reports") val modReports: JsonElement? = null,
    @SerialName("ignore_reports") val ignoreReports: JsonElement? = null,
    @SerialName("can_mod_post") val canModPost: JsonElement? = null,
    // MODERATION02A3_LOCK_DISTINGUISH_READBACK_FIELDS_V1
    val locked: JsonElement? = null,
    val distinguished: JsonElement = MISSING_MODERATOR_DISTINGUISHED_FIELD,
    val permalink: String? = null,
)

internal fun RedditModerationListingDto.toModeratedCommunityPage(): NetworkPage<String> {
    val items = data.children.asSequence()
        .mapNotNull { thing ->
            thing?.data?.displayName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }
        .mapNotNull { name ->
            runCatching { normalizeRedditCommunityName(name) }.getOrNull()
        }
        .distinct()
        .take(MODERATOR_DIRECTORY_PAGE_LIMIT)
        .toList()
    return NetworkPage(
        items = items,
        nextCursor = data.after
            ?.trim()
            ?.takeIf(String::isNotEmpty),
    )
}

internal fun RedditModerationListingDto.toModeratorQueuePage(
    rawCommunityName: String,
): NetworkPage<RedditModeratorQueueItem> {
    val communityName = normalizeRedditCommunityName(rawCommunityName)
    val items = data.children.asSequence()
        .mapNotNull { thing -> thing?.toModeratorQueueItem(communityName) }
        .distinctBy(RedditModeratorQueueItem::fullname)
        .take(MODQUEUE_PAGE_LIMIT)
        .toList()
    return NetworkPage(
        items = items,
        nextCursor = data.after
            ?.trim()
            ?.takeIf(String::isNotEmpty),
    )
}

private fun RedditModerationThingDto.toModeratorQueueItem(
    communityName: String,
): RedditModeratorQueueItem? {
    val aggregateQueue = communityName == REDDIT_MODERATION_AGGREGATE_QUEUE
    val kind = when (kind?.lowercase()) {
        "t3" -> RedditModeratorQueueKind.POST
        "t1" -> RedditModeratorQueueKind.COMMENT
        else -> return null
    }
    val fullname = data.name
        ?.trim()
        ?.lowercase()
        ?.takeIf { candidate -> candidate.matches(THING_FULLNAME_REGEX) }
        ?: return null
    if (fullname.length > 35) return null
    if ((kind == RedditModeratorQueueKind.POST) != fullname.startsWith("t3_")) return null
    val itemCommunity = data.subreddit
        ?.let { subreddit ->
            runCatching { normalizeRedditCommunityName(subreddit) }.getOrNull()
        }
        ?: return null
    if (!aggregateQueue && itemCommunity != communityName) return null
    val author = data.author
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(MAX_QUEUE_AUTHOR_CHARS)
        ?: "[deleted]"
    val summary = moderationQueueSummary(
        raw = when (kind) {
            RedditModeratorQueueKind.POST -> data.title
            RedditModeratorQueueKind.COMMENT -> data.body
        },
        kind = kind,
    ) ?: return null
    val reportDetails = moderatorReportDetails(
        data.numReports, data.userReports, data.modReports, data.ignoreReports, data.canModPost,
    )
    val contentState = moderatorContentState(
        locked = data.locked,
        distinguished = data.distinguished,
        canModPost = data.canModPost,
    )
    val reportCount = (reportDetails.totalCount ?: 0).coerceIn(0, MAX_QUEUE_REPORT_COUNT)
    return RedditModeratorQueueItem(
        fullname = fullname,
        kind = kind,
        communityName = itemCommunity,
        author = author,
        summary = summary,
        reportCount = reportCount,
        reportDetails = reportDetails,
        contentState = contentState,
        permalink = data.permalink
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_QUEUE_PERMALINK_CHARS),
    )
}


// MOD01A221_MEDIA_AWARE_MODQUEUE_PREVIEW_V1
internal fun moderationQueueSummary(
    raw: String?,
    kind: RedditModeratorQueueKind,
): String? {
    val normalized = raw
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf(String::isNotEmpty)
        ?: return null
    if (kind != RedditModeratorQueueKind.COMMENT) {
        return normalized.take(MAX_QUEUE_SUMMARY_CHARS)
    }

    val hasImage = REDDIT_PROCESSING_IMAGE_PLACEHOLDER.containsMatchIn(normalized) ||
        REDDIT_INLINE_IMAGE_PLACEHOLDER.containsMatchIn(normalized)
    // LOW_NET_MODQUEUE_GIF_PREVIEW_V1 (OCB-550): a Giphy placeholder
    // (![gif](giphy|id)) is shown as a GIF, not as raw markup.
    val hasGif = REDDIT_INLINE_GIF_PLACEHOLDER.containsMatchIn(normalized)
    if (!hasImage && !hasGif) return normalized.take(MAX_QUEUE_SUMMARY_CHARS)

    val text = normalized
        .replace(REDDIT_PROCESSING_IMAGE_PLACEHOLDER, " ")
        .replace(REDDIT_INLINE_IMAGE_PLACEHOLDER, " ")
        .replace(REDDIT_INLINE_GIF_PLACEHOLDER, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val preview = if (text.isEmpty()) {
        if (hasImage) "Image comment" else "GIF comment"
    } else {
        if (hasImage) "$text · Image" else "$text · GIF"
    }
    return preview.take(MAX_QUEUE_SUMMARY_CHARS)
}

private val THING_FULLNAME_REGEX = Regex("t[13]_[a-z0-9]+")
private val REDDIT_PROCESSING_IMAGE_PLACEHOLDER = Regex(
    pattern = """\*?\s*Processing\s+img\s+[A-Za-z0-9_-]+(?:\.\.\.)?\s*\*?""",
    option = RegexOption.IGNORE_CASE,
)
private val REDDIT_INLINE_IMAGE_PLACEHOLDER = Regex(
    pattern = """!\[(?:img|image)]\([^\r\n)]{1,512}\)""",
    option = RegexOption.IGNORE_CASE,
)
private val REDDIT_INLINE_GIF_PLACEHOLDER = Regex(
    pattern = """!\[gif]\([^\r\n)]{1,512}\)""",
    option = RegexOption.IGNORE_CASE,
)
private const val MODERATOR_DIRECTORY_PAGE_LIMIT = 100
private const val MODQUEUE_PAGE_LIMIT = 50
private const val MAX_QUEUE_AUTHOR_CHARS = 100
private const val MAX_QUEUE_SUMMARY_CHARS = 600
private const val MAX_QUEUE_PERMALINK_CHARS = 2_000
private const val MAX_QUEUE_REPORT_COUNT = 10_000


internal fun redditModeratorReportTargetRequest(target: ModeratorReportTarget, postFullname: String): JsonGetRequest =
    if (target.fullname.startsWith("t3_")) {
        redditPostReadRequest(postFullname)
    } else {
        redditCommentReadRequest(postFullname, target.fullname)
    }

internal fun RedditModerationListingDto.toModeratorReportTarget(
    target: ModeratorReportTarget,
): RedditModeratorQueueItem? {
    // A lookup must resolve exactly one requested thing, not the first nearby post.
    val thing = data.children.singleOrNull() ?: return null
    val item = thing.toModeratorQueueItem(REDDIT_MODERATION_AGGREGATE_QUEUE) ?: return null
    if (item.fullname != target.fullname) return null
    if (target.communityName != null && item.communityName != target.communityName) return null
    return item
}
