package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.auth.reddit.RedditSubscriptionList
import app.subshell.prototype.data.auth.reddit.loadAllRedditSubscriptions
import app.subshell.prototype.data.auth.reddit.redditFavoriteFormRequest
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * REDDITFAV01_SUBSCRIPTIONS_CARRY_REDDIT_FAVORITES_V1: Reddit's
 * user_has_favorited is read from the subscriptions listing (true only when
 * Reddit says so) and collected across pages; REDDITFAV02 sends api/favorite.
 */
class RedditFav01SubscriptionFavoritesTest {
    @Test
    fun listingReadsRedditsFavoriteMark() {
        val page = SubshellNetworkJson.providerJson.decodeFromString(
            RedditSubscriptionListingDto.serializer(),
            """
            {"kind":"Listing","data":{"after":null,"children":[
              {"kind":"t5","data":{"name":"t5_a","display_name":"norge","user_has_favorited":true}},
              {"kind":"t5","data":{"name":"t5_b","display_name":"Android","user_has_favorited":false}},
              {"kind":"t5","data":{"name":"t5_c","display_name":"space"}}
            ]}}
            """.trimIndent(),
        ).toNetworkPage()

        assertEquals(
            listOf("norge" to true, "Android" to false, "space" to false),
            page.items.map { item -> item.community.name to item.favorite },
        )
    }

    @Test
    fun favoritesAreCollectedAcrossPages() = runBlocking {
        val pages = mutableListOf(
            page(listOf("norge" to true, "space" to false), "t5_next"),
            page(listOf("subshell" to true), null),
        )
        val source = object : RedditSubscriptionPageSource {
            override suspend fun page(cursor: String?, limit: Int) = pages.removeAt(0)
        }

        val result = loadAllRedditSubscriptions(source) as NetworkResult.Success<RedditSubscriptionList>

        assertEquals(listOf("norge", "space", "subshell"), result.value.communities.map { it.name })
        assertEquals(setOf("norge", "subshell"), result.value.favoriteCommunityNames)
    }

    @Test
    fun favoriteRequestNamesTheCommunityAndTheState() {
        val request = redditFavoriteFormRequest("r/Android", makeFavorite = false)

        assertEquals(listOf("api", "favorite"), request.pathSegments)
        assertEquals(
            listOf("make_favorite" to "false", "sr_name" to "android", "api_type" to "json"),
            request.fields,
        )
    }

    private fun page(
        names: List<Pair<String, Boolean>>,
        next: String?,
    ): NetworkResult<NetworkPage<RedditSubscribedCommunity>> = NetworkResult.Success(
        value = NetworkPage(
            names.map { (name, favorite) ->
                RedditSubscribedCommunity(
                    community = app.subshell.prototype.model.Community(
                        id = "t5_$name",
                        name = name,
                        description = "",
                        members = "—",
                        accent = 0L,
                        joined = true,
                    ),
                    favorite = favorite,
                )
            },
            next,
        ),
        metadata = NetworkResponseMetadata(
            statusCode = 200,
            receivedAtEpochMs = 1L,
            rateLimit = NetworkRateLimit(null, null, null),
            retryAfterMs = null,
        ),
    )

    @Test
    fun customFeedFavoriteRequestUsesTheMultipath() {
        val request = app.subshell.prototype.data.auth.reddit.redditCustomFeedFavoriteFormRequest(
            "/user/brealorg/m/baesj/",
            makeFavorite = true,
        )

        assertEquals(listOf("api", "multi", "favorite"), request.pathSegments)
        assertEquals(
            listOf("multipath" to "/user/brealorg/m/baesj/", "make_favorite" to "true", "api_type" to "json"),
            request.fields,
        )
    }
}
