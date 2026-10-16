package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Search01CFederatedProvidersPolicyTest {
    @Test
    fun authenticatedGlobalSearchDefaultsToFederatedAllProviders() {
        val authenticated = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_search_c1",
            username = "searcher",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + authenticated,
            initialActiveAccountId = authenticated.stableId,
        )

        state.openSearch(refocus = false)

        assertEquals(SearchArea.ALL_SUBSHELL, state.searchArea)
        assertEquals(SearchScope.ALL, state.searchScope)
        assertEquals(
            listOf(
                SearchScope.ALL,
                SearchScope.POSTS,
                SearchScope.COMMUNITIES,
                SearchScope.USERS,
            ),
            state.availableSearchScopes,
        )
    }

    @Test
    fun allProviderScopeReusesTheCanonicalLiveRedditPostQueryIdentity() {
        val authenticated = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_search_c1",
            username = "searcher",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )

        val allKey = searchPostFeedQueryKey(
            account = authenticated,
            destination = MainDestination.HOME,
            originCommunity = null,
            query = "android",
            scope = SearchScope.ALL,
            area = SearchArea.ALL_SUBSHELL,
        )
        val postsKey = searchPostFeedQueryKey(
            account = authenticated,
            destination = MainDestination.HOME,
            originCommunity = null,
            query = "android",
            scope = SearchScope.POSTS,
            area = SearchArea.ALL_SUBSHELL,
        )

        assertEquals(postsKey, allKey)
        assertTrue(allKey.value.contains("live05|search"))
    }

    @Test
    fun currentCommunitySearchRemainsPostOnly() {
        val state = SubshellState()
        state.openSubreddit("android")
        state.openSearch(refocus = false)

        assertEquals(SearchArea.CURRENT_COMMUNITY, state.searchArea)
        assertEquals(SearchScope.POSTS, state.searchScope)
        assertEquals(listOf(SearchScope.POSTS), state.availableSearchScopes)
    }
}
