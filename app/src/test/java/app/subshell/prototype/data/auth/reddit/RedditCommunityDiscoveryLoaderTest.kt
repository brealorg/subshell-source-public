package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.auth.COMMUNITY_AUTOCOMPLETE_PAGE_SIZE
import app.subshell.prototype.data.auth.COMMUNITY_DISCOVERY_PAGE_SIZE
import app.subshell.prototype.data.auth.communityDiscoveryCacheKey
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.RedditCommunityDiscoveryPageSource
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditCommunityDiscoveryLoaderTest {
    @Test
    fun targetNormalizesSearchAndBuildsSortSpecificAccountCacheKey() {
        val relevance = AuthenticatedRedditCommunityDiscoveryTarget.search(
            rawQuery = "  Android   development ",
            sort = CommunityDiscoverySort.RELEVANCE,
        )
        val activity = AuthenticatedRedditCommunityDiscoveryTarget.search(
            rawQuery = "  Android   development ",
            sort = CommunityDiscoverySort.ACTIVITY,
        )

        assertEquals("Android development", relevance.searchQuery)
        assertEquals(
            "account|reddit:test|community-discovery|" +
                "search:relevance:android development",
            communityDiscoveryCacheKey("reddit:test", relevance),
        )
        assertNotEquals(
            communityDiscoveryCacheKey("reddit:test", relevance),
            communityDiscoveryCacheKey("reddit:test", activity),
        )
    }

    @Test
    fun autocompleteCacheKeySeparatesSafeAndNsfwSuggestionSets() {
        val safe = AuthenticatedRedditCommunityDiscoveryTarget.autocomplete(
            rawQuery = "  Ted   Lasso ",
            includeOver18 = false,
        )
        val all = AuthenticatedRedditCommunityDiscoveryTarget.autocomplete(
            rawQuery = "Ted Lasso",
            includeOver18 = true,
        )

        assertEquals("Ted Lasso", safe.searchQuery)
        assertEquals(COMMUNITY_AUTOCOMPLETE_PAGE_SIZE, safe.pageSize)
        assertEquals(
            "account|reddit:test|community-discovery|autocomplete:safe:ted lasso",
            communityDiscoveryCacheKey("reddit:test", safe),
        )
        assertNotEquals(
            communityDiscoveryCacheKey("reddit:test", safe),
            communityDiscoveryCacheKey("reddit:test", all),
        )
    }

    @Test
    fun autocompleteTargetRejectsQueriesLongerThanTwentyFiveCharacters() {
        assertTrue(
            runCatching {
                AuthenticatedRedditCommunityDiscoveryTarget.autocomplete(
                    rawQuery = "abcdefghijklmnopqrstuvwxyz",
                    includeOver18 = false,
                )
            }.isFailure,
        )
    }

    @Test
    fun oneBoundedPagePreservesRemoteOrderAndDeduplicatesNames() = runBlocking {
        val source = RecordingDiscoverySource(
            NetworkResult.Success(
                value = NetworkPage(
                    items = listOf(
                        item("one", "Android"),
                        item("two", "Kotlin"),
                        item("three", "android"),
                    ),
                    nextCursor = "t5_ignored_after_first_page",
                ),
                metadata = metadata(),
            ),
        )

        val result = loadRedditCommunityDiscovery(
            source = source,
            target = AuthenticatedRedditCommunityDiscoveryTarget.Popular,
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals(
            listOf("Android", "Kotlin"),
            (result as NetworkResult.Success<List<CommunityDiscoveryItem>>)
                .value
                .map { value -> value.community.name },
        )
        assertEquals(listOf(null), source.searchQueries)
        assertEquals(listOf(null), source.searchSorts)
        assertEquals(listOf(null), source.autocompleteOver18Values)
        assertEquals(listOf(null), source.cursors)
        assertEquals(listOf(COMMUNITY_DISCOVERY_PAGE_SIZE), source.limits)
    }

    @Test
    fun searchQueryAndSortAreForwardedToTheReadOnlySource() = runBlocking {
        val source = RecordingDiscoverySource(
            NetworkResult.Success(
                value = NetworkPage(emptyList(), null),
                metadata = metadata(),
            ),
        )

        loadRedditCommunityDiscovery(
            source = source,
            target = AuthenticatedRedditCommunityDiscoveryTarget.search(
                rawQuery = "science",
                sort = CommunityDiscoverySort.RELEVANCE,
            ),
        )

        assertEquals(listOf("science"), source.searchQueries)
        assertEquals(listOf(CommunityDiscoverySort.RELEVANCE), source.searchSorts)
        assertEquals(listOf(null), source.autocompleteOver18Values)
    }

    @Test
    fun autocompleteForwardsNsfwPolicyAndUsesEightSuggestionLimit() = runBlocking {
        val source = RecordingDiscoverySource(
            NetworkResult.Success(
                value = NetworkPage(emptyList(), null),
                metadata = metadata(),
            ),
        )

        loadRedditCommunityDiscovery(
            source = source,
            target = AuthenticatedRedditCommunityDiscoveryTarget.autocomplete(
                rawQuery = "ted",
                includeOver18 = false,
            ),
        )

        assertEquals(listOf("ted"), source.searchQueries)
        assertEquals(listOf(null), source.searchSorts)
        assertEquals(listOf(false), source.autocompleteOver18Values)
        assertEquals(listOf(COMMUNITY_AUTOCOMPLETE_PAGE_SIZE), source.limits)
    }

    private fun item(id: String, name: String): CommunityDiscoveryItem =
        CommunityDiscoveryItem(
            community = Community(
                id = id,
                name = name,
                description = "",
                members = "—",
                accent = 0xFF6750A4L,
            ),
            nsfw = false,
        )

    private fun metadata(): NetworkResponseMetadata = NetworkResponseMetadata(
        statusCode = 200,
        receivedAtEpochMs = 1L,
        rateLimit = NetworkRateLimit(null, null, null),
        retryAfterMs = null,
    )
}

private class RecordingDiscoverySource(
    private val result: NetworkResult<NetworkPage<CommunityDiscoveryItem>>,
) : RedditCommunityDiscoveryPageSource {
    val searchQueries = mutableListOf<String?>()
    val searchSorts = mutableListOf<CommunityDiscoverySort?>()
    val autocompleteOver18Values = mutableListOf<Boolean?>()
    val cursors = mutableListOf<String?>()
    val limits = mutableListOf<Int>()

    override suspend fun page(
        searchQuery: String?,
        searchSort: CommunityDiscoverySort?,
        autocompleteIncludeOver18: Boolean?,
        cursor: String?,
        limit: Int,
        listingIncludeOver18: Boolean?,
    ): NetworkResult<NetworkPage<CommunityDiscoveryItem>> {
        searchQueries += searchQuery
        searchSorts += searchSort
        autocompleteOver18Values += autocompleteIncludeOver18
        cursors += cursor
        limits += limit
        return result
    }
}
