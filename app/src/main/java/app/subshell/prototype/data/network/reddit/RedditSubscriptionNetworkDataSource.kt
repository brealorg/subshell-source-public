package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.Community
import kotlinx.serialization.Serializable

internal interface RedditSubscriptionPageSource {
    suspend fun page(
        cursor: String?,
        limit: Int,
    ): NetworkResult<NetworkPage<RedditSubscribedCommunity>>
}

// REDDITFAV01_SUBSCRIPTIONS_CARRY_REDDIT_FAVORITES_V1: each subscribed
// community with Reddit's own favorite mark (user_has_favorited,
// device-verified 2026-10-05). The mark is not part of Community, so the
// cached Community codec is unchanged.
internal data class RedditSubscribedCommunity(
    val community: Community,
    val favorite: Boolean,
)

internal class RedditSubscriptionNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditSubscriptionPageSource {
    override suspend fun page(
        cursor: String?,
        limit: Int,
    ): NetworkResult<NetworkPage<RedditSubscribedCommunity>> = httpClient.getJson(
        request = redditSubscriptionsJsonRequest(cursor, limit),
        deserializer = RedditSubscriptionListingDto.serializer(),
    ).map(RedditSubscriptionListingDto::toNetworkPage)
}

@Serializable
internal data class RedditSubscriptionListingDto(
    val data: RedditSubscriptionListingDataDto = RedditSubscriptionListingDataDto(),
)

@Serializable
internal data class RedditSubscriptionListingDataDto(
    val after: String? = null,
    val children: List<RedditSubscriptionThingDto> = emptyList(),
)

@Serializable
internal data class RedditSubscriptionThingDto(
    val kind: String? = null,
    val data: RedditCommunityAboutDataDto = RedditCommunityAboutDataDto(),
)

internal fun RedditSubscriptionListingDto.toNetworkPage(): NetworkPage<RedditSubscribedCommunity> = NetworkPage(
    items = data.children.mapNotNull { thing ->
        if (thing.kind != null && thing.kind != REDDIT_COMMUNITY_KIND) {
            return@mapNotNull null
        }
        if (thing.data.subredditType.equals("user", ignoreCase = true)) {
            return@mapNotNull null
        }
        val displayName = thing.data.displayName
            ?.normalizedRedditCommunityName()
            ?: return@mapNotNull null
        val stableId = thing.data.name
            ?.trim()
            ?.takeIf { name -> name.startsWith("t5_") }
            ?: thing.data.id
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { id -> "t5_$id" }
            ?: "reddit-community:${displayName.lowercase()}"
        RedditSubscribedCommunity(
            community = thing.data.toCommunity(
                fallback = Community(
                    id = stableId,
                    name = displayName,
                    description = "",
                    members = "—",
                    accent = DEFAULT_REDDIT_COMMUNITY_ACCENT,
                    joined = true,
                ),
            ),
            favorite = thing.data.userHasFavorited == true,
        )
    },
    nextCursor = data.after?.trim()?.takeIf(String::isNotEmpty),
)

internal fun redditSubscriptionsJsonRequest(
    cursor: String?,
    limit: Int,
): JsonGetRequest {
    require(limit in 1..REDDIT_SUBSCRIPTIONS_MAX_PAGE_SIZE) {
        "Reddit subscriptions limit must be between 1 and $REDDIT_SUBSCRIPTIONS_MAX_PAGE_SIZE"
    }
    return JsonGetRequest(
        pathSegments = listOf("subreddits", "mine", "subscriber"),
        queryParameters = buildList {
            add("raw_json" to "1")
            add("limit" to limit.toString())
            cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { after -> add("after" to after) }
        },
    )
}

internal const val REDDIT_SUBSCRIPTIONS_MAX_PAGE_SIZE = 100
private const val REDDIT_COMMUNITY_KIND = "t5"
private const val DEFAULT_REDDIT_COMMUNITY_ACCENT = 0xFF6750A4L
