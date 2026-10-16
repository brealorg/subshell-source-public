package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.CommunityDiscoverySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditCommunityDiscoveryNetworkDataSourceTest {
    @Test
    fun popularRequestUsesActivityListingWithoutSearchParameters() {
        val request = redditCommunityDiscoveryJsonRequest(
            searchQuery = null,
            searchSort = null,
            autocompleteIncludeOver18 = null,
            cursor = "t5_after",
            limit = 50,
        )

        assertEquals(listOf("subreddits", "popular"), request.pathSegments)
        assertEquals(
            mapOf(
                "raw_json" to "1",
                "limit" to "50",
                "after" to "t5_after",
            ),
            request.queryParameters.toMap(),
        )
    }

    @Test
    fun manualSearchUsesRelevanceAndExcludesUserProfiles() {
        val request = redditCommunityDiscoveryJsonRequest(
            searchQuery = "android development",
            searchSort = CommunityDiscoverySort.RELEVANCE,
            autocompleteIncludeOver18 = null,
            cursor = null,
            limit = 25,
        )

        assertEquals(listOf("subreddits", "search"), request.pathSegments)
        assertEquals("android development", request.queryParameters.toMap()["q"])
        assertEquals("relevance", request.queryParameters.toMap()["sort"])
        assertEquals("false", request.queryParameters.toMap()["show_users"])
        assertEquals("true", request.queryParameters.toMap()["sr_detail"])
    }

    @Test
    fun topicSearchUsesActivitySort() {
        val request = redditCommunityDiscoveryJsonRequest(
            searchQuery = "Technology",
            searchSort = CommunityDiscoverySort.ACTIVITY,
            autocompleteIncludeOver18 = null,
            cursor = null,
            limit = 25,
        )

        assertEquals("activity", request.queryParameters.toMap()["sort"])
    }

    @Test
    fun autocompleteUsesTypeaheadEndpointBoundedSuggestionsAndNsfwPolicy() {
        val request = redditCommunityDiscoveryJsonRequest(
            searchQuery = "ted",
            searchSort = null,
            autocompleteIncludeOver18 = false,
            cursor = null,
            limit = 8,
        )

        assertEquals(
            listOf("api", "subreddit_autocomplete_v2"),
            request.pathSegments,
        )
        assertEquals(
            mapOf(
                "raw_json" to "1",
                "limit" to "8",
                "query" to "ted",
                "include_over_18" to "false",
                "include_profiles" to "false",
                "typeahead_active" to "true",
            ),
            request.queryParameters.toMap(),
        )
    }

    @Test
    fun autocompleteRejectsListingCursorAndLimitAboveTen() {
        assertTrue(
            runCatching {
                redditCommunityDiscoveryJsonRequest(
                    searchQuery = "ted",
                    searchSort = null,
                    autocompleteIncludeOver18 = false,
                    cursor = "t5_not_supported",
                    limit = 8,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                redditCommunityDiscoveryJsonRequest(
                    searchQuery = "ted",
                    searchSort = null,
                    autocompleteIncludeOver18 = false,
                    cursor = null,
                    limit = 11,
                )
            }.isFailure,
        )
    }

    @Test
    fun listingMapsMetadataNsfwAndOnlyValidCommunityThings() {
        val page = RedditCommunityDiscoveryListingDto(
            data = RedditCommunityDiscoveryListingDataDto(
                after = "t5_next",
                children = listOf(
                    RedditCommunityDiscoveryThingDto(
                        kind = "t5",
                        data = RedditCommunityDiscoveryDataDto(
                            id = "android",
                            name = "t5_android",
                            displayName = "Android",
                            publicDescription = "Android news and development",
                            subscribers = 1_234_567L,
                            communityIcon =
                                "https://styles.redditmedia.com/icon.png?x=1&amp;y=2",
                            primaryColor = "#123456",
                            over18 = true,
                        ),
                    ),
                    null,
                    RedditCommunityDiscoveryThingDto(
                        kind = "t3",
                        data = RedditCommunityDiscoveryDataDto(
                            displayName = "not_a_subreddit",
                        ),
                    ),
                    RedditCommunityDiscoveryThingDto(
                        kind = "t5",
                        data = null,
                    ),
                    RedditCommunityDiscoveryThingDto(
                        kind = "t5",
                        data = RedditCommunityDiscoveryDataDto(displayName = ""),
                    ),
                ),
            ),
        ).toNetworkPage()

        assertEquals("t5_next", page.nextCursor)
        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("t5_android", item.community.id)
        assertEquals("Android", item.community.name)
        assertEquals("1.2m", item.community.members)
        assertEquals(0xFF123456L, item.community.accent)
        assertEquals(
            "https://styles.redditmedia.com/icon.png?x=1&y=2",
            item.community.iconImageUri,
        )
        assertTrue(item.nsfw)
        assertFalse(item.community.joined)
    }

    @Test
    fun missingListingDataMapsToAnEmptyPageInsteadOfFailingTheResponse() {
        val page = RedditCommunityDiscoveryListingDto(data = null).toNetworkPage()

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }
}
