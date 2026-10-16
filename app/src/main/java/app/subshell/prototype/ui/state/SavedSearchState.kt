package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateListOf
import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.ui.identityKey
import app.subshell.prototype.ui.normalizeSavedSearches
import app.subshell.prototype.ui.normalizedSavedSearchOrNull

/**
 * ISSUE206_SAVED_SEARCHES_V1 (#206): the active account's saved searches,
 * newest first. Only signed-in Reddit accounts keep them. SubshellState holds
 * one instance and reloads it on account switch and session sync through
 * [reloadForAccount], next to the user tags.
 */
internal class SavedSearchState(
    private val loadSavedSearches: (String) -> List<SavedSearch>,
    private val persistSavedSearches: (String, List<SavedSearch>) -> Unit,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
    private val authenticatedAccountIdsProvider: () -> List<String>,
) {
    private val searches = mutableStateListOf<SavedSearch>().apply {
        addAll(loadFor(activeAccountIdProvider()))
    }

    val available: Boolean
        get() = activeAccountIsAuthenticatedProvider()

    val saved: List<SavedSearch>
        get() = if (available) searches else emptyList()

    fun isSaved(search: SavedSearch): Boolean {
        val key = normalizedSavedSearchOrNull(search)?.identityKey() ?: return false
        return saved.any { it.identityKey() == key }
    }

    /** Puts [search] first (moving it if it is already saved). */
    fun save(search: SavedSearch) {
        if (!available) return
        val normalized = normalizedSavedSearchOrNull(search) ?: return
        val updated = normalizeSavedSearches(
            listOf(normalized) + searches.filterNot { it.identityKey() == normalized.identityKey() },
        )
        if (updated == searches.toList()) return
        searches.clear()
        searches.addAll(updated)
        persistSavedSearches(activeAccountIdProvider(), updated)
    }

    fun remove(search: SavedSearch) {
        if (!available) return
        val key = normalizedSavedSearchOrNull(search)?.identityKey() ?: return
        if (searches.none { it.identityKey() == key }) return
        searches.removeAll { it.identityKey() == key }
        persistSavedSearches(activeAccountIdProvider(), searches.toList())
    }

    /** Every signed-in account's saved searches: the active one from memory, the others from storage. */
    fun captureByAccount(): Map<String, List<SavedSearch>> {
        val activeId = activeAccountIdProvider()
        return authenticatedAccountIdsProvider().associateWith { accountId ->
            if (accountId == activeId) searches.toList() else loadFor(accountId)
        }
    }

    /**
     * Live-state projection of a restore whose durable commit already happened.
     * Null = module missing (unchanged); empty = every account cleared;
     * otherwise only a listed active account changes.
     */
    fun applyRestored(restoredByAccount: Map<String, List<SavedSearch>>?) {
        val restored = restoredByAccount ?: return
        val activeRestored = if (restored.isEmpty()) {
            emptyList()
        } else {
            restored[activeAccountIdProvider()] ?: return
        }
        searches.clear()
        searches.addAll(normalizeSavedSearches(activeRestored))
    }

    fun reloadForAccount(accountStableId: String) {
        searches.clear()
        searches.addAll(loadFor(accountStableId))
    }

    private fun loadFor(accountStableId: String): List<SavedSearch> =
        normalizeSavedSearches(loadSavedSearches(accountStableId))
}
