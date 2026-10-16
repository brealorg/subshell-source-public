package app.subshell.prototype.ui

import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Search02A1StateIntegrationPolicyTest {
    @Test
    fun statePersistsSelectionsAndReloadsOnlyTheActiveAccountsIndex() {
        val accounts = localFixtureAccountSummaries(listOf("root", "shelluser"))
        val indexByAccount = mutableMapOf(
            accounts[0].stableId to listOf(
                SearchIndexEntry(
                    kind = SearchIndexKind.USER,
                    key = "root-user",
                    primaryText = "root-user",
                    selectionCount = 2,
                    lastSelectedEpochMs = 100L,
                ),
            ),
            accounts[1].stableId to listOf(
                SearchIndexEntry(
                    kind = SearchIndexKind.USER,
                    key = "shell-user",
                    primaryText = "shell-user",
                    selectionCount = 3,
                    lastSelectedEpochMs = 200L,
                ),
            ),
        )
        var now = 1_000L
        val state = SubshellState(
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts[0].stableId,
            loadSearchIndex = { accountStableId ->
                indexByAccount[accountStableId].orEmpty()
            },
            persistSearchIndex = { accountStableId, entries ->
                indexByAccount[accountStableId] = entries
            },
            currentTimeMillis = { now },
        )

        assertEquals("root-user", state.searchIndexEntries.single().key)
        state.recordSearchSelection(
            kind = SearchIndexKind.USER,
            key = "u/New_User",
            primaryText = "New_User",
            secondaryText = "New profile",
        )

        assertEquals("new_user", state.searchIndexEntries.first().key)
        assertEquals(
            state.searchIndexEntries.toList(),
            indexByAccount[accounts[0].stableId],
        )
        assertTrue(state.searchIndexBoostFor(SearchIndexKind.USER, "new_user") > 0)

        now = 2_000L
        state.switchAccount(accounts[1].stableId)

        assertEquals(accounts[1].stableId, state.activeAccountId)
        assertEquals("shell-user", state.searchIndexEntries.single().key)
        assertEquals("new_user", indexByAccount[accounts[0].stableId]?.first()?.key)
    }

    @Test
    fun clearingOneKindPreservesOtherIndexedTargets() {
        var persisted: List<SearchIndexEntry> = emptyList()
        val state = SubshellState(
            loadSearchIndex = {
                listOf(
                    SearchIndexEntry(
                        kind = SearchIndexKind.USER,
                        key = "person",
                        primaryText = "person",
                        lastSelectedEpochMs = 2L,
                    ),
                    SearchIndexEntry(
                        kind = SearchIndexKind.COMMUNITY,
                        key = "community",
                        primaryText = "community",
                        lastSelectedEpochMs = 1L,
                    ),
                )
            },
            persistSearchIndex = { _, entries -> persisted = entries },
        )

        state.clearSearchIndex(SearchIndexKind.USER)

        assertEquals(1, state.searchIndexEntries.size)
        assertEquals(SearchIndexKind.COMMUNITY, state.searchIndexEntries.single().kind)
        assertEquals(state.searchIndexEntries.toList(), persisted)
    }
}
