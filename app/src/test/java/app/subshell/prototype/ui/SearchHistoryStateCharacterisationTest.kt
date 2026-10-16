package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.search.TrendingSearchCache
import app.subshell.prototype.data.search.TrendingSearchSeed
import app.subshell.prototype.data.search.encodeTrendingSearchCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the device-local search history, the account-scoped
 * search interaction index and the trending seed cache inside SubshellState
 * as they behave today, written before they move to SearchHistoryState.
 * Current behaviour, not a specification. Submission and the account
 * switch are already covered by Search01/Search02A1 integration tests.
 */
class SearchHistoryStateCharacterisationTest {
    private val accounts = localFixtureAccountSummaries(listOf("root", "shelluser"))
    private val historyByAccount = mutableMapOf<String, List<String>>()
    private val indexByAccount = mutableMapOf<String, List<SearchIndexEntry>>()
    private val historyWrites = mutableListOf<String>()
    private val indexWrites = mutableListOf<String>()
    private var trendingToken = ""
    private var now = 1_700_000_000_000L

    @Test
    fun historyIsOneListDedupedOnSubmitAndClearedOnlyWhenNonEmpty() {
        val state = state()
        val list = state.recentSearches
        assertTrue(list.isEmpty())

        state.openSearch(refocus = false)
        state.updateSearchQuery("  Kotlin ")
        assertEquals("Kotlin", state.submitSearch())
        state.updateSearchQuery("compose")
        state.submitSearch()
        // Case-insensitive dedupe keeps the newest spelling in front.
        state.updateSearchQuery("KOTLIN")
        state.submitSearch()
        assertSame(list, state.recentSearches)
        assertEquals(listOf("KOTLIN", "compose"), state.recentSearches.toList())
        assertEquals(listOf("KOTLIN", "compose"), historyByAccount[accounts[0].stableId])
        assertEquals(3, historyWrites.size)

        state.clearRecentSearches()
        assertTrue(state.recentSearches.isEmpty())
        assertEquals(emptyList<String>(), historyByAccount[accounts[0].stableId])
        assertEquals(4, historyWrites.size)

        // Clearing an empty history persists nothing.
        state.clearRecentSearches()
        assertEquals(4, historyWrites.size)
    }

    @Test
    fun indexRecordsBoostsListsByKindAndPersistsOnlyOnChange() {
        val state = state()
        val list = state.searchIndexEntries
        assertEquals(0, state.searchIndexBoostFor(SearchIndexKind.COMMUNITY, "homelab"))

        state.recordSearchSelection(SearchIndexKind.COMMUNITY, "HomeLab", "r/homelab")
        assertSame(list, state.searchIndexEntries)
        assertEquals(1, state.searchIndexEntries.size)
        assertEquals(1, indexWrites.size)
        val entry = state.searchIndexEntries.single()
        assertEquals(SearchIndexKind.COMMUNITY, entry.kind)
        assertEquals(1, entry.selectionCount)
        assertEquals(now, entry.lastSelectedEpochMs)
        val firstBoost = state.searchIndexBoostFor(SearchIndexKind.COMMUNITY, "homelab")
        assertTrue(firstBoost > 0)

        now += 1_000L
        state.recordSearchSelection(SearchIndexKind.COMMUNITY, "homelab", "r/homelab")
        assertEquals(2, state.searchIndexEntries.single().selectionCount)
        assertTrue(state.searchIndexBoostFor(SearchIndexKind.COMMUNITY, "homelab") > firstBoost)
        assertEquals(2, indexWrites.size)

        // A blank key changes nothing and persists nothing.
        state.recordSearchSelection(SearchIndexKind.USER, "   ", "someone")
        assertEquals(2, indexWrites.size)

        // Same timestamp: ordered by primary text; a later timestamp wins.
        state.recordSearchSelection(SearchIndexKind.USER, "bob", "u/bob")
        state.recordSearchSelection(SearchIndexKind.USER, "alice", "u/alice")
        assertEquals(
            listOf("alice", "bob"),
            state.recentSearchIndexEntries(SearchIndexKind.USER, limit = 5).map { it.key },
        )
        now += 1_000L
        state.recordSearchSelection(SearchIndexKind.USER, "bob", "u/bob")
        assertEquals(
            listOf("bob", "alice"),
            state.recentSearchIndexEntries(SearchIndexKind.USER, limit = 5).map { it.key },
        )
        assertEquals(
            listOf("bob"),
            state.recentSearchIndexEntries(SearchIndexKind.USER, limit = 1).map { it.key },
        )
        assertTrue(state.recentSearchIndexEntries(SearchIndexKind.USER, limit = -1).isEmpty())
        assertEquals(5, indexWrites.size)

        state.clearSearchIndex(SearchIndexKind.USER)
        assertEquals(listOf(SearchIndexKind.COMMUNITY), state.searchIndexEntries.map { it.kind })
        assertEquals(6, indexWrites.size)
        state.clearSearchIndex(SearchIndexKind.USER)
        assertEquals(6, indexWrites.size)
        state.clearSearchIndex()
        assertTrue(state.searchIndexEntries.isEmpty())
        assertEquals(7, indexWrites.size)
        assertEquals(emptyList<SearchIndexEntry>(), indexByAccount[accounts[0].stableId])
    }

    @Test
    fun sessionSynchronisationReloadsHistoryAndIndexForTheSelectedAccountIntoTheSameLists() {
        historyByAccount[accounts[0].stableId] = listOf("root query")
        historyByAccount[accounts[1].stableId] = listOf("shell query")
        indexByAccount[accounts[1].stableId] = listOf(
            SearchIndexEntry(SearchIndexKind.COMMUNITY, "shellsub", "r/shellsub", lastSelectedEpochMs = 5L),
        )
        val state = state()
        val history = state.recentSearches
        val index = state.searchIndexEntries
        assertEquals(listOf("root query"), history.toList())
        assertTrue(index.isEmpty())

        state.synchronizeAccountSessions(AccountSessionDirectorySnapshot(accounts, accounts[1].stableId))
        assertSame(history, state.recentSearches)
        assertSame(index, state.searchIndexEntries)
        assertEquals(listOf("shell query"), state.recentSearches.toList())
        assertEquals(listOf("shellsub"), state.searchIndexEntries.map { it.key })
        assertTrue(historyWrites.isEmpty() && indexWrites.isEmpty())

        // Re-synchronising the same active account is a no-op.
        state.recordSearchSelection(SearchIndexKind.USER, "carol", "u/carol")
        state.synchronizeAccountSessions(AccountSessionDirectorySnapshot(accounts, accounts[1].stableId))
        assertEquals(listOf("carol", "shellsub"), state.searchIndexEntries.map { it.key })
    }

    @Test
    fun trendingSeedsComeFromAFreshCacheAndARefreshIsSkippedWhileSeedsAreShown() {
        trendingToken = encodeTrendingSearchCache(
            TrendingSearchCache(
                fetchedAtEpochMs = now - 60_000L,
                seeds = listOf(TrendingSearchSeed("Kotlin 2.3"), TrendingSearchSeed("Compose")),
            ),
        )
        val state = state()
        assertEquals(listOf("Kotlin 2.3", "Compose"), state.trendingSearchSeeds.map { it.title })
        assertFalse(state.trendingSearchRefreshInProgress)

        // Seeds present: no refresh, no network, no persistence.
        runBlocking { state.refreshTrendingSearchSeedsIfStale() }
        assertFalse(state.trendingSearchRefreshInProgress)
        assertEquals(listOf("Kotlin 2.3", "Compose"), state.trendingSearchSeeds.map { it.title })

        // Past the TTL the cache yields nothing.
        now += 31L * 60L * 1_000L
        assertTrue(state.trendingSearchSeeds.isEmpty())

        val empty = state(token = "")
        assertTrue(empty.trendingSearchSeeds.isEmpty())
        val malformed = state(token = "not a cache")
        assertTrue(malformed.trendingSearchSeeds.isEmpty())
    }

    private fun state(token: String = trendingToken): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = accounts,
        initialActiveAccountId = accounts[0].stableId,
        loadSearchHistory = { account -> historyByAccount[account].orEmpty() },
        persistSearchHistory = { account, history ->
            historyWrites += account
            historyByAccount[account] = history
        },
        loadSearchIndex = { account -> indexByAccount[account].orEmpty() },
        persistSearchIndex = { account, entries ->
            indexWrites += account
            indexByAccount[account] = entries
        },
        loadTrendingSearchCache = { token },
        persistTrendingSearchCache = { trendingToken = it },
        currentTimeMillis = { now },
    )
}
