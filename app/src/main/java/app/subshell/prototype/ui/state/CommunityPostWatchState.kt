package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateListOf
import app.subshell.prototype.communityposts.MAX_WATCHED_COMMUNITIES
import app.subshell.prototype.communityposts.normalizeWatchedCommunityNames

/** What a request to watch or unwatch a community did. */
internal enum class CommunityPostWatchChange { CHANGED, UNCHANGED, LIMIT_REACHED, UNAVAILABLE }

/**
 * ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1 (#239): the communities the
 * active signed-in account gets new-post notifications from, at most
 * [MAX_WATCHED_COMMUNITIES]. SubshellState holds one instance and reloads it
 * on account switch and session sync through [reloadForAccount], next to the
 * saved searches; the background check reads the same stored list.
 */
internal class CommunityPostWatchState(
    private val loadWatched: (String) -> Set<String>,
    private val persistWatched: (String, Set<String>) -> Unit,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
    private val authenticatedAccountIdsProvider: () -> List<String>,
) {
    private val names = mutableStateListOf<String>().apply {
        addAll(loadFor(activeAccountIdProvider()))
    }

    val available: Boolean
        get() = activeAccountIsAuthenticatedProvider()

    val watched: List<String>
        get() = if (available) names else emptyList()

    fun isWatched(communityName: String): Boolean {
        val name = normalizeWatchedCommunityNames(listOf(communityName)).singleOrNull() ?: return false
        return name in watched
    }

    fun setWatched(communityName: String, watch: Boolean): CommunityPostWatchChange {
        if (!available) return CommunityPostWatchChange.UNAVAILABLE
        val name = normalizeWatchedCommunityNames(listOf(communityName)).singleOrNull()
            ?: return CommunityPostWatchChange.UNCHANGED
        if (watch == (name in names)) return CommunityPostWatchChange.UNCHANGED
        if (watch && names.size >= MAX_WATCHED_COMMUNITIES) return CommunityPostWatchChange.LIMIT_REACHED
        val updated = normalizeWatchedCommunityNames(if (watch) names + name else names - name)
        names.clear()
        names.addAll(updated)
        persistWatched(activeAccountIdProvider(), updated.toSet())
        return CommunityPostWatchChange.CHANGED
    }

    /** Every signed-in account's list: the active one from memory, the others from storage. */
    fun captureByAccount(): Map<String, Set<String>> {
        val activeId = activeAccountIdProvider()
        return authenticatedAccountIdsProvider().associateWith { accountId ->
            if (accountId == activeId) names.toSet() else loadFor(accountId).toSet()
        }
    }

    /**
     * Live-state projection of a restore whose durable commit already happened.
     * Null = module missing (unchanged); empty = every account cleared;
     * otherwise only a listed active account changes.
     */
    fun applyRestored(restoredByAccount: Map<String, Set<String>>?) {
        val restored = restoredByAccount ?: return
        val activeRestored = if (restored.isEmpty()) {
            emptySet()
        } else {
            restored[activeAccountIdProvider()] ?: return
        }
        names.clear()
        names.addAll(normalizeWatchedCommunityNames(activeRestored))
    }

    fun reloadForAccount(accountStableId: String) {
        names.clear()
        names.addAll(loadFor(accountStableId))
    }

    private fun loadFor(accountStableId: String): List<String> =
        normalizeWatchedCommunityNames(loadWatched(accountStableId))
}
