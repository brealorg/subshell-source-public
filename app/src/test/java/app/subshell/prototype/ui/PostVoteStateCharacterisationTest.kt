package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterises SubshellState's vote handling before it moves to a
 * delegate: the authenticated optimistic override, the in-flight guard,
 * reconciliation of the vote flow's phases (success keeps, failure rolls
 * back, another account clears) and the local post/comment vote maps.
 */
class PostVoteStateCharacterisationTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:voter",
        username = "voter",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val post = SampleData.posts.first().copy(id = "abc123", score = 10, vote = 0)
    private val comment = SampleData.comments.first().copy(id = "def456", score = 5, vote = 0)

    private fun authenticatedState(): Pair<SubshellState, MutableList<PostVoteRequest>> {
        val requests = mutableListOf<PostVoteRequest>()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.setAuthenticatedPostVoteRequester { request -> requests += request }
        return state to requests
    }

    private fun sync(
        state: SubshellState,
        phase: PostVotePhase,
        fullname: String,
        direction: Int,
        accountStableId: String? = account.stableId,
    ) {
        state.synchronizePostVoteState(
            PostVoteUiState(
                phase = phase,
                accountStableId = accountStableId,
                postFullname = fullname,
                direction = direction,
            ),
        )
    }

    @Test
    fun authenticatedPostVoteIsOptimisticAndGuardedWhileInFlight() {
        val (state, requests) = authenticatedState()
        assertTrue(state.activeAccountIsAuthenticated)
        assertTrue(state.postVoteControlEnabled(post))

        state.vote(post, 1)

        assertEquals(listOf(PostVoteRequest(account.stableId, "t3_abc123", 1)), requests)
        assertEquals(1, state.effectivePostVote(post))
        assertEquals(11, state.effectivePostScore(post))
        assertFalse(state.postVoteControlEnabled(post))
        assertFalse("abc123" in state.votes)

        state.vote(post, -1)
        assertEquals(1, requests.size)
    }

    @Test
    fun successKeepsTheOverrideAndReleasesTheTarget() {
        val (state, requests) = authenticatedState()
        state.vote(post, 1)

        sync(state, PostVotePhase.SUCCEEDED, "t3_abc123", 1)

        assertEquals(1, state.effectivePostVote(post))
        assertTrue(state.postVoteControlEnabled(post))

        state.vote(post, 1)
        assertEquals(PostVoteRequest(account.stableId, "t3_abc123", 0), requests.last())
        assertEquals(0, state.effectivePostVote(post))
    }

    @Test
    fun failureRollsBackToThePreviousOverride() {
        val (state, requests) = authenticatedState()
        state.vote(post, 1)
        sync(state, PostVotePhase.SUCCEEDED, "t3_abc123", 1)
        state.vote(post, -1)
        assertEquals(-1, requests.last().direction)
        assertEquals(-1, state.effectivePostVote(post))

        sync(state, PostVotePhase.FAILED, "t3_abc123", -1)

        assertEquals(1, state.effectivePostVote(post))
        assertTrue(state.postVoteControlEnabled(post))
    }

    @Test
    fun failureWithoutAnEarlierOverrideFallsBackToTheServerVote() {
        val (state, _) = authenticatedState()
        state.vote(post, 1)

        sync(state, PostVotePhase.FAILED, "t3_abc123", 1)

        assertEquals(0, state.effectivePostVote(post))
        assertEquals(10, state.effectivePostScore(post))
        assertTrue(state.postVoteControlEnabled(post))
    }

    @Test
    fun inFlightPhaseFromTheFlowAppliesTheOverrideAndBlocksTheTarget() {
        val (state, requests) = authenticatedState()

        sync(state, PostVotePhase.AWAITING_CALLBACK, "t3_abc123", -1)

        assertEquals(-1, state.effectivePostVote(post))
        assertFalse(state.postVoteControlEnabled(post))
        assertTrue(requests.isEmpty())

        sync(state, PostVotePhase.FAILED, "t3_abc123", -1)
        assertEquals(0, state.effectivePostVote(post))
    }

    @Test
    fun anotherAccountsFlowStateClearsEveryOverride() {
        val (state, _) = authenticatedState()
        state.vote(post, 1)
        state.vote(comment, -1)

        sync(state, PostVotePhase.SUCCEEDED, "t3_abc123", 1, accountStableId = "reddit:other")

        assertEquals(0, state.effectivePostVote(post))
        assertEquals(0, state.effectiveCommentVote(comment))
        assertTrue(state.postVoteControlEnabled(post))
        assertTrue(state.commentVoteControlEnabled(comment))
    }

    @Test
    fun authenticatedCommentVoteUsesTheCommentFullnameAndTheSameReconciliation() {
        val (state, requests) = authenticatedState()

        state.vote(comment, -1)

        assertEquals(listOf(PostVoteRequest(account.stableId, "t1_def456", -1)), requests)
        assertEquals(-1, state.effectiveCommentVote(comment))
        assertEquals(4, state.effectiveCommentScore(comment))
        assertFalse(state.commentVoteControlEnabled(comment))
        assertTrue(state.postVoteControlEnabled(post))

        sync(state, PostVotePhase.SUCCEEDED, "t1_def456", -1)
        assertEquals(-1, state.effectiveCommentVote(comment))
        assertTrue(state.commentVoteControlEnabled(comment))
    }

    @Test
    fun localVotesToggleThePostAndCommentMapsWithoutARequester() {
        val state = SubshellState()
        assertFalse(state.activeAccountIsAuthenticated)
        var requested = false
        state.setAuthenticatedPostVoteRequester { requested = true }

        state.vote(post, 1)
        assertEquals(1, state.votes["abc123"])
        assertEquals(1, state.effectivePostVote(post))
        state.vote(post, 1)
        assertFalse("abc123" in state.votes)

        state.vote(comment, -1)
        assertEquals(-1, state.effectiveCommentVote(comment))
        state.vote(comment, -1)
        assertEquals(0, state.effectiveCommentVote(comment))

        assertFalse(requested)
        assertTrue(state.postVoteControlEnabled(post))
        assertTrue(state.commentVoteControlEnabled(comment))
    }
}
