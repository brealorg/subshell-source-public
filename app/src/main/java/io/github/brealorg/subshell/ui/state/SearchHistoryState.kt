package io.github.brealorg.subshell.ui.state

import androidx.compose.runtime.mutableStateListOf
import io.github.brealorg.subshell.ui.SearchIndexEntry
import io.github.brealorg.subshell.ui.SearchIndexKind
import io.github.brealorg.subshell.ui.normalizeSearchHistory
import io.github.brealorg.subshell.ui.normalizeSearchIndex
import io.github.brealorg.subshell.ui.recordSearchIndexSelection
import io.github.brealorg.subshell.ui.searchIndexBoost

/**
 * Search history state: the device-local recent searches and the search
 * interaction index, both per account.
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
    private val currentTimeMillis: () -> Long,
    private val activeAccountIdProvider: () -> String,
) {
    private val activeAccountId: String
        get() = activeAccountIdProvider()

    val recentSearches = mutableStateListOf<String>().apply {
        addAll(normalizeSearchHistory(loadSearchHistory(activeAccountId)))
    }
    // SEARCH02A1_ACCOUNT_SCOPED_INTERACTION_INDEX
    val searchIndexEntries = mutableStateListOf<SearchIndexEntry>().apply {
        addAll(normalizeSearchIndex(loadSearchIndex(activeAccountId)))
    }
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
}
