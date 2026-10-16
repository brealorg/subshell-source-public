package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostFollowUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterises SubshellState's post-follow handling before it moves to a
 * delegate: unknown until acknowledged, the optimistic override, one
 * request at a time with the same target's last intent queued,
 * reconciliation of the follow flow's phases (success keeps or sends the
 * queued follow-up, failure rolls back, another account clears), and no
 * follow at all for a local account.
 */
class PostFollowStateCharacterisationTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:follower",
        username = "follower",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val post = SampleData.posts.first().copy(id = "abc123")
    private val other = SampleData.posts.first().copy(id = "xyz789")

    private fun authenticatedState(): Pair<SubshellState, MutableList<PostFollowRequest>> {
        val requests = mutableListOf<PostFollowRequest>()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.setAuthenticatedPostFollowRequester { request -> requests += request }
        return state to requests
    }

    private fun sync(
        state: SubshellState,
        phase: PostFollowPhase,
        fullname: String,
        followed: Boolean,
        accountStableId: String? = account.stableId,
    ) {
        state.synchronizePostFollowState(
            PostFollowUiState(
                phase = phase,
                accountStableId = accountStableId,
                postFullname = fullname,
                followed = followed,
            ),
        )
    }

    @Test
    fun followIsUnknownUntilTouchedThenOptimisticAndSerialized() {
        val (state, requests) = authenticatedState()
        assertNull(state.effectivePostFollowed(post))
        assertTrue(state.postFollowControlEnabled(post))

        state.togglePostFollow(post)

        assertEquals(listOf(PostFollowRequest(account.stableId, "t3_abc123", true)), requests)
        assertEquals(true, state.effectivePostFollowed(post))
        assertTrue(state.postFollowControlEnabled(post))
        assertFalse(state.postFollowControlEnabled(other))

        state.togglePostFollow(other)
        assertEquals(1, requests.size)
        assertNull(state.effectivePostFollowed(other))
    }

    @Test
    fun successWithoutAQueuedIntentKeepsTheStateAndReleasesTheTarget() {
        val (state, requests) = authenticatedState()
        state.togglePostFollow(post)

        sync(state, PostFollowPhase.SUCCEEDED, "t3_abc123", true)

        assertEquals(1, requests.size)
        assertEquals(true, state.effectivePostFollowed(post))
        assertTrue(state.postFollowControlEnabled(other))

        state.togglePostFollow(post)
        assertEquals(PostFollowRequest(account.stableId, "t3_abc123", false), requests.last())
        assertEquals(false, state.effectivePostFollowed(post))
    }

    @Test
    fun sameTargetPressWhileInFlightQueuesTheLastIntentAndSendsItAfterSuccess() {
        val (state, requests) = authenticatedState()
        state.togglePostFollow(post)

        state.togglePostFollow(post)

        assertEquals(1, requests.size)
        assertEquals(false, state.effectivePostFollowed(post))

        sync(state, PostFollowPhase.SUCCEEDED, "t3_abc123", true)

        assertEquals(2, requests.size)
        assertEquals(PostFollowRequest(account.stableId, "t3_abc123", false), requests.last())
        assertEquals(false, state.effectivePostFollowed(post))
        assertFalse(state.postFollowControlEnabled(other))

        sync(state, PostFollowPhase.SUCCEEDED, "t3_abc123", false)
        assertEquals(2, requests.size)
        assertTrue(state.postFollowControlEnabled(other))
    }

    @Test
    fun queuedIntentEqualToTheAcknowledgedStateSendsNothingMore() {
        val (state, requests) = authenticatedState()
        state.togglePostFollow(post)
        state.togglePostFollow(post)
        state.togglePostFollow(post)

        sync(state, PostFollowPhase.SUCCEEDED, "t3_abc123", true)

        assertEquals(1, requests.size)
        assertEquals(true, state.effectivePostFollowed(post))
        assertTrue(state.postFollowControlEnabled(other))
    }

    @Test
    fun failureRollsBackToUnknownAndDropsTheQueue() {
        val (state, requests) = authenticatedState()
        state.togglePostFollow(post)
        state.togglePostFollow(post)

        sync(state, PostFollowPhase.FAILED, "t3_abc123", true)

        assertNull(state.effectivePostFollowed(post))
        assertTrue(state.postFollowControlEnabled(other))

        sync(state, PostFollowPhase.SUCCEEDED, "t3_abc123", true)
        assertEquals(1, requests.size)
    }

    @Test
    fun failureAfterAnAcknowledgedStateRollsBackToIt() {
        val (state, _) = authenticatedState()
        state.togglePostFollow(post)
        sync(state, PostFollowPhase.SUCCEEDED, "t3_abc123", true)
        state.togglePostFollow(post)

        sync(state, PostFollowPhase.FAILED, "t3_abc123", false)

        assertEquals(true, state.effectivePostFollowed(post))
    }

    @Test
    fun inFlightPhaseFromTheFlowAppliesTheStateAndBlocksOtherTargets() {
        val (state, requests) = authenticatedState()

        sync(state, PostFollowPhase.AWAITING_CALLBACK, "t3_abc123", true)

        assertEquals(true, state.effectivePostFollowed(post))
        assertFalse(state.postFollowControlEnabled(other))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun anotherAccountsFlowStateClearsEverything() {
        val (state, _) = authenticatedState()
        state.togglePostFollow(post)

        sync(state, PostFollowPhase.SUCCEEDED, "t3_abc123", true, accountStableId = "reddit:other")

        assertNull(state.effectivePostFollowed(post))
        assertTrue(state.postFollowControlEnabled(other))
    }

    @Test
    fun aLocalAccountCannotFollowPosts() {
        val state = SubshellState()
        assertFalse(state.activeAccountIsAuthenticated)
        var requested = false
        state.setAuthenticatedPostFollowRequester { requested = true }

        assertFalse(state.postFollowControlEnabled(post))
        state.togglePostFollow(post)

        assertNull(state.effectivePostFollowed(post))
        assertFalse(requested)
    }
}
