package app.subshell.prototype.ui

import app.subshell.prototype.data.REDDIT_SEARCH_QUERY_MAX_LENGTH
import java.util.Locale

// ISSUE206_SAVED_SEARCHES_V1 (#206): searches a reader chose to keep, per
// signed-in account (keyed on the account's stable id), newest first. Search
// has no sort or time period of its own, so a saved search is the query plus
// what it searched: scope, area and, for a community search, the community.
// Portable: settings backup module "savedSearches" (schema v10).

internal const val SAVED_SEARCHES_PER_ACCOUNT = 50

private const val SAVED_SEARCHES_KEY_PREFIX = "saved_searches:"
private val SAVED_SEARCH_COMMUNITY_PATTERN = Regex("^[A-Za-z0-9_]{2,21}$")

internal fun savedSearchesPreferenceKey(accountStableId: String): String =
    SAVED_SEARCHES_KEY_PREFIX + accountStableId

data class SavedSearch(
    val query: String,
    val scope: SearchScope,
    val area: SearchArea,
    /** The community searched, only for [SearchArea.CURRENT_COMMUNITY]. */
    val community: String? = null,
)

/** Two saved searches are the same search when they would ask Reddit the same. */
internal fun SavedSearch.identityKey(): String = listOf(
    query.lowercase(Locale.ROOT),
    scope.name,
    area.name,
    community?.lowercase(Locale.ROOT).orEmpty(),
).joinToString("\u0000")

/** A search that can run as stored, or null (blank query, community search without a community). */
internal fun normalizedSavedSearchOrNull(search: SavedSearch): SavedSearch? {
    val query = normalizeSearchQuery(search.query).take(REDDIT_SEARCH_QUERY_MAX_LENGTH).trim()
    if (query.isEmpty()) return null
    return when (search.area) {
        SearchArea.CURRENT_COMMUNITY -> {
            val community = search.community
                ?.trim()
                ?.removePrefix("/")
                ?.removePrefix("r/")
                ?.takeIf(SAVED_SEARCH_COMMUNITY_PATTERN::matches)
                ?: return null
            SavedSearch(query, SearchScope.POSTS, SearchArea.CURRENT_COMMUNITY, community)
        }
        SearchArea.ALL_SUBSHELL,
        SearchArea.MY_COMMUNITIES,
        -> SavedSearch(query, search.scope, search.area, community = null)
    }
}

/** Valid, distinct, newest first, at most [SAVED_SEARCHES_PER_ACCOUNT]. */
internal fun normalizeSavedSearches(searches: List<SavedSearch>): List<SavedSearch> =
    searches
        .mapNotNull(::normalizedSavedSearchOrNull)
        .distinctBy(SavedSearch::identityKey)
        .take(SAVED_SEARCHES_PER_ACCOUNT)

/** "Posts · r/norge", "All · All Reddit": what a saved search searches. */
internal fun SavedSearch.scopeSummary(): String {
    val where = when (area) {
        SearchArea.CURRENT_COMMUNITY -> "r/$community"
        SearchArea.MY_COMMUNITIES -> "My communities"
        SearchArea.ALL_SUBSHELL -> "All Reddit"
    }
    return "${scope.label} · $where"
}
