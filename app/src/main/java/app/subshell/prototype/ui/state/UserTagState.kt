package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateMapOf
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.normalizeUserTagUsername
import app.subshell.prototype.ui.normalizeUserTags
import app.subshell.prototype.ui.normalizedUserTagOrNull

/**
 * ISSUE34_USER_TAGS_V1: the active account's private notes and tags on
 * Reddit usernames. Tags exist only for signed-in Reddit accounts; the
 * local (signed-out) session neither shows nor stores any. SubshellState
 * holds one instance and reloads it on account switch and session sync
 * through [reloadForAccount].
 */
internal class UserTagState(
    private val loadUserTags: (String) -> Map<String, UserTag>,
    private val persistUserTags: (String, Map<String, UserTag>) -> Unit,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
    private val authenticatedAccountIdsProvider: () -> List<String>,
) {
    private val tagsByUsername = mutableStateMapOf<String, UserTag>().apply {
        putAll(loadFor(activeAccountIdProvider()))
    }

    val available: Boolean
        get() = activeAccountIsAuthenticatedProvider()

    val tags: Map<String, UserTag>
        get() = if (available) tagsByUsername else emptyMap()

    /** A real Reddit username other than the active account's own. */
    fun canTag(username: String, activeUsername: String): Boolean =
        available &&
            normalizeUserTagUsername(username) != null &&
            normalizeUserTagUsername(username) != normalizeUserTagUsername(activeUsername)

    fun tagFor(username: String): UserTag? {
        if (!available) return null
        val key = normalizeUserTagUsername(username) ?: return null
        return tagsByUsername[key]
    }

    /** Stores [tag] for [username]; a tag with no label and no note (or null) removes it. */
    fun setTag(username: String, tag: UserTag?) {
        if (!available) return
        val key = normalizeUserTagUsername(username) ?: return
        val normalized = tag?.let(::normalizedUserTagOrNull)
        if (tagsByUsername[key] == normalized) return
        if (normalized == null) {
            tagsByUsername.remove(key)
        } else {
            tagsByUsername[key] = normalized
        }
        persistUserTags(activeAccountIdProvider(), tagsByUsername.toMap())
    }

    // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
    /** Every signed-in account's tags: the active one from memory, the others from storage. */
    fun captureByAccount(): Map<String, Map<String, UserTag>> {
        val activeId = activeAccountIdProvider()
        return authenticatedAccountIdsProvider().associateWith { accountId ->
            if (accountId == activeId) tagsByUsername.toMap() else loadFor(accountId)
        }
    }

    /**
     * Live-state projection of a restore whose durable commit already happened.
     * Null = module missing (unchanged); empty = every account cleared;
     * otherwise only a listed active account changes.
     */
    fun applyRestored(restoredByAccount: Map<String, Map<String, UserTag>>?) {
        val restored = restoredByAccount ?: return
        val activeRestored = if (restored.isEmpty()) {
            emptyMap()
        } else {
            restored[activeAccountIdProvider()] ?: return
        }
        tagsByUsername.clear()
        tagsByUsername.putAll(normalizeUserTags(activeRestored))
    }

    fun reloadForAccount(accountStableId: String) {
        tagsByUsername.clear()
        tagsByUsername.putAll(loadFor(accountStableId))
    }

    private fun loadFor(accountStableId: String): Map<String, UserTag> =
        normalizeUserTags(loadUserTags(accountStableId))
}
