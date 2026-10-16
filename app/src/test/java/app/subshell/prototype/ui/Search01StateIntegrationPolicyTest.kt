package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Search01StateIntegrationPolicyTest {
    @Test
    fun statePersistsAccountScopedHistoryAndKeepsDraftOffSubmittedResults() {
        var persistedAccount: String? = null
        var persistedHistory: List<String> = emptyList()
        val state = SubshellState(
            loadSearchHistory = { listOf("loaded query") },
            persistSearchHistory = { accountStableId, history ->
                persistedAccount = accountStableId
                persistedHistory = history
            },
        )

        assertEquals(listOf("loaded query"), state.recentSearches)
        state.openSearch(refocus = false)
        state.updateSearchQuery("first result")
        assertEquals("first result", state.searchQuery)
        assertEquals("", state.searchSubmittedQuery)
        assertTrue(state.searchHasUnsubmittedChanges)

        assertEquals("first result", state.submitSearch())
        assertEquals("first result", state.searchSubmittedQuery)
        assertFalse(state.searchHasUnsubmittedChanges)
        assertEquals(state.activeAccountId, persistedAccount)
        assertEquals(listOf("first result", "loaded query"), persistedHistory)

        state.updateSearchQuery("next draft")
        val route = state.currentNavigationRoute as SubshellRoute.Search
        assertEquals("next draft", route.query)
        assertEquals("first result", route.submittedQuery)
        assertEquals("first result", state.searchSubmittedQuery)
        assertTrue(state.searchHasUnsubmittedChanges)

        state.clearRecentSearches()
        assertEquals(emptyList<String>(), persistedHistory)
    }

    @Test
    fun processSnapshotRestoresDraftAndSubmittedSearchIdentity() {
        val state = SubshellState()
        state.openSearch(refocus = false)
        state.updateSearchQuery("submitted search")
        state.submitSearch()
        state.updateSearchQuery("editing draft")

        val restored = SubshellState(
            initialProcessSnapshotToken = encodeSubshellProcessSnapshot(
                state.processStateSnapshot(),
            ),
        )

        assertTrue(restored.searchVisible)
        assertEquals("editing draft", restored.searchQuery)
        assertEquals("submitted search", restored.searchSubmittedQuery)
        assertTrue(restored.searchHasUnsubmittedChanges)
    }
    @Test
    fun accountSwitchReloadsOnlyTheSelectedAccountsDeviceLocalHistory() {
        val accounts = app.subshell.prototype.data.account.localFixtureAccountSummaries(
            listOf("root", "shelluser"),
        )
        val historyByAccount = mutableMapOf(
            accounts[0].stableId to listOf("root history"),
            accounts[1].stableId to listOf("shell history"),
        )
        val state = SubshellState(
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts[0].stableId,
            loadSearchHistory = { accountStableId ->
                historyByAccount[accountStableId].orEmpty()
            },
            persistSearchHistory = { accountStableId, history ->
                historyByAccount[accountStableId] = history
            },
        )

        assertEquals(listOf("root history"), state.recentSearches)
        state.openSearch(refocus = false)
        state.updateSearchQuery("root submitted")
        state.submitSearch()
        assertEquals(
            listOf("root submitted", "root history"),
            historyByAccount[accounts[0].stableId],
        )

        state.switchAccount(accounts[1].stableId)

        assertEquals(accounts[1].stableId, state.activeAccountId)
        assertEquals(listOf("shell history"), state.recentSearches)
        assertEquals("", state.searchQuery)
        assertEquals("", state.searchSubmittedQuery)
        assertEquals(
            listOf("root submitted", "root history"),
            historyByAccount[accounts[0].stableId],
        )
    }

}
