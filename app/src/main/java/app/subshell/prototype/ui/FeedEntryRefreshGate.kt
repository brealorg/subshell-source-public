package app.subshell.prototype.ui

import app.subshell.prototype.data.RepositoryQueryKey

/**
 * Decides whether entering an authenticated Home/community feed surface may
 * issue an automatic authoritative refresh (issue #17).
 *
 * The decision is owned by state, not by the composable: a surface leaves
 * composition every time a post is opened, so a composition-scoped gate
 * re-fires on every return and collapses the paged list to the first page.
 *
 * ISSUE40_REFRESH_ON_PAGER_CREATION_ONLY_V1: like Paging 3's
 * `RemoteMediator.initialize()`, which runs only when a Pager is created, a
 * query key is refreshed automatically once, on its first entry in this
 * process (or after [reset], i.e. an account switch). Every later entry keeps
 * the stored list, and freshness within a session is the reader's
 * pull-to-refresh. An automatic refresh rewrites the query to its first page,
 * so a later one threw a reader past that page back up mid-scroll (issue #40).
 *
 * ISSUE40_NO_AUTOMATIC_REFRESH_DEEP_IN_LIST_V1: a first entry whose restored
 * position is already past the first page is deferred and not recorded. The
 * next entry near the top refreshes.
 */
internal class FeedEntryRefreshGate {
    private val claimedQueryKeys = mutableSetOf<RepositoryQueryKey>()

    fun claim(
        queryKey: RepositoryQueryKey,
        deepInList: Boolean = false,
    ): FeedEntryRefreshDecision {
        val decision = when {
            queryKey in claimedQueryKeys -> FeedEntryRefreshDecision.SKIP_REENTRY
            deepInList -> FeedEntryRefreshDecision.DEFER_DEEP
            else -> FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY
        }
        if (decision.execute) claimedQueryKeys += queryKey
        return decision
    }

    fun reset() {
        claimedQueryKeys.clear()
    }
}

internal enum class FeedEntryRefreshDecision(val execute: Boolean, val logToken: String) {
    EXECUTE_FIRST_ENTRY(execute = true, logToken = "execute_first_entry"),
    SKIP_REENTRY(execute = false, logToken = "skip_reentry"),
    DEFER_DEEP(execute = false, logToken = "defer_deep"),
}

/**
 * A feed position at or past this list index is deeper than the first page
 * an automatic refresh leaves behind (Reddit listings return 25 posts).
 */
internal const val FEED_AUTOMATIC_REFRESH_DEEP_INDEX = 25

internal fun FeedScrollAnchor.isDeepForAutomaticRefresh(): Boolean =
    fallbackIndex >= FEED_AUTOMATIC_REFRESH_DEEP_INDEX
