package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.search.GoogleTrendsSearchDiscoveryLoader
import app.subshell.prototype.data.search.TrendingSearchCache
import app.subshell.prototype.data.search.TrendingSearchSeed
import app.subshell.prototype.data.search.decodeTrendingSearchCache
import app.subshell.prototype.data.search.encodeTrendingSearchCache
import app.subshell.prototype.data.search.freshTrendingSearchSeeds
import app.subshell.prototype.ui.SearchIndexEntry
import app.subshell.prototype.ui.SearchIndexKind
import app.subshell.prototype.ui.normalizeSearchHistory
import app.subshell.prototype.ui.normalizeSearchIndex
import app.subshell.prototype.ui.recordSearchIndexSelection
import app.subshell.prototype.ui.searchIndexBoost

/**
 * Search history state: the device-local recent searches and the search
 * interaction index, both per account, and the global trending seed cache.
 * Extracted verbatim from SubshellState, which holds one instance and
 * delegates to it; the account-switch path reloads both lists through
 * [reloadForAccount], and submission records its query through
 * [recordSubmittedSearch]. The search route fields and session stay in
 * SubshellState with the navigation. Behaviour is unchanged.
 */
internal class SearchHistoryState(
    private val loadSearchHistory: (String) -> List<String>,
    private val persistSearchHistory: (String, List<String>) -> Unit,
    private val loadSearchIndex: (String) -> List<SearchIndexEntry>,
    private val persistSearchIndex: (String, List<SearchIndexEntry>) -> Unit,
    loadTrendingSearchCache: () -> String,
    private val persistTrendingSearchCache: (String) -> Unit,
    private val currentTimeMillis: () -> Long,
    private val activeAccountIdProvider: () -> String,
    private val loadTrendingSearchSeeds: suspend () -> List<TrendingSearchSeed> =
        { GoogleTrendsSearchDiscoveryLoader.load() },
) {
    // LOWUI5_TRENDING_RETRY_WAITS_AFTER_A_FRUITLESS_FETCH_V1 (OCB-290): an empty
    // or failed trending read is not repeated on every empty search; the next
    // try waits TRENDING_SEARCH_RETRY_AFTER_MS.
    private var trendingSearchAttemptAtMs: Long? = null

    private val activeAccountId: String
        get() = activeAccountIdProvider()

    val recentSearches = mutableStateListOf<String>().apply {
        addAll(normalizeSearchHistory(loadSearchHistory(activeAccountId)))
    }
    // SEARCH02A1_ACCOUNT_SCOPED_INTERACTION_INDEX
    val searchIndexEntries = mutableStateListOf<SearchIndexEntry>().apply {
        addAll(normalizeSearchIndex(loadSearchIndex(activeAccountId)))
    }
    // SEARCH02A12_DISCOVERY_SEARCH_HUB
    private var trendingSearchCache by mutableStateOf(
        decodeTrendingSearchCache(loadTrendingSearchCache()),
    )
    var trendingSearchRefreshInProgress by mutableStateOf(false)
        private set
    internal val trendingSearchSeeds: List<TrendingSearchSeed>
        get() = freshTrendingSearchSeeds(
            cache = trendingSearchCache,
            nowEpochMs = currentTimeMillis(),
        )

    /** Reloads both per-account lists in place for the selected account. */
    fun reloadForAccount(accountStableId: String) {
        recentSearches.clear()
        recentSearches.addAll(normalizeSearchHistory(loadSearchHistory(accountStableId)))
        searchIndexEntries.clear()
        searchIndexEntries.addAll(normalizeSearchIndex(loadSearchIndex(accountStableId)))
    }

    /** Puts a submitted query in front of the history and persists it. */
    fun recordSubmittedSearch(submittedQuery: String) {
        val normalizedHistory = normalizeSearchHistory(
            listOf(submittedQuery) + recentSearches,
        )
        recentSearches.clear()
        recentSearches.addAll(normalizedHistory)
        persistSearchHistory(activeAccountId, normalizedHistory)
    }

    fun clearRecentSearches() {
        if (recentSearches.isEmpty()) return
        recentSearches.clear()
        persistSearchHistory(activeAccountId, emptyList())
    }

    internal fun searchIndexBoostFor(
        kind: SearchIndexKind,
        key: String,
    ): Int = searchIndexBoost(
        entries = searchIndexEntries,
        kind = kind,
        key = key,
        nowEpochMs = currentTimeMillis(),
    )

    internal fun recentSearchIndexEntries(
        kind: SearchIndexKind,
        limit: Int,
    ): List<SearchIndexEntry> = searchIndexEntries
        .asSequence()
        .filter { entry -> entry.kind == kind }
        .take(limit.coerceAtLeast(0))
        .toList()

    internal fun recordSearchSelection(
        kind: SearchIndexKind,
        key: String,
        primaryText: String,
        secondaryText: String = "",
        imageUri: String? = null,
    ) {
        val updated = recordSearchIndexSelection(
            entries = searchIndexEntries,
            kind = kind,
            key = key,
            primaryText = primaryText,
            secondaryText = secondaryText,
            imageUri = imageUri,
            selectedAtEpochMs = currentTimeMillis(),
        )
        if (updated == searchIndexEntries) return
        searchIndexEntries.clear()
        searchIndexEntries.addAll(updated)
        persistSearchIndex(activeAccountId, updated)
    }

    internal fun clearSearchIndex(kind: SearchIndexKind? = null) {
        val retained = if (kind == null) {
            emptyList()
        } else {
            searchIndexEntries.filterNot { entry -> entry.kind == kind }
        }
        if (retained.size == searchIndexEntries.size) return
        searchIndexEntries.clear()
        searchIndexEntries.addAll(retained)
        persistSearchIndex(activeAccountId, retained)
    }

    internal suspend fun refreshTrendingSearchSeedsIfStale() {
        if (trendingSearchSeeds.isNotEmpty() || trendingSearchRefreshInProgress) return
        val now = currentTimeMillis()
        trendingSearchAttemptAtMs?.let { attemptAt ->
            if (now - attemptAt in 0 until TRENDING_SEARCH_RETRY_AFTER_MS) return
        }
        trendingSearchAttemptAtMs = now
        trendingSearchRefreshInProgress = true
        try {
            val seeds = loadTrendingSearchSeeds()
            if (seeds.isEmpty()) return
            val updated = TrendingSearchCache(
                fetchedAtEpochMs = currentTimeMillis(),
                seeds = seeds,
            )
            trendingSearchCache = updated
            trendingSearchAttemptAtMs = null
            persistTrendingSearchCache(encodeTrendingSearchCache(updated))
        } finally {
            trendingSearchRefreshInProgress = false
        }
    }
}

internal const val TRENDING_SEARCH_RETRY_AFTER_MS = 30L * 60L * 1000L
