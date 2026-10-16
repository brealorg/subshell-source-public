package app.subshell.prototype.ui

import app.subshell.prototype.data.RepositoryQueryKey

internal class ProfileHistoryEntryRefreshGate {
    private val claimedQueryKeys = mutableSetOf<RepositoryQueryKey>()

    fun claim(queryKey: RepositoryQueryKey): Boolean =
        claimedQueryKeys.add(queryKey)

    // LOW_PROFILE_HISTORY_GATE_FOLLOWS_THE_FEED_GATE_V1 (OCB-327, owner: take
    // it): an account switch drops every pager; like the feed gate, the
    // history gate starts over, so returning to a history refreshes it once.
    fun reset() {
        claimedQueryKeys.clear()
    }
}
