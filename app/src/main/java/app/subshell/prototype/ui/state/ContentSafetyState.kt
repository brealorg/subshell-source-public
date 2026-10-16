package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.account.normalizeRedditSafetyCommentFullname
import app.subshell.prototype.data.account.normalizeRedditSafetyPostFullname
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.normalizedPostIdentity

/**
 * Content safety: hidden posts (the local hidden list, and for an
 * authenticated account its remote projection with optimistic override
 * and membership rollback), reports, user blocks with the authoritative
 * blocked-accounts list, the one in-flight action shared by hide, report
 * and block, and the requesters MainActivity sets.
 * [synchronizeContentSafetyState] reconciles the flow's phases (success
 * keeps, failure rolls back, another account clears everything including
 * the hidden projection).
 * Extracted verbatim from SubshellState, which holds one instance and
 * forwards its public API to it. The account post actions (capture,
 * replace on account switch, persistence) and applyDeletedPost stay in
 * SubshellState and read [hiddenPostIds] through its forwarder; account
 * switch and session sync call [clearAuthenticatedContentSafetyState].
 * Behaviour is unchanged.
 */
internal class ContentSafetyState(
    private val persistActiveAccountPostActions: () -> Unit,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountUsernameProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
    private val localMutationsAllowedProvider: () -> Boolean,
) {
    private val activeAccountId: String
        get() = activeAccountIdProvider()
    private val activeAccountUsername: String
        get() = activeAccountUsernameProvider()
    private val activeAccountIsAuthenticated: Boolean
        get() = activeAccountIsAuthenticatedProvider()
    private val localMutationsAllowed: Boolean
        get() = localMutationsAllowedProvider()

    val hiddenPostIds = mutableStateListOf<String>()
    private val authenticatedPostHiddenOverrides = mutableStateMapOf<String, Boolean>()
    private val authenticatedPostHiddenRollback = mutableMapOf<String, Boolean?>()
    private val authenticatedPostHiddenMembershipRollback = mutableMapOf<String, Boolean>()
    // USERACTION01A2_AUTHORITATIVE_BLOCKED_ACCOUNT_PROJECTION_V1
    private val authenticatedBlockedUsernames = mutableStateMapOf<String, Unit>()
    private val authenticatedUserBlockedOverrides = mutableStateMapOf<String, Boolean>()
    private val authenticatedUserBlockedRollback = mutableMapOf<String, Boolean?>()
    private var authenticatedContentSafetyInFlight by mutableStateOf<String?>(null)
    private var requestAuthenticatedPostVisibility: (RedditPostVisibilityRequest) -> Unit = {}
    private var requestAuthenticatedContentReport: (RedditContentReportRequest) -> Unit = {}
    private var requestAuthenticatedUserBlock: (RedditUserBlockRequest) -> Unit = {}

    fun setAuthenticatedContentSafetyRequesters(
        postVisibilityRequester: (RedditPostVisibilityRequest) -> Unit,
        reportRequester: (RedditContentReportRequest) -> Unit,
        userBlockRequester: (RedditUserBlockRequest) -> Unit = {},
    ) {
        requestAuthenticatedPostVisibility = postVisibilityRequester
        requestAuthenticatedContentReport = reportRequester
        requestAuthenticatedUserBlock = userBlockRequester
    }

    fun effectivePostHidden(
        post: Post,
        authoritativeHiddenHint: Boolean = false,
    ): Boolean {
        if (!activeAccountIsAuthenticated) return post.id in hiddenPostIds
        val fullname = runCatching {
            normalizeRedditSafetyPostFullname(post.id)
        }.getOrNull() ?: return authoritativeHiddenHint
        return authenticatedPostHiddenOverrides[fullname]
            ?: (authoritativeHiddenHint ||
                normalizedPostIdentity(post.id) in hiddenPostIds)
    }

    fun postVisibilityControlEnabled(post: Post): Boolean =
        localMutationsAllowed || (
            authenticatedContentSafetyInFlight == null &&
                runCatching { normalizeRedditSafetyPostFullname(post.id) }.isSuccess
        )

    fun contentReportControlEnabled(): Boolean =
        activeAccountIsAuthenticated && authenticatedContentSafetyInFlight == null

    // USERACTION01A1_PROFILE_BLOCK_STATE_V1
    fun isUserBlocked(username: String): Boolean =
        runCatching { normalizeRedditSafetyUsername(username) }
            .getOrNull()
            ?.let { normalized ->
                authenticatedUserBlockedOverrides[normalized]
                    ?: authenticatedBlockedUsernames.containsKey(normalized)
            }
            ?: false

    // USERACTION01A2_OPTIMISTIC_AUTHORITATIVE_ROW_PROJECTION_V1
    fun showsAuthoritativeBlockedAccount(username: String): Boolean =
        runCatching { normalizeRedditSafetyUsername(username) }
            .getOrNull()
            ?.let { normalized -> authenticatedUserBlockedOverrides[normalized] != false }
            ?: false

    fun userBlockControlEnabled(username: String): Boolean {
        if (!activeAccountIsAuthenticated || authenticatedContentSafetyInFlight != null) {
            return false
        }
        val normalized = runCatching {
            normalizeRedditSafetyUsername(username)
        }.getOrNull() ?: return false
        val activeUsername = runCatching {
            normalizeRedditSafetyUsername(activeAccountUsername)
        }.getOrNull()
        return normalized != activeUsername
    }

    fun userBlockInProgress(username: String): Boolean {
        val normalized = runCatching { normalizeRedditSafetyUsername(username) }
            .getOrNull()
            ?: return false
        return authenticatedContentSafetyInFlight == "u/$normalized"
    }

    fun noteAuthoritativeHiddenPost(postId: String) {
        if (!activeAccountIsAuthenticated) return
        val normalized = normalizedPostIdentity(postId)
        if (normalized.isNotEmpty() && normalized !in hiddenPostIds) {
            hiddenPostIds.add(normalized)
        }
    }

    // LOWUI4_LOCAL_HIDE_ONLY_WITHOUT_AN_ACCOUNT_V1 (OCB-288): a signed-in
    // account's hidden list is Reddit's (SAFETY01A3); hiding there goes through
    // togglePostHidden, which asks Reddit. This local hide is for no account only.
    fun hidePost(postId: String) {
        if (activeAccountIsAuthenticated) return
        val normalizedPostId = normalizedPostIdentity(postId)
        if (normalizedPostId.isEmpty() || normalizedPostId in hiddenPostIds) return
        hiddenPostIds.add(normalizedPostId)
        persistActiveAccountPostActions()
    }

    fun togglePostHidden(
        post: Post,
        authoritativeHiddenHint: Boolean = false,
    ) {
        val normalizedPostId = normalizedPostIdentity(post.id)
        if (normalizedPostId.isEmpty()) return
        if (!activeAccountIsAuthenticated) {
            if (normalizedPostId in hiddenPostIds) {
                hiddenPostIds.remove(normalizedPostId)
            } else {
                hiddenPostIds.add(normalizedPostId)
            }
            persistActiveAccountPostActions()
            return
        }
        if (authenticatedContentSafetyInFlight != null) return
        val postFullname = runCatching {
            normalizeRedditSafetyPostFullname(post.id)
        }.getOrNull() ?: return
        val currentHidden = effectivePostHidden(post, authoritativeHiddenHint)
        val targetHidden = !currentHidden
        authenticatedPostHiddenRollback[postFullname] =
            authenticatedPostHiddenOverrides[postFullname]
        authenticatedPostHiddenMembershipRollback[postFullname] =
            normalizedPostId in hiddenPostIds
        authenticatedPostHiddenOverrides[postFullname] = targetHidden
        updateAuthenticatedHiddenMembership(postFullname, targetHidden)
        authenticatedContentSafetyInFlight = postFullname
        requestAuthenticatedPostVisibility(
            RedditPostVisibilityRequest(
                accountStableId = activeAccountId,
                postFullname = postFullname,
                hidden = targetHidden,
            ),
        )
    }

    fun report(post: Post, reason: String) {
        if (!activeAccountIsAuthenticated || authenticatedContentSafetyInFlight != null) return
        val fullname = runCatching {
            normalizeRedditSafetyPostFullname(post.id)
        }.getOrNull() ?: return
        val normalizedReason = reason.trim()
        if (normalizedReason.isEmpty()) return
        authenticatedContentSafetyInFlight = fullname
        requestAuthenticatedContentReport(
            RedditContentReportRequest(
                accountStableId = activeAccountId,
                thingFullname = fullname,
                reason = normalizedReason,
                communityName = post.community,
            ),
        )
    }

    fun report(comment: Comment, reason: String) {
        if (!activeAccountIsAuthenticated || authenticatedContentSafetyInFlight != null) return
        val fullname = runCatching {
            normalizeRedditSafetyCommentFullname(comment.id)
        }.getOrNull() ?: return
        val normalizedReason = reason.trim()
        if (normalizedReason.isEmpty()) return
        authenticatedContentSafetyInFlight = fullname
        requestAuthenticatedContentReport(
            RedditContentReportRequest(
                accountStableId = activeAccountId,
                thingFullname = fullname,
                reason = normalizedReason,
            ),
        )
    }

    fun setUserBlocked(username: String, blocked: Boolean) {
        if (!activeAccountIsAuthenticated || authenticatedContentSafetyInFlight != null) return
        val normalized = runCatching {
            normalizeRedditSafetyUsername(username)
        }.getOrNull() ?: return
        val activeUsername = runCatching {
            normalizeRedditSafetyUsername(activeAccountUsername)
        }.getOrNull()
        if (normalized == activeUsername) return

        authenticatedUserBlockedRollback[normalized] =
            authenticatedUserBlockedOverrides[normalized]
        authenticatedUserBlockedOverrides[normalized] = blocked
        authenticatedContentSafetyInFlight = "u/$normalized"
        requestAuthenticatedUserBlock(
            RedditUserBlockRequest(
                accountStableId = activeAccountId,
                username = normalized,
                blocked = blocked,
            ),
        )
    }

    // SAFETY01A3_OPTIMISTIC_HIDDEN_STATE_AND_ROLLBACK_V1
    fun synchronizeContentSafetyState(contentSafetyState: ContentSafetyUiState) {
        val blockedAccounts = contentSafetyState.blockedAccounts
        if (
            activeAccountIsAuthenticated &&
            blockedAccounts.matches(activeAccountId) &&
            blockedAccounts.loaded &&
            !blockedAccounts.inProgress
        ) {
            val authoritativeNames = blockedAccounts.items
                .map { account -> account.username.lowercase() }
                .toSet()
            authenticatedBlockedUsernames.keys
                .filterNot(authoritativeNames::contains)
                .forEach { username -> authenticatedBlockedUsernames.remove(username) }
            authoritativeNames.forEach { username ->
                authenticatedBlockedUsernames[username] = Unit
                val optimistic = authenticatedUserBlockedOverrides[username]
                if (
                    optimistic == true &&
                    !authenticatedUserBlockedRollback.containsKey(username)
                ) {
                    authenticatedUserBlockedOverrides.remove(username)
                }
            }
            authenticatedUserBlockedOverrides.keys
                .filter { username ->
                    username !in authoritativeNames &&
                        authenticatedUserBlockedOverrides[username] == false &&
                        !authenticatedUserBlockedRollback.containsKey(username)
                }
                .toList()
                .forEach { username -> authenticatedUserBlockedOverrides.remove(username) }
        }
        if (!activeAccountIsAuthenticated) {
            clearAuthenticatedContentSafetyState()
            return
        }
        if (
            contentSafetyState.accountStableId != null &&
            contentSafetyState.accountStableId != activeAccountId
        ) {
            clearAuthenticatedContentSafetyState()
            return
        }
        val fullname = contentSafetyState.thingFullname ?: return
        when (contentSafetyState.operation) {
            ContentSafetyOperation.SET_POST_HIDDEN -> {
                val hidden = contentSafetyState.hidden ?: return
                when (contentSafetyState.phase) {
                    ContentSafetyPhase.AUTHORIZATION_REQUIRED,
                    ContentSafetyPhase.AUTHORIZING,
                    ContentSafetyPhase.AWAITING_CALLBACK,
                    ContentSafetyPhase.SENDING,
                    -> {
                        if (!authenticatedPostHiddenRollback.containsKey(fullname)) {
                            authenticatedPostHiddenRollback[fullname] =
                                authenticatedPostHiddenOverrides[fullname]
                            authenticatedPostHiddenMembershipRollback[fullname] =
                                normalizedPostIdentity(fullname) in hiddenPostIds
                        }
                        authenticatedPostHiddenOverrides[fullname] = hidden
                        updateAuthenticatedHiddenMembership(fullname, hidden)
                        authenticatedContentSafetyInFlight = fullname
                    }
                    ContentSafetyPhase.SUCCEEDED -> {
                        authenticatedPostHiddenOverrides[fullname] = hidden
                        updateAuthenticatedHiddenMembership(fullname, hidden)
                        clearAuthenticatedHiddenRollback(fullname)
                        authenticatedContentSafetyInFlight = null
                    }
                    ContentSafetyPhase.FAILED -> {
                        rollbackAuthenticatedHidden(fullname)
                        authenticatedContentSafetyInFlight = null
                    }
                    ContentSafetyPhase.IDLE -> Unit
                }
            }
            ContentSafetyOperation.REPORT_CONTENT -> {
                when (contentSafetyState.phase) {
                    ContentSafetyPhase.AUTHORIZATION_REQUIRED,
                    ContentSafetyPhase.AUTHORIZING,
                    ContentSafetyPhase.AWAITING_CALLBACK,
                    ContentSafetyPhase.SENDING,
                    -> authenticatedContentSafetyInFlight = fullname
                    ContentSafetyPhase.SUCCEEDED -> {
                        if (contentSafetyState.hidden == true && fullname.startsWith("t3_")) {
                            authenticatedPostHiddenOverrides[fullname] = true
                            updateAuthenticatedHiddenMembership(fullname, true)
                        }
                        authenticatedContentSafetyInFlight = null
                    }
                    ContentSafetyPhase.FAILED,
                    ContentSafetyPhase.IDLE,
                    -> authenticatedContentSafetyInFlight = null
                }
            }
            ContentSafetyOperation.SET_USER_BLOCKED -> {
                val username = contentSafetyState.username
                    ?.let { value ->
                        runCatching { normalizeRedditSafetyUsername(value) }.getOrNull()
                    }
                    ?: return
                val blocked = contentSafetyState.blocked ?: return
                when (contentSafetyState.phase) {
                    ContentSafetyPhase.AUTHORIZATION_REQUIRED,
                    ContentSafetyPhase.AUTHORIZING,
                    ContentSafetyPhase.AWAITING_CALLBACK,
                    ContentSafetyPhase.SENDING,
                    -> {
                        if (!authenticatedUserBlockedRollback.containsKey(username)) {
                            authenticatedUserBlockedRollback[username] =
                                authenticatedUserBlockedOverrides[username]
                        }
                        authenticatedUserBlockedOverrides[username] = blocked
                        authenticatedContentSafetyInFlight = "u/$username"
                    }
                    ContentSafetyPhase.SUCCEEDED -> {
                        authenticatedUserBlockedOverrides[username] = blocked
                        authenticatedUserBlockedRollback.remove(username)
                        authenticatedContentSafetyInFlight = null
                    }
                    ContentSafetyPhase.FAILED -> {
                        rollbackAuthenticatedUserBlocked(username)
                        authenticatedContentSafetyInFlight = null
                    }
                    ContentSafetyPhase.IDLE -> Unit
                }
            }
            null -> if (contentSafetyState.phase == ContentSafetyPhase.IDLE) {
                authenticatedContentSafetyInFlight = null
            }
        }
    }

    private fun updateAuthenticatedHiddenMembership(
        postFullname: String,
        hidden: Boolean,
    ) {
        val normalizedPostId = normalizedPostIdentity(postFullname)
        if (normalizedPostId.isEmpty()) return
        if (hidden) {
            if (normalizedPostId !in hiddenPostIds) hiddenPostIds.add(normalizedPostId)
        } else {
            hiddenPostIds.removeAll { id -> normalizedPostIdentity(id) == normalizedPostId }
        }
    }

    private fun rollbackAuthenticatedHidden(postFullname: String) {
        val previousOverride = authenticatedPostHiddenRollback.remove(postFullname)
        if (previousOverride == null) {
            authenticatedPostHiddenOverrides.remove(postFullname)
        } else {
            authenticatedPostHiddenOverrides[postFullname] = previousOverride
        }
        val wasMember = authenticatedPostHiddenMembershipRollback.remove(postFullname)
        if (wasMember != null) updateAuthenticatedHiddenMembership(postFullname, wasMember)
    }

    private fun clearAuthenticatedHiddenRollback(postFullname: String) {
        authenticatedPostHiddenRollback.remove(postFullname)
        authenticatedPostHiddenMembershipRollback.remove(postFullname)
    }

    private fun rollbackAuthenticatedUserBlocked(username: String) {
        val previous = authenticatedUserBlockedRollback.remove(username)
        if (previous == null) {
            authenticatedUserBlockedOverrides.remove(username)
        } else {
            authenticatedUserBlockedOverrides[username] = previous
        }
    }

    fun clearAuthenticatedContentSafetyState() {
        authenticatedPostHiddenOverrides.clear()
        authenticatedPostHiddenRollback.clear()
        authenticatedPostHiddenMembershipRollback.clear()
        authenticatedBlockedUsernames.clear()
        authenticatedUserBlockedOverrides.clear()
        authenticatedUserBlockedRollback.clear()
        authenticatedContentSafetyInFlight = null
        if (activeAccountIsAuthenticated) hiddenPostIds.clear()
    }
}
