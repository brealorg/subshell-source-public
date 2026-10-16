package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.normalizePostFollowFullname
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.postFollowDesiredState
import app.subshell.prototype.ui.postFollowTargetControlEnabled
import app.subshell.prototype.ui.queuedPostFollowFollowUp

/**
 * Post follows of an authenticated account: unknown until acknowledged or
 * touched, then the optimistic override, rollback, one in-flight target at
 * a time with the same target's last intent queued, and the requester
 * MainActivity sets. [synchronizePostFollowState] reconciles the follow
 * flow's phases (success keeps or sends the queued follow-up, failure
 * rolls back, another account clears). A local account cannot follow.
 * Extracted verbatim from SubshellState, which holds one instance and
 * forwards its public API to it; account switch and session sync call
 * [clearAuthenticatedPostFollowState]. Behaviour is unchanged.
 */
internal class PostFollowState(
    private val activeAccountIdProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
) {
    private val activeAccountId: String
        get() = activeAccountIdProvider()
    private val activeAccountIsAuthenticated: Boolean
        get() = activeAccountIsAuthenticatedProvider()

    // FOLLOWPOST01A1B_ACCOUNT_SCOPED_ACKNOWLEDGED_STATE_V1
    private val authenticatedPostFollowOverrides =
        mutableStateMapOf<String, Boolean>()
    private val authenticatedPostFollowRollback =
        mutableMapOf<String, Boolean?>()
    private val authenticatedPostFollowQueuedDesired =
        mutableStateMapOf<String, Boolean>()
    private var authenticatedPostFollowInFlight by
        mutableStateOf<String?>(null)
    private var requestAuthenticatedPostFollow:
        (PostFollowRequest) -> Unit = {}

    fun postFollowControlEnabled(post: Post): Boolean {
        if (!activeAccountIsAuthenticated) return false
        val postFullname = runCatching {
            normalizePostFollowFullname(post.id)
        }.getOrNull() ?: return false
        return postFollowTargetControlEnabled(
            activeAccountIsAuthenticated = activeAccountIsAuthenticated,
            inFlightFullname = authenticatedPostFollowInFlight,
            targetFullname = postFullname,
        )
    }

    fun setAuthenticatedPostFollowRequester(
        requester: (PostFollowRequest) -> Unit,
    ) {
        requestAuthenticatedPostFollow = requester
    }

    fun effectivePostFollowed(post: Post): Boolean? {
        if (!activeAccountIsAuthenticated) return null
        val postFullname = runCatching {
            normalizePostFollowFullname(post.id)
        }.getOrNull() ?: return null
        return authenticatedPostFollowOverrides[postFullname]
    }

    fun togglePostFollow(post: Post) {
        if (!activeAccountIsAuthenticated) return

        val postFullname = runCatching {
            normalizePostFollowFullname(post.id)
        }.getOrNull() ?: return

        val currentInFlight = authenticatedPostFollowInFlight
        if (
            currentInFlight != null &&
            currentInFlight != postFullname
        ) {
            return
        }

        val targetFollowed =
            postFollowDesiredState(effectivePostFollowed(post))

        if (currentInFlight == postFullname) {
            authenticatedPostFollowOverrides[postFullname] =
                targetFollowed
            authenticatedPostFollowQueuedDesired[postFullname] =
                targetFollowed
            return
        }

        authenticatedPostFollowRollback[postFullname] =
            authenticatedPostFollowOverrides[postFullname]
        authenticatedPostFollowOverrides[postFullname] =
            targetFollowed
        authenticatedPostFollowInFlight = postFullname

        requestAuthenticatedPostFollow(
            PostFollowRequest(
                accountStableId = activeAccountId,
                postFullname = postFullname,
                followed = targetFollowed,
            ),
        )
    }

    fun synchronizePostFollowState(
        postFollowState: PostFollowUiState,
    ) {
        if (!activeAccountIsAuthenticated) {
            clearAuthenticatedPostFollowState()
            return
        }

        if (
            postFollowState.accountStableId != null &&
            postFollowState.accountStableId != activeAccountId
        ) {
            clearAuthenticatedPostFollowState()
            return
        }

        val postFullname =
            postFollowState.postFullname ?: return
        val followed =
            postFollowState.followed ?: return

        when (postFollowState.phase) {
            PostFollowPhase.SENDING,
            PostFollowPhase.AUTHORIZATION_REQUIRED,
            PostFollowPhase.AUTHORIZING,
            PostFollowPhase.AWAITING_CALLBACK,
            -> {
                if (
                    !authenticatedPostFollowRollback.containsKey(
                        postFullname,
                    )
                ) {
                    authenticatedPostFollowRollback[postFullname] =
                        authenticatedPostFollowOverrides[postFullname]
                }

                authenticatedPostFollowOverrides[postFullname] =
                    authenticatedPostFollowQueuedDesired[postFullname]
                        ?: followed

                authenticatedPostFollowInFlight = postFullname
            }

            PostFollowPhase.SUCCEEDED -> {
                val queuedDesired =
                    authenticatedPostFollowQueuedDesired.remove(
                        postFullname,
                    )

                val queuedFollowUp =
                    queuedPostFollowFollowUp(
                        acknowledgedFollowed = followed,
                        queuedDesiredFollowed = queuedDesired,
                    )

                if (queuedFollowUp != null) {
                    authenticatedPostFollowRollback[postFullname] =
                        followed
                    authenticatedPostFollowOverrides[postFullname] =
                        queuedFollowUp
                    authenticatedPostFollowInFlight =
                        postFullname

                    requestAuthenticatedPostFollow(
                        PostFollowRequest(
                            accountStableId = activeAccountId,
                            postFullname = postFullname,
                            followed = queuedFollowUp,
                        ),
                    )
                } else {
                    authenticatedPostFollowOverrides[postFullname] =
                        followed
                    authenticatedPostFollowRollback.remove(
                        postFullname,
                    )
                    if (
                        authenticatedPostFollowInFlight ==
                        postFullname
                    ) {
                        authenticatedPostFollowInFlight = null
                    }
                }
            }

            PostFollowPhase.FAILED -> {
                authenticatedPostFollowQueuedDesired.remove(
                    postFullname,
                )

                if (
                    authenticatedPostFollowRollback.containsKey(
                        postFullname,
                    )
                ) {
                    val previous =
                        authenticatedPostFollowRollback.remove(
                            postFullname,
                        )

                    if (previous == null) {
                        authenticatedPostFollowOverrides.remove(
                            postFullname,
                        )
                    } else {
                        authenticatedPostFollowOverrides[postFullname] =
                            previous
                    }
                }

                if (
                    authenticatedPostFollowInFlight ==
                    postFullname
                ) {
                    authenticatedPostFollowInFlight = null
                }
            }

            PostFollowPhase.IDLE -> Unit
        }
    }

    fun clearAuthenticatedPostFollowState() {
        authenticatedPostFollowOverrides.clear()
        authenticatedPostFollowRollback.clear()
        authenticatedPostFollowQueuedDesired.clear()
        authenticatedPostFollowInFlight = null
    }
}
