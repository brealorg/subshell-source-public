package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.screens.SEARCH01C2B_ALL_COMMUNITY_PREVIEW_LIMIT
import app.subshell.prototype.ui.screens.SEARCH01C2B_ALL_PEOPLE_PREVIEW_LIMIT
import app.subshell.prototype.ui.screens.shouldLoadLivePeopleDraftSuggestions
import app.subshell.prototype.ui.screens.shouldShowSearchDraftSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Search01C2LivePeoplePolicyTest {
    @Test
    fun authenticatedGlobalSearchExposesPeopleAsFederatedScope() {
        val authenticated = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_search_c2",
            username = "searcher",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions =
                localFixtureAccountSummaries(listOf("root")) + authenticated,
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
        assertTrue(state.searchAreaDescription(SearchArea.ALL_SUBSHELL).contains("people"))
    }


    @Test
    fun authenticatedDraftPeopleSuggestionsAreGlobalTypeaheadOnly() {
        assertTrue(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "spez",
                scope = SearchScope.ALL,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = false,
            ),
        )
        assertTrue(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "subshell_dev",
                scope = SearchScope.USERS,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = false,
            ),
        )
        assertFalse(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "spez",
                scope = SearchScope.POSTS,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = false,
            ),
        )
        assertFalse(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "spez",
                scope = SearchScope.COMMUNITIES,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = false,
            ),
        )
        assertFalse(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "spez",
                scope = SearchScope.ALL,
                area = SearchArea.CURRENT_COMMUNITY,
                hasDirectAction = false,
            ),
        )
        assertFalse(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "u/spez",
                scope = SearchScope.ALL,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = true,
            ),
        )
        assertFalse(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = false,
                query = "spez",
                scope = SearchScope.ALL,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = false,
            ),
        )
        assertTrue(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "x".repeat(25),
                scope = SearchScope.ALL,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = false,
            ),
        )
        assertFalse(
            shouldLoadLivePeopleDraftSuggestions(
                authenticated = true,
                query = "x".repeat(26),
                scope = SearchScope.ALL,
                area = SearchArea.ALL_SUBSHELL,
                hasDirectAction = false,
            ),
        )
    }

    @Test
    fun submittedQueryDoesNotRenderDraftSuggestionsAgain() {
        assertTrue(
            shouldShowSearchDraftSuggestions(
                draftQuery = "spez",
                submittedQuery = "",
                hasUnsubmittedChanges = false,
            ),
        )
        assertTrue(
            shouldShowSearchDraftSuggestions(
                draftQuery = "spez",
                submittedQuery = "brealorg",
                hasUnsubmittedChanges = true,
            ),
        )
        assertFalse(
            shouldShowSearchDraftSuggestions(
                draftQuery = "spez",
                submittedQuery = "spez",
                hasUnsubmittedChanges = false,
            ),
        )
        assertFalse(
            shouldShowSearchDraftSuggestions(
                draftQuery = "s",
                submittedQuery = "",
                hasUnsubmittedChanges = false,
            ),
        )
    }

    @Test
    fun federatedAllPreviewIsBoundedAndSubmitPreservesScope() {
        assertEquals(3, SEARCH01C2B_ALL_COMMUNITY_PREVIEW_LIMIT)
        assertEquals(2, SEARCH01C2B_ALL_PEOPLE_PREVIEW_LIMIT)

        val authenticated = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_search_c2b",
            username = "searcher",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions =
                localFixtureAccountSummaries(listOf("root")) + authenticated,
            initialActiveAccountId = authenticated.stableId,
        )
        state.openSearch(refocus = false)
        state.updateSearchQuery("spez")

        assertEquals("spez", state.submitSearch())
        assertEquals(SearchScope.ALL, state.searchScope)
    }

    @Test
    fun currentCommunitySearchRemainsPostsOnlyAfterPeopleProviderLands() {
        val state = SubshellState()
        state.openSubreddit("android")
        state.openSearch(refocus = false)

        assertEquals(SearchArea.CURRENT_COMMUNITY, state.searchArea)
        assertEquals(listOf(SearchScope.POSTS), state.availableSearchScopes)
        assertFalse(SearchScope.USERS in state.availableSearchScopes)
    }

    @Test
    fun explicitUserTargetStillUsesCanonicalDirectResolver() {
        val plan = planSearchQuery(
            rawQuery = "u/spez",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )

        assertEquals(listOf(SearchProviderKind.DIRECT), plan.providers)
        assertTrue(plan.directAction?.target is RedditTarget.User)
    }
}
