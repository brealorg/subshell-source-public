package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.normalizeRedditUsername
import io.github.brealorg.subshell.data.network.JsonGetRequest
import io.github.brealorg.subshell.data.network.NetworkPage
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.map
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.ProfileCommentFixture

internal class RedditAccountHistoryNetworkDataSource(
    private val httpClient: SubshellHttpClient,
    private val clock: EpochSecondsClock = SystemEpochSecondsClock,
) {
    suspend fun posts(
        username: String,
        kind: RedditAccountHistoryKind,
        request: RepositoryPageRequest,
    ): NetworkResult<NetworkPage<Post>> {
        require(kind.isPostHistory) {
            "Post history transport cannot load a comment history kind"
        }
        return httpClient.getJson(
            request = redditAccountHistoryJsonRequest(
                username = username,
                kind = kind,
                request = request,
            ),
            deserializer = RedditReadListingDto.serializer(),
        ).map { listing -> listing.toNetworkPage(SubshellNetworkJson.providerJson, clock.epochSeconds()) }
    }

    suspend fun comments(
        username: String,
        kind: RedditAccountHistoryKind,
        request: RepositoryPageRequest,
    ): NetworkResult<NetworkPage<ProfileCommentFixture>> {
        require(!kind.isPostHistory) {
            "Comment history transport cannot load a post history kind"
        }
        return httpClient.getJson(
            request = redditAccountHistoryJsonRequest(
                username = username,
                kind = kind,
                request = request,
            ),
            deserializer = RedditProfileCommentsListingDto.serializer(),
        ).map { listing ->
            listing.toProfileCommentsPage(
                nowEpochSeconds = clock.epochSeconds(),
                limit = request.limit,
            )
        }
    }

    suspend fun saved(
        username: String,
        request: RepositoryPageRequest,
    ): NetworkResult<NetworkPage<RedditSavedHistoryItem>> =
        httpClient.getJson(
            request = redditMixedSavedHistoryJsonRequest(
                username = username,
                request = request,
            ),
            deserializer = RedditSavedListingDto.serializer(),
        ).map { listing ->
            listing.toSavedHistoryPage(
                json = SubshellNetworkJson.providerJson,
                nowEpochSeconds = clock.epochSeconds(),
            )
        }
}

internal fun redditAccountHistoryJsonRequest(
    username: String,
    kind: RedditAccountHistoryKind,
    request: RepositoryPageRequest = RepositoryPageRequest(),
): JsonGetRequest {
    require(request.limit in 1..RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT) {
        "Reddit account history limit must be between 1 and " +
            RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT
    }
    val normalizedUsername = normalizeRedditUsername(username)
    return JsonGetRequest(
        pathSegments = listOf(
            "user",
            normalizedUsername,
            kind.pathSegment,
        ),
        queryParameters = buildList {
            add("raw_json" to "1")
            add("sort" to "new")
            add("limit" to request.limit.toString())
            kind.typeFilter?.let { type -> add("type" to type) }
            request.cursor
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { cursor -> add("after" to cursor) }
        },
    )
}

internal fun redditMixedSavedHistoryJsonRequest(
    username: String,
    request: RepositoryPageRequest = RepositoryPageRequest(),
): JsonGetRequest {
    require(request.limit in 1..RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT) {
        "Reddit mixed saved limit must be between 1 and " +
            RedditListingNetworkDataSource.REDDIT_MAX_LISTING_LIMIT
    }
    val normalizedUsername = normalizeRedditUsername(username)
    return JsonGetRequest(
        pathSegments = listOf(
            "user",
            normalizedUsername,
            "saved",
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
