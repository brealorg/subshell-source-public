package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal interface RedditCommunityDiscoveryPageSource {
    suspend fun page(
        searchQuery: String?,
        searchSort: CommunityDiscoverySort?,
        autocompleteIncludeOver18: Boolean?,
        cursor: String?,
        limit: Int,
        listingIncludeOver18: Boolean?,
    ): NetworkResult<NetworkPage<CommunityDiscoveryItem>>
}

internal class RedditCommunityDiscoveryNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditCommunityDiscoveryPageSource {
    override suspend fun page(
        searchQuery: String?,
        searchSort: CommunityDiscoverySort?,
        autocompleteIncludeOver18: Boolean?,
        cursor: String?,
        limit: Int,
        listingIncludeOver18: Boolean?,
    ): NetworkResult<NetworkPage<CommunityDiscoveryItem>> = httpClient.getJson(
        request = redditCommunityDiscoveryJsonRequest(
            searchQuery = searchQuery,
            searchSort = searchSort,
            autocompleteIncludeOver18 = autocompleteIncludeOver18,
            cursor = cursor,
            limit = limit,
            listingIncludeOver18 = listingIncludeOver18,
        ),
        deserializer = RedditCommunityDiscoveryListingDto.serializer(),
    ).map(RedditCommunityDiscoveryListingDto::toNetworkPage)
}

@Serializable
internal data class RedditCommunityDiscoveryListingDto(
    val data: RedditCommunityDiscoveryListingDataDto? = null,
)

@Serializable
internal data class RedditCommunityDiscoveryListingDataDto(
    val after: String? = null,
    val children: List<RedditCommunityDiscoveryThingDto?> = emptyList(),
)

@Serializable
internal data class RedditCommunityDiscoveryThingDto(
    val kind: String? = null,
    val data: RedditCommunityDiscoveryDataDto? = null,
)

@Serializable
internal data class RedditCommunityDiscoveryDataDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("display_name_prefixed") val displayNamePrefixed: String? = null,
    @SerialName("public_description") val publicDescription: String? = null,
    val subscribers: Long? = null,
    @SerialName("community_icon") val communityIcon: String? = null,
    @SerialName("icon_img") val iconImage: String? = null,
    @SerialName("banner_background_image") val bannerBackgroundImage: String? = null,
    @SerialName("banner_img") val bannerImage: String? = null,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("key_color") val keyColor: String? = null,
    val over18: Boolean? = null,
    @SerialName("over_18") val legacyOver18: Boolean? = null,
)

internal fun RedditCommunityDiscoveryListingDto.toNetworkPage():
    NetworkPage<CommunityDiscoveryItem> = NetworkPage(
    items = data?.children.orEmpty().mapNotNull { thing ->
        val value = thing ?: return@mapNotNull null
        if (value.kind != null && value.kind != REDDIT_COMMUNITY_KIND) {
            return@mapNotNull null
        }
        value.data?.toDiscoveryItem()
    },
    nextCursor = data?.after?.trim()?.takeIf(String::isNotEmpty),
)

internal fun redditCommunityDiscoveryJsonRequest(
    searchQuery: String?,
    searchSort: CommunityDiscoverySort?,
    autocompleteIncludeOver18: Boolean?,
    cursor: String?,
    limit: Int,
    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1: Popular and full search only.
    listingIncludeOver18: Boolean? = null,
): JsonGetRequest {
    val normalizedQuery = searchQuery?.trim()?.takeIf(String::isNotEmpty)
    val autocomplete = autocompleteIncludeOver18 != null

    when {
        autocomplete -> {
            require(searchSort == null) {
                "Reddit community autocomplete cannot include a listing sort"
            }
            require(normalizedQuery != null) {
                "Reddit community autocomplete requires a query"
            }
            require(cursor.isNullOrBlank()) {
                "Reddit community autocomplete does not support listing cursors"
            }
            require(limit in 1..REDDIT_COMMUNITY_AUTOCOMPLETE_MAX_PAGE_SIZE) {
                "Reddit community autocomplete limit must be between 1 and " +
                    "$REDDIT_COMMUNITY_AUTOCOMPLETE_MAX_PAGE_SIZE"
            }
            require(
                normalizedQuery.length in
                    REDDIT_COMMUNITY_SEARCH_MIN_QUERY_LENGTH..COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH,
            ) {
                "Reddit community autocomplete query must contain between " +
                    "$REDDIT_COMMUNITY_SEARCH_MIN_QUERY_LENGTH and " +
                    "$COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH characters"
            }
        }
        normalizedQuery == null -> {
            require(searchSort == null) {
                "Reddit popular community discovery cannot include a search sort"
            }
            require(limit in 1..REDDIT_COMMUNITY_DISCOVERY_MAX_PAGE_SIZE) {
                "Reddit community discovery limit must be between 1 and " +
                    "$REDDIT_COMMUNITY_DISCOVERY_MAX_PAGE_SIZE"
            }
        }
        else -> {
            require(searchSort != null) {
                "Reddit full community search requires a sort"
            }
            require(limit in 1..REDDIT_COMMUNITY_DISCOVERY_MAX_PAGE_SIZE) {
                "Reddit community discovery limit must be between 1 and " +
                    "$REDDIT_COMMUNITY_DISCOVERY_MAX_PAGE_SIZE"
            }
            require(
                normalizedQuery.length in
                    REDDIT_COMMUNITY_SEARCH_MIN_QUERY_LENGTH..REDDIT_COMMUNITY_SEARCH_MAX_QUERY_LENGTH,
            ) {
                "Reddit community search query must contain between " +
                    "$REDDIT_COMMUNITY_SEARCH_MIN_QUERY_LENGTH and " +
                    "$REDDIT_COMMUNITY_SEARCH_MAX_QUERY_LENGTH characters"
            }
        }
    }

    return JsonGetRequest(
        pathSegments = when {
            autocomplete -> listOf("api", "subreddit_autocomplete_v2")
            normalizedQuery == null -> listOf("subreddits", "popular")
            else -> listOf("subreddits", "search")
        },
        queryParameters = buildList {
            add("raw_json" to "1")
            add("limit" to limit.toString())
            when {
                autocomplete -> {
                    add("query" to requireNotNull(normalizedQuery))
                    add("include_over_18" to requireNotNull(autocompleteIncludeOver18).toString())
                    add("include_profiles" to "false")
                    add("typeahead_active" to "true")
                }
                normalizedQuery != null -> {
                    add("q" to normalizedQuery)
                    add("sort" to requireNotNull(searchSort).queryValue)
                    add("show_users" to "false")
                    add("sr_detail" to "true")
                    listingIncludeOver18?.let { include -> add("include_over_18" to if (include) "on" else "off") }
                    cursor
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { after -> add("after" to after) }
                }
                // DEAD03_COMMUNITY_SEARCH_PAGES_WITH_AFTER_V1 (OCB-546): full
                // search pages with `after` like the popular listing, instead of
                // silently dropping the cursor it returns.
                else -> {
                    listingIncludeOver18?.let { include -> add("include_over_18" to if (include) "on" else "off") }
                    cursor
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { after -> add("after" to after) }
                }
            }
        },
    )
}

private fun RedditCommunityDiscoveryDataDto.toDiscoveryItem(): CommunityDiscoveryItem? {
    val normalizedName = displayName
        ?.normalizedRedditCommunityName()
        ?: displayNamePrefixed?.normalizedRedditCommunityName()
        ?: return null
    val stableId = name
        ?.trim()
        ?.takeIf { value -> value.startsWith("t5_") }
        ?: id
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { value -> "t5_$value" }
        ?: "reddit-community:${normalizedName.lowercase()}"
    return CommunityDiscoveryItem(
        community = Community(
            id = stableId,
            name = normalizedName,
            description = publicDescription?.trim().orEmpty(),
            members = subscribers.toCommunityMemberLabel(),
            accent = (primaryColor ?: keyColor).toCommunityAccent(),
            joined = false,
            iconImageUri = sequenceOf(communityIcon, iconImage)
                .mapNotNull { candidate -> candidate.secureRedditImageUrlOrNull() }
                .firstOrNull(),
            bannerImageUri = sequenceOf(bannerBackgroundImage, bannerImage)
                .mapNotNull { candidate -> candidate.secureRedditImageUrlOrNull() }
                .firstOrNull(),
        ),
        nsfw = over18 == true || legacyOver18 == true,
    )
}

private fun Long?.toCommunityMemberLabel(): String {
    val count = this?.coerceAtLeast(0L) ?: return "—"
    return when {
        count >= 1_000_000_000L -> compactCount(count, 1_000_000_000L, "b")
        count >= 1_000_000L -> compactCount(count, 1_000_000L, "m")
        count >= 1_000L -> compactCount(count, 1_000L, "k")
        else -> count.toString()
    }
}

private fun compactCount(value: Long, divisor: Long, suffix: String): String {
    val tenths = (value * 10L) / divisor
    val whole = tenths / 10L
    val fraction = tenths % 10L
    return if (fraction == 0L) "$whole$suffix" else "$whole.$fraction$suffix"
}

private fun String?.toCommunityAccent(): Long {
    val raw = this?.trim()?.removePrefix("#") ?: return DEFAULT_DISCOVERY_ACCENT
    if (raw.length != 6 || raw.any { character -> character.digitToIntOrNull(16) == null }) {
        return DEFAULT_DISCOVERY_ACCENT
    }
    return 0xFF000000L or raw.toLong(16)
}

private fun String?.secureRedditImageUrlOrNull(): String? {
    val candidate = this
        ?.replace("&amp;", "&")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val parsed = candidate.toHttpUrlOrNull() ?: return null
    return candidate.takeIf {
        parsed.isHttps &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty()
    }
}

internal const val REDDIT_COMMUNITY_DISCOVERY_MAX_PAGE_SIZE = 100
internal const val REDDIT_COMMUNITY_AUTOCOMPLETE_MAX_PAGE_SIZE = 10
private const val REDDIT_COMMUNITY_SEARCH_MIN_QUERY_LENGTH = 2
private const val REDDIT_COMMUNITY_SEARCH_MAX_QUERY_LENGTH = 100
private const val REDDIT_COMMUNITY_KIND = "t5"
private const val DEFAULT_DISCOVERY_ACCENT = 0xFF6750A4L
