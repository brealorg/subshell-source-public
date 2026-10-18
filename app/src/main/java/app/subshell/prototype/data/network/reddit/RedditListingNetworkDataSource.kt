package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.normalizeRedditSearchQuery
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.normalizeRedditUsername
import kotlinx.serialization.Serializable

fun interface EpochSecondsClock {
    fun epochSeconds(): Long
}

object SystemEpochSecondsClock : EpochSecondsClock {
    override fun epochSeconds(): Long = System.currentTimeMillis() / 1_000L
}

enum class RedditListingSort(
    val pathSegment: String,
) {
    BEST("best"),
    HOT("hot"),
    NEW("new"),
    TOP("top"),
    CONTROVERSIAL("controversial"),
    RISING("rising"),
}

class RedditListingNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    private val clock: EpochSecondsClock = SystemEpochSecondsClock,
) {
    suspend fun posts(
        community: String? = null,
        sort: RedditListingSort = RedditListingSort.BEST,
        timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): NetworkResult<NetworkPage<Post>> {
        return httpClient.getJson(
            request = redditListingJsonRequest(
                community = community,
                sort = sort,
                timePeriod = timePeriod,
                request = request,
            ),
            deserializer = RedditReadListingDto.serializer(),
        ).map { listing -> listing.toNetworkPage(SubshellNetworkJson.providerJson, clock.epochSeconds()) }
    }

    suspend fun searchPosts(
        query: String,
        community: String? = null,
        request: RepositoryPageRequest = RepositoryPageRequest(),
        includeOver18: Boolean? = null,
    ): NetworkResult<NetworkPage<Post>> {
        return httpClient.getJson(
            request = redditSearchJsonRequest(
                query = query,
                community = community,
                request = request,
                includeOver18 = includeOver18,
            ),
            deserializer = RedditReadListingDto.serializer(),
        ).map { listing -> listing.toNetworkPage(SubshellNetworkJson.providerJson, clock.epochSeconds()) }
            .let { result ->
                // REDDITSEARCH01_REDIRECT_IS_NO_RESULTS_V1: Reddit answers some
                // searches (URL-shaped queries seen on device) with a redirect
                // instead of a listing. The client never follows redirects, and
                // a redirect carries no posts: it is an empty result, not a
                // failed request.
                if (result is NetworkResult.HttpFailure && result.statusCode in REDIRECT_STATUS_CODES) {
                    NetworkResult.Success(NetworkPage(items = emptyList(), nextCursor = null), result.metadata)
                } else {
                    result
                }
            }
            .let { result -> result.orCommunityNotFound(community, request) }
    }

    // REDDITSEARCH02_EMPTY_COMMUNITY_SEARCH_CHECKS_COMMUNITY_V1: an empty first
    // page of a search in one community asks Reddit whether the community
    // exists. A missing one (about answers 404, a redirect, or something other
    // than a community) becomes HTTP 404, so the UI can say so instead of "no
    // results". Any other answer, or a failed check, keeps the empty result.
    private suspend fun NetworkResult<NetworkPage<Post>>.orCommunityNotFound(
        community: String?,
        request: RepositoryPageRequest,
    ): NetworkResult<NetworkPage<Post>> {
        if (this !is NetworkResult.Success || value.items.isNotEmpty()) return this
        if (request.cursor?.trim()?.isNotEmpty() == true) return this
        val normalizedCommunity = community?.normalizeCommunityName() ?: return this
        val about = httpClient.getJson(
            request = JsonGetRequest(
                pathSegments = listOf("r", normalizedCommunity, "about"),
                queryParameters = listOf("raw_json" to "1"),
            ),
            deserializer = RedditThingKindDto.serializer(),
        )
        val missing = when (about) {
            is NetworkResult.Success -> about.value.kind != REDDIT_COMMUNITY_KIND
            is NetworkResult.HttpFailure ->
                about.statusCode == HTTP_NOT_FOUND || about.statusCode in REDIRECT_STATUS_CODES
            else -> false
        }
        return if (missing) {
            NetworkResult.HttpFailure(statusCode = HTTP_NOT_FOUND, retryable = false, metadata = metadata)
        } else {
            this
        }
    }

    suspend fun userSubmittedPosts(
        username: String,
        request: RepositoryPageRequest = RepositoryPageRequest(),
    ): NetworkResult<NetworkPage<Post>> {
        return httpClient.getJson(
            request = redditUserSubmittedJsonRequest(
                username = username,
                request = request,
            ),
            deserializer = RedditReadListingDto.serializer(),
        ).map { listing -> listing.toNetworkPage(SubshellNetworkJson.providerJson, clock.epochSeconds()) }
    }

    companion object {
        const val REDDIT_MAX_LISTING_LIMIT = 100
        private val REDIRECT_STATUS_CODES = 300..399
        private const val HTTP_NOT_FOUND = 404
        private const val REDDIT_COMMUNITY_KIND = "t5"
    }
}

@Serializable
internal data class RedditThingKindDto(
    val kind: String? = null,
)

internal fun redditListingJsonRequest(
    community: String? = null,
    sort: RedditListingSort = RedditListingSort.BEST,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
    request: RepositoryPageRequest = RepositoryPageRequest(),
): JsonGetRequest {
    require(request.limit in 1..RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT) {
        "Reddit listing limit must be between 1 and " +
            RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT
    }
    val normalizedCommunity = community?.normalizeCommunityName()
    return JsonGetRequest(
        pathSegments = buildList {
            if (normalizedCommunity != null) {
                add("r")
                add(normalizedCommunity)
            }
            add(sort.pathSegment)
        },
        queryParameters = buildList {
            add("raw_json" to "1")
            add("limit" to request.limit.toString())
            if (sort.supportsTimePeriod) {
                add("t" to timePeriod.apiValue)
            }
            request.cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { cursor -> add("after" to cursor) }
        },
    )
}

internal fun redditSearchJsonRequest(
    query: String,
    community: String? = null,
    request: RepositoryPageRequest = RepositoryPageRequest(),
    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1 (#211): "Show NSFW posts"; null
    // leaves it to the account's search preference, as before.
    includeOver18: Boolean? = null,
): JsonGetRequest {
    require(request.limit in 1..RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT) {
        "Reddit listing limit must be between 1 and " +
            RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT
    }
    val normalizedQuery = normalizeRedditSearchQuery(query)
    val normalizedCommunity = community?.normalizeCommunityName()
    return JsonGetRequest(
        pathSegments = buildList {
            if (normalizedCommunity != null) {
                add("r")
                add(normalizedCommunity)
            }
            add("search")
        },
        queryParameters = buildList {
            add("raw_json" to "1")
            add("q" to normalizedQuery)
            add("type" to "link")
            add("sort" to "relevance")
            add("t" to "all")
            if (normalizedCommunity != null) {
                add("restrict_sr" to "true")
            }
            includeOver18?.let { include -> add("include_over_18" to if (include) "on" else "off") }
            add("limit" to request.limit.toString())
            request.cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { cursor -> add("after" to cursor) }
        },
    )
}

internal fun redditUserSubmittedJsonRequest(
    username: String,
    request: RepositoryPageRequest = RepositoryPageRequest(),
): JsonGetRequest {
    require(request.limit in 1..RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT) {
        "Reddit listing limit must be between 1 and " +
            RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT
    }
    val normalizedUsername = normalizeRedditUsername(username)
    return JsonGetRequest(
        pathSegments = listOf(
            "user",
            normalizedUsername,
            "submitted",
        ),
        queryParameters = buildList {
            add("raw_json" to "1")
            add("sort" to "new")
            add("limit" to request.limit.toString())
            request.cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { cursor -> add("after" to cursor) }
        },
    )
}

private fun String.normalizeCommunityName(): String {
    val normalized = trim().removePrefix("/").removePrefix("r/")
    require(normalized.length in 1..MAX_COMMUNITY_NAME_LENGTH) {
        "Community name length is outside the supported range"
    }
    require(normalized.all { character -> character.isLetterOrDigit() || character == '_' }) {
        "Community name contains unsupported characters"
    }
    return normalized
}

private val RedditListingSort.supportsTimePeriod: Boolean
    get() = this == RedditListingSort.TOP || this == RedditListingSort.CONTROVERSIAL

private const val MAX_COMMUNITY_NAME_LENGTH = 100
