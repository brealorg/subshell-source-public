package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostSaveUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterises SubshellState's save handling before it moves to a
 * delegate: the authenticated optimistic override and saved-list
 * membership, one request at a time with the same target's last intent
 * queued (ISSUE16), reconciliation of the save flow's phases (success
 * keeps or dispatches the queued follow-up, failure rolls back override and
 * membership, another account clears), comment saves, and local saves.
 */
class PostSaveStateCharacterisationTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:saver",
        username = "saver",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val post = SampleData.posts.first().copy(id = "abc123", saved = false)
    private val other = SampleData.posts.first().copy(id = "xyz789", saved = false)
    private val comment = SampleData.comments.first().copy(id = "def456", saved = false)

    private fun authenticatedState(): Pair<SubshellState, MutableList<PostSaveRequest>> {
        val requests = mutableListOf<PostSaveRequest>()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.setAuthenticatedPostSaveRequester { request -> requests += request }
        return state to requests
    }

    private fun sync(
        state: SubshellState,
        phase: PostSavePhase,
        fullname: String,
        saved: Boolean,
        accountStableId: String? = account.stableId,
    ) {
        state.synchronizePostSaveState(
            PostSaveUiState(
                phase = phase,
                accountStableId = accountStableId,
                postFullname = fullname,
                saved = saved,
            ),
        )
    }

    @Test
    fun authenticatedSaveIsOptimisticAndSerializesOtherTargets() {
        val (state, requests) = authenticatedState()
        assertTrue(state.authenticatedPostSavingEnabled)
        assertTrue(state.postSaveControlEnabled(post))

        state.toggleSaved(post)

        assertEquals(listOf(PostSaveRequest(account.stableId, "t3_abc123", true)), requests)
        assertTrue(state.effectivePostSaved(post))
        assertTrue("abc123" in state.savedPostIds)
        assertFalse(state.authenticatedPostSavingEnabled)
        assertTrue(state.postSaveControlEnabled(post))
        assertFalse(state.postSaveControlEnabled(other))

        state.toggleSaved(other)
        assertEquals(1, requests.size)
        assertFalse(state.effectivePostSaved(other))
    }

    @Test
    fun successWithoutAQueuedIntentKeepsTheStateAndReleasesTheTarget() {
        val (state, requests) = authenticatedState()
        state.toggleSaved(post)

        sync(state, PostSavePhase.SUCCEEDED, "t3_abc123", true)

        assertEquals(1, requests.size)
        assertTrue(state.effectivePostSaved(post))
        assertTrue("abc123" in state.savedPostIds)
        assertTrue(state.authenticatedPostSavingEnabled)
        assertTrue(state.postSaveControlEnabled(other))
    }

    @Test
    fun sameTargetPressWhileInFlightQueuesTheLastIntentAndSendsItAfterSuccess() {
        val (state, requests) = authenticatedState()
        state.toggleSaved(post)

        state.toggleSaved(post)

        assertEquals(1, requests.size)
        assertFalse(state.effectivePostSaved(post))
        assertFalse("abc123" in state.savedPostIds)

        sync(state, PostSavePhase.SUCCEEDED, "t3_abc123", true)

        assertEquals(PostSaveRequest(account.stableId, "t3_abc123", false), requests.last())
        assertEquals(2, requests.size)
        assertFalse(state.effectivePostSaved(post))
        assertFalse(state.authenticatedPostSavingEnabled)

        sync(state, PostSavePhase.SUCCEEDED, "t3_abc123", false)
        assertEquals(2, requests.size)
        assertFalse(state.effectivePostSaved(post))
        assertTrue(state.authenticatedPostSavingEnabled)
    }

    @Test
    fun queuedIntentEqualToTheAcknowledgedStateSendsNothingMore() {
        val (state, requests) = authenticatedState()
        state.toggleSaved(post)
        state.toggleSaved(post)
        state.toggleSaved(post)
        assertTrue(state.effectivePostSaved(post))

        sync(state, PostSavePhase.SUCCEEDED, "t3_abc123", true)

        assertEquals(1, requests.size)
        assertTrue(state.effectivePostSaved(post))
        assertTrue(state.authenticatedPostSavingEnabled)
    }

    @Test
    fun failureRollsBackOverrideAndMembershipAndDropsTheQueue() {
        val (state, requests) = authenticatedState()
        state.toggleSaved(post)
        state.toggleSaved(post)
        state.toggleSaved(post)

        sync(state, PostSavePhase.FAILED, "t3_abc123", true)

        assertEquals(1, requests.size)
        assertFalse(state.effectivePostSaved(post))
        assertFalse("abc123" in state.savedPostIds)
        assertTrue(state.authenticatedPostSavingEnabled)

        sync(state, PostSavePhase.SUCCEEDED, "t3_abc123", true)
        assertEquals(1, requests.size)
    }

    @Test
    fun inFlightPhaseFromTheFlowAppliesTheOverrideAndBlocksOtherTargets() {
        val (state, requests) = authenticatedState()

        sync(state, PostSavePhase.AWAITING_CALLBACK, "t3_abc123", true)

        assertTrue(state.effectivePostSaved(post))
        assertFalse(state.postSaveControlEnabled(other))
        assertTrue(requests.isEmpty())

        sync(state, PostSavePhase.FAILED, "t3_abc123", true)
        assertFalse(state.effectivePostSaved(post))
        assertTrue(state.postSaveControlEnabled(other))
    }

    @Test
    fun anotherAccountsFlowStateClearsEverything() {
        val (state, _) = authenticatedState()
        state.toggleSaved(post)

        sync(state, PostSavePhase.SUCCEEDED, "t3_abc123", true, accountStableId = "reddit:other")

        assertFalse(state.effectivePostSaved(post))
        assertTrue(state.authenticatedPostSavingEnabled)
        assertTrue(state.postSaveControlEnabled(other))
    }

    @Test
    fun commentSaveUsesTheSameQueueWithoutTouchingTheSavedPostList() {
        val (state, requests) = authenticatedState()
        val savedBefore = state.savedPostIds.toList()
        assertTrue(state.commentSaveControlEnabled(comment))

        state.toggleSaved(comment)

        assertEquals(listOf(PostSaveRequest(account.stableId, "t1_def456", true)), requests)
        assertTrue(state.effectiveCommentSaved(comment))
        assertFalse(state.postSaveControlEnabled(post))

        state.toggleSaved(comment)
        assertFalse(state.effectiveCommentSaved(comment))
        sync(state, PostSavePhase.SUCCEEDED, "t1_def456", true)

        assertEquals(PostSaveRequest(account.stableId, "t1_def456", false), requests.last())
        assertEquals(savedBefore, state.savedPostIds.toList())
    }

    @Test
    fun localSavesToggleTheSavedListAndCommentsCannotBeSaved() {
        val state = SubshellState()
        assertFalse(state.activeAccountIsAuthenticated)
        var requested = false
        state.setAuthenticatedPostSaveRequester { requested = true }
        assertTrue(state.postSaveControlEnabled(post))
        assertFalse(state.commentSaveControlEnabled(comment))

        state.toggleSaved(post)
        assertTrue("abc123" in state.savedPostIds)
        assertTrue(state.effectivePostSaved(post))
        state.toggleSaved(post)
        assertFalse("abc123" in state.savedPostIds)

        state.toggleSaved(comment)
        assertFalse(state.effectiveCommentSaved(comment))
        assertFalse(requested)
        assertFalse(state.authenticatedPostSavingEnabled)
    }
}
