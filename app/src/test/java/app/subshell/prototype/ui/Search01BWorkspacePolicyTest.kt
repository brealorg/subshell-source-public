package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Search01BWorkspacePolicyTest {
    @Test
    fun editedDraftBackRestoresTheSubmittedResultIdentity() {
        val state = SubshellState()
        state.openSearch(refocus = false)
        state.updateSearchQuery("android")
        state.submitSearch()
        state.updateSearchQuery("android battery")

        assertEquals(
            SearchBackAction.RESTORE_SUBMITTED_QUERY,
            resolveSearchBackAction(
                draftQuery = state.searchQuery,
                submittedQuery = state.searchSubmittedQuery,
            ),
        )
        assertTrue(state.navigateBack())
        assertEquals("android", state.searchQuery)
        assertEquals("android", state.searchSubmittedQuery)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)
    }

    @Test
    fun submittedResultsBackReturnsToHubBeforeLeavingSearch() {
        val state = SubshellState()
        state.openSearch(refocus = false)
        state.updateSearchQuery("tromsø")
        state.submitSearch()

        assertEquals(
            SearchBackAction.CLEAR_TO_SEARCH_HUB,
            resolveSearchBackAction(
                draftQuery = state.searchQuery,
                submittedQuery = state.searchSubmittedQuery,
            ),
        )
        assertTrue(state.navigateBack())
        assertEquals("", state.searchQuery)
        assertEquals("", state.searchSubmittedQuery)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)

        assertEquals(
            SearchBackAction.CLOSE_SEARCH,
            resolveSearchBackAction(
                draftQuery = state.searchQuery,
                submittedQuery = state.searchSubmittedQuery,
            ),
        )
        assertTrue(state.navigateBack())
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
    }

    @Test
    fun searchChromeBackUsesTheSameInternalBackPolicy() {
        val state = SubshellState()
        state.openSearch(refocus = false)
        state.updateSearchQuery("volvo")
        state.submitSearch()

        state.handleSearchBack()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)
        assertEquals("", state.searchQuery)
        assertEquals("", state.searchSubmittedQuery)

        state.handleSearchBack()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
    }
}
