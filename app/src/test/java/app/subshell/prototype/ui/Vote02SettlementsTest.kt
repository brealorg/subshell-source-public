package app.subshell.prototype.ui

import app.subshell.prototype.auth.PostVoteCoordinator
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostVoteFailureKind
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VOTE02 (#60): the vote flow reaches SubshellState through collectAsState,
 * which keeps only the newest state. Votes on two targets in quick
 * succession used to lose the first one's end, leaving it stuck in flight
 * (and, after a failure, showing a vote Reddit never recorded).
 */
class Vote02SettlementsTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:voter",
        username = "voter",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val postA = SampleData.posts.first().copy(id = "aaa111", score = 10, vote = 0)
    private val postB = SampleData.posts.first().copy(id = "bbb222", score = 20, vote = 0)

    private fun authenticatedState(): Pair<SubshellState, MutableList<PostVoteRequest>> {
        val requests = mutableListOf<PostVoteRequest>()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.setAuthenticatedPostVoteRequester { request -> requests += request }
        return state to requests
    }

    private fun coordinator(
        result: (PostVoteRequest) -> AuthenticatedPostVoteResult,
    ) = PostVoteCoordinator(
        capability = null,
        writer = { request -> result(request) },
        clientMode = null,
        activeAccountStableId = { account.stableId },
    )

    @Test
    fun aSucceededVoteHiddenByALaterOneIsStillReleased() = runBlocking {
        val (state, requests) = authenticatedState()
        val votes = coordinator { request -> AuthenticatedPostVoteResult.Voted(request) }
        state.vote(postA, 1)
        state.vote(postB, 1)
        requests.forEach { votes.vote(it) }

        // Only the newest state reaches the UI: B's success.
        state.synchronizePostVoteState(votes.uiState.value)

        assertEquals("t3_bbb222", votes.uiState.value.postFullname)
        assertEquals(1, state.effectivePostVote(postA))
        assertTrue(state.postVoteControlEnabled(postA))
        assertTrue(state.postVoteControlEnabled(postB))
    }

    @Test
    fun aFailedVoteHiddenByALaterOneIsRolledBack() = runBlocking {
        val (state, requests) = authenticatedState()
        val votes = coordinator { request ->
            if (request.postFullname == "t3_aaa111") {
                AuthenticatedPostVoteResult.Failed(PostVoteFailureKind.HTTP, statusCode = 429)
            } else {
                AuthenticatedPostVoteResult.Voted(request)
            }
        }
        state.vote(postA, 1)
        state.vote(postB, 1)
        requests.forEach { votes.vote(it) }

        state.synchronizePostVoteState(votes.uiState.value)

        assertEquals(0, state.effectivePostVote(postA))
        assertEquals(10, state.effectivePostScore(postA))
        assertTrue(state.postVoteControlEnabled(postA))
        assertEquals(1, state.effectivePostVote(postB))
    }

    @Test
    fun aVoteOnAnotherTargetWhileOneWaitsForAuthorizationIsRefused() = runBlocking {
        val (state, requests) = authenticatedState()
        val sent = mutableListOf<PostVoteRequest>()
        val votes = coordinator { request ->
            sent += request
            AuthenticatedPostVoteResult.AuthorizationRequired
        }
        state.vote(postA, 1)
        votes.vote(requests[0])
        state.synchronizePostVoteState(votes.uiState.value)
        state.vote(postB, -1)
        votes.vote(requests[1])

        state.synchronizePostVoteState(votes.uiState.value)

        // A still waits for authorization; B was never sent and is rolled back.
        assertEquals(listOf("t3_aaa111"), sent.map { it.postFullname })
        assertEquals(PostVotePhase.AUTHORIZATION_REQUIRED, votes.uiState.value.phase)
        assertEquals("t3_aaa111", votes.uiState.value.postFullname)
        assertFalse(state.postVoteControlEnabled(postA))
        assertEquals(0, state.effectivePostVote(postB))
        assertTrue(state.postVoteControlEnabled(postB))
    }

    @Test
    fun anOldSettlementDoesNotEndANewVoteOnTheSameTarget() = runBlocking {
        val (state, requests) = authenticatedState()
        val votes = coordinator { request -> AuthenticatedPostVoteResult.Voted(request) }
        state.vote(postA, 1)
        votes.vote(requests[0])
        state.synchronizePostVoteState(votes.uiState.value)

        // A second vote on A is in flight; a later state still carries A's
        // first end among its settlements.
        state.vote(postA, 1)
        state.synchronizePostVoteState(
            votes.uiState.value.copy(
                phase = PostVotePhase.IDLE,
                postFullname = null,
                direction = null,
            ),
        )

        assertEquals(0, state.effectivePostVote(postA))
        assertFalse(state.postVoteControlEnabled(postA))
    }
}
