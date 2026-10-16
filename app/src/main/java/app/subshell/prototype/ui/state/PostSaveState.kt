package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.normalizeCommentSaveFullname
import app.subshell.prototype.data.account.normalizePostSaveFullname
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.postSaveTargetControlEnabled
import app.subshell.prototype.ui.queuedPostSaveFollowUp

/**
 * Post and comment saves: the saved-post list of the account post
 * actions, and for an authenticated account the optimistic override,
 * rollback (override and saved-list membership), one in-flight target at a
 * time with the same target's last intent queued (ISSUE16), and the
 * requester MainActivity sets. [synchronizePostSaveState] reconciles the
 * save flow's phases (success keeps or sends the queued follow-up, failure
 * rolls back, another account clears).
 * Extracted verbatim from SubshellState, which holds one instance and
 * forwards its public API to it. The account post actions (capture,
 * replace on account switch, persistence), applyDeletedPost and the saved
 * library projection stay in SubshellState and read [savedPostIds] through
 * its forwarder; account switch and session sync call
 * [clearAuthenticatedPostSaveState]. Behaviour is unchanged.
 */
internal class PostSaveState(
    private val repository: SubshellRepository,
    private val persistActiveAccountPostActions: () -> Unit,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
    private val localMutationsAllowedProvider: () -> Boolean,
) {
    private val activeAccountId: String
        get() = activeAccountIdProvider()
    private val activeAccountIsAuthenticated: Boolean
        get() = activeAccountIsAuthenticatedProvider()
    private val localMutationsAllowed: Boolean
        get() = localMutationsAllowedProvider()

    val savedPostIds = mutableStateListOf<String>()
    private val authenticatedPostSaveOverrides = mutableStateMapOf<String, Boolean>()
    private val authenticatedPostSaveRollback = mutableMapOf<String, Boolean?>()
    private val authenticatedPostSaveMembershipRollback = mutableMapOf<String, Boolean>()
    private val authenticatedPostSavePostIdByFullname = mutableMapOf<String, String>()
    // ISSUE16_SAVE_LAST_INTENT_QUEUE_V1
    private val authenticatedPostSaveQueuedDesired = mutableStateMapOf<String, Boolean>()
    private var authenticatedPostSaveInFlight by mutableStateOf<String?>(null)
    private var requestAuthenticatedPostSave: (PostSaveRequest) -> Unit = {}

    val authenticatedPostSavingEnabled: Boolean
        get() = activeAccountIsAuthenticated && authenticatedPostSaveInFlight == null

    fun postSaveControlEnabled(post: Post): Boolean {
        if (!activeAccountIsAuthenticated) return localMutationsAllowed
        val postFullname = runCatching {
            normalizePostSaveFullname(post.id)
        }.getOrNull() ?: return false
        return postSaveTargetControlEnabled(
            activeAccountIsAuthenticated = activeAccountIsAuthenticated,
            inFlightFullname = authenticatedPostSaveInFlight,
            targetFullname = postFullname,
        )
    }

    fun setAuthenticatedPostSaveRequester(
        requester: (PostSaveRequest) -> Unit,
    ) {
        requestAuthenticatedPostSave = requester
    }

    fun effectivePostSaved(post: Post): Boolean =
        if (activeAccountIsAuthenticated) {
            runCatching { normalizePostSaveFullname(post.id) }
                .getOrNull()
                ?.let(authenticatedPostSaveOverrides::get)
                ?: post.saved
        } else {
            post.id in savedPostIds
        }

    // COMMENTSAVE01A2_LIVE_COMMENT_SAVE_STATE_V1
    fun effectiveCommentSaved(comment: Comment): Boolean =
        if (activeAccountIsAuthenticated) {
            runCatching { normalizeCommentSaveFullname(comment.id) }
                .getOrNull()
                ?.let(authenticatedPostSaveOverrides::get)
                ?: comment.saved
        } else {
            comment.saved
        }

    fun commentSaveControlEnabled(comment: Comment): Boolean {
        val commentFullname = runCatching {
            normalizeCommentSaveFullname(comment.id)
        }.getOrNull() ?: return false
        return postSaveTargetControlEnabled(
            activeAccountIsAuthenticated = activeAccountIsAuthenticated,
            inFlightFullname = authenticatedPostSaveInFlight,
            targetFullname = commentFullname,
        )
    }

    fun toggleSaved(postId: String) {
        if (!localMutationsAllowed) return
        val normalizedPostId = postId.trim()
        if (normalizedPostId.isEmpty()) return
        val saved = if (savedPostIds.remove(normalizedPostId)) {
            false
        } else {
            savedPostIds.add(normalizedPostId)
            true
        }
        repository.recordSaved(normalizedPostId, saved)
        persistActiveAccountPostActions()
    }

    fun toggleSaved(post: Post) {
        if (!activeAccountIsAuthenticated) {
            toggleSaved(post.id)
            return
        }
        val postFullname = runCatching {
            normalizePostSaveFullname(post.id)
        }.getOrNull() ?: return
        val currentInFlight = authenticatedPostSaveInFlight
        if (currentInFlight != null && currentInFlight != postFullname) return

        val currentSaved = effectivePostSaved(post)
        val targetSaved = !currentSaved

        if (currentInFlight == postFullname) {
            authenticatedPostSaveOverrides[postFullname] = targetSaved
            updateSavedPostMembership(post.id, targetSaved)
            authenticatedPostSaveQueuedDesired[postFullname] = targetSaved
            return
        }

        authenticatedPostSaveRollback[postFullname] =
            authenticatedPostSaveOverrides[postFullname]
        authenticatedPostSaveMembershipRollback[postFullname] =
            post.id in savedPostIds
        authenticatedPostSavePostIdByFullname[postFullname] = post.id
        authenticatedPostSaveOverrides[postFullname] = targetSaved
        updateSavedPostMembership(post.id, targetSaved)
        authenticatedPostSaveInFlight = postFullname
        requestAuthenticatedPostSave(
            PostSaveRequest(
                accountStableId = activeAccountId,
                postFullname = postFullname,
                saved = targetSaved,
            ),
        )
    }

    fun toggleSaved(comment: Comment) {
        if (!activeAccountIsAuthenticated) return
        val commentFullname = runCatching {
            normalizeCommentSaveFullname(comment.id)
        }.getOrNull() ?: return
        val currentInFlight = authenticatedPostSaveInFlight
        if (currentInFlight != null && currentInFlight != commentFullname) return

        val targetSaved = !effectiveCommentSaved(comment)

        if (currentInFlight == commentFullname) {
            authenticatedPostSaveOverrides[commentFullname] = targetSaved
            authenticatedPostSaveQueuedDesired[commentFullname] = targetSaved
            return
        }

        authenticatedPostSaveRollback[commentFullname] =
            authenticatedPostSaveOverrides[commentFullname]
        authenticatedPostSaveOverrides[commentFullname] = targetSaved
        authenticatedPostSaveInFlight = commentFullname
        requestAuthenticatedPostSave(
            PostSaveRequest(
                accountStableId = activeAccountId,
                postFullname = commentFullname,
                saved = targetSaved,
            ),
        )
    }

    private fun updateSavedPostMembership(postId: String, saved: Boolean) {
        val normalizedPostId = postId.trim()
        if (normalizedPostId.isEmpty()) return
        if (saved) {
            if (normalizedPostId !in savedPostIds) savedPostIds.add(normalizedPostId)
        } else {
            savedPostIds.remove(normalizedPostId)
        }
        persistActiveAccountPostActions()
    }

    fun synchronizePostSaveState(postSaveState: PostSaveUiState) {
        if (!activeAccountIsAuthenticated) {
            clearAuthenticatedPostSaveState()
            return
        }
        if (
            postSaveState.accountStableId != null &&
            postSaveState.accountStableId != activeAccountId
        ) {
            clearAuthenticatedPostSaveState()
            return
        }
        val postFullname = postSaveState.postFullname ?: return
        val saved = postSaveState.saved ?: return

        when (postSaveState.phase) {
            PostSavePhase.SENDING,
            PostSavePhase.AUTHORIZATION_REQUIRED,
            PostSavePhase.AUTHORIZING,
            PostSavePhase.AWAITING_CALLBACK,
            -> {
                if (!authenticatedPostSaveRollback.containsKey(postFullname)) {
                    authenticatedPostSaveRollback[postFullname] =
                        authenticatedPostSaveOverrides[postFullname]
                }
                authenticatedPostSaveOverrides[postFullname] =
                    authenticatedPostSaveQueuedDesired[postFullname] ?: saved
                authenticatedPostSaveInFlight = postFullname
            }

            PostSavePhase.SUCCEEDED -> {
                val queuedDesired = authenticatedPostSaveQueuedDesired.remove(postFullname)
                val queuedFollowUp = queuedPostSaveFollowUp(
                    acknowledgedSaved = saved,
                    queuedDesiredSaved = queuedDesired,
                )

                if (queuedFollowUp != null) {
                    // The acknowledged state becomes the rollback baseline for the
                    // follow-up write. The UI stays at the user's latest intent.
                    authenticatedPostSaveRollback[postFullname] = saved
                    authenticatedPostSaveOverrides[postFullname] = queuedFollowUp
                    authenticatedPostSavePostIdByFullname[postFullname]?.let { postId ->
                        authenticatedPostSaveMembershipRollback[postFullname] = saved
                        updateSavedPostMembership(postId, queuedFollowUp)
                    }
                    authenticatedPostSaveInFlight = postFullname
                    requestAuthenticatedPostSave(
                        PostSaveRequest(
                            accountStableId = activeAccountId,
                            postFullname = postFullname,
                            saved = queuedFollowUp,
                        ),
                    )
                } else {
                    authenticatedPostSaveOverrides[postFullname] = saved
                    authenticatedPostSaveRollback.remove(postFullname)
                    authenticatedPostSaveMembershipRollback.remove(postFullname)
                    authenticatedPostSavePostIdByFullname.remove(postFullname)
                    if (authenticatedPostSaveInFlight == postFullname) {
                        authenticatedPostSaveInFlight = null
                    }
                }
            }

            PostSavePhase.FAILED -> {
                authenticatedPostSaveQueuedDesired.remove(postFullname)
                if (authenticatedPostSaveRollback.containsKey(postFullname)) {
                    val previous = authenticatedPostSaveRollback.remove(postFullname)
                    if (previous == null) {
                        authenticatedPostSaveOverrides.remove(postFullname)
                    } else {
                        authenticatedPostSaveOverrides[postFullname] = previous
                    }
                }
                authenticatedPostSaveMembershipRollback.remove(postFullname)?.let { wasSaved ->
                    authenticatedPostSavePostIdByFullname.remove(postFullname)?.let { postId ->
                        updateSavedPostMembership(postId, wasSaved)
                    }
                }
                if (authenticatedPostSaveInFlight == postFullname) {
                    authenticatedPostSaveInFlight = null
                }
            }

            PostSavePhase.IDLE -> Unit
        }
    }

    fun clearAuthenticatedPostSaveState() {
        authenticatedPostSaveOverrides.clear()
        authenticatedPostSaveRollback.clear()
        authenticatedPostSaveMembershipRollback.clear()
        authenticatedPostSavePostIdByFullname.clear()
        authenticatedPostSaveQueuedDesired.clear()
        authenticatedPostSaveInFlight = null
    }
}
