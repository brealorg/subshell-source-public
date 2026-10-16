package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditBlockedAccountsUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterises SubshellState's content-safety handling before it moves to
 * a delegate: authenticated hide with override and hidden-list membership,
 * report and block, the single in-flight action shared by all three, the
 * flow's phases (success keeps, failure rolls back, another account clears
 * everything including the remote hidden projection), the authoritative
 * blocked-accounts list, and local hide.
 */
class ContentSafetyStateCharacterisationTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:guard",
        username = "guard",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val post = SampleData.posts.first().copy(id = "abc123", community = "android")
    private val other = SampleData.posts.first().copy(id = "xyz789")
    private val comment = SampleData.comments.first().copy(id = "def456")

    private class Requests {
        val visibility = mutableListOf<RedditPostVisibilityRequest>()
        val reports = mutableListOf<RedditContentReportRequest>()
        val blocks = mutableListOf<RedditUserBlockRequest>()
        val total: Int get() = visibility.size + reports.size + blocks.size
    }

    private fun authenticatedState(): Pair<SubshellState, Requests> {
        val requests = Requests()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester = { request -> requests.visibility += request },
            reportRequester = { request -> requests.reports += request },
            userBlockRequester = { request -> requests.blocks += request },
        )
        return state to requests
    }

    private fun sync(
        state: SubshellState,
        operation: ContentSafetyOperation?,
        phase: ContentSafetyPhase,
        fullname: String? = null,
        hidden: Boolean? = null,
        username: String? = null,
        blocked: Boolean? = null,
        accountStableId: String? = account.stableId,
        blockedAccounts: RedditBlockedAccountsUiState = RedditBlockedAccountsUiState(),
    ) {
        state.synchronizeContentSafetyState(
            ContentSafetyUiState(
                phase = phase,
                operation = operation,
                accountStableId = accountStableId,
                thingFullname = fullname,
                username = username,
                hidden = hidden,
                blocked = blocked,
                blockedAccounts = blockedAccounts,
            ),
        )
    }

    private fun assertAllControlsLocked(state: SubshellState) {
        assertFalse(state.postVisibilityControlEnabled(other))
        assertFalse(state.contentReportControlEnabled())
        assertFalse(state.userBlockControlEnabled("someone"))
    }

    private fun assertAllControlsFree(state: SubshellState) {
        assertTrue(state.postVisibilityControlEnabled(other))
        assertTrue(state.contentReportControlEnabled())
        assertTrue(state.userBlockControlEnabled("someone"))
    }

    @Test
    fun authenticatedHideIsOptimisticAndLocksEverySafetyAction() {
        val (state, requests) = authenticatedState()
        assertFalse(state.effectivePostHidden(post))
        assertAllControlsFree(state)

        state.togglePostHidden(post)

        assertEquals(listOf(RedditPostVisibilityRequest(account.stableId, "t3_abc123", true)), requests.visibility)
        assertTrue(state.effectivePostHidden(post))
        assertTrue("abc123" in state.hiddenPostIds)
        assertAllControlsLocked(state)

        state.togglePostHidden(post)
        state.report(post, "spam")
        state.setUserBlocked("someone", true)
        assertEquals(1, requests.total)
    }

    @Test
    fun hideSuccessKeepsAndFailureRollsBackOverrideAndMembership() {
        val (state, requests) = authenticatedState()
        state.togglePostHidden(post)
        sync(state, ContentSafetyOperation.SET_POST_HIDDEN, ContentSafetyPhase.SUCCEEDED, "t3_abc123", hidden = true)
        assertTrue(state.effectivePostHidden(post))
        assertAllControlsFree(state)

        state.togglePostHidden(post)
        assertEquals(false, requests.visibility.last().hidden)
        assertFalse(state.effectivePostHidden(post))
        assertFalse("abc123" in state.hiddenPostIds)

        sync(state, ContentSafetyOperation.SET_POST_HIDDEN, ContentSafetyPhase.FAILED, "t3_abc123", hidden = false)

        assertTrue(state.effectivePostHidden(post))
        assertTrue("abc123" in state.hiddenPostIds)
        assertAllControlsFree(state)
    }

    @Test
    fun theAuthoritativeHiddenHintCountsUntilThereIsAnOverride() {
        val (state, requests) = authenticatedState()
        assertTrue(state.effectivePostHidden(post, authoritativeHiddenHint = true))

        state.togglePostHidden(post, authoritativeHiddenHint = true)

        assertEquals(false, requests.visibility.single().hidden)
        assertFalse(state.effectivePostHidden(post, authoritativeHiddenHint = true))

        state.noteAuthoritativeHiddenPost("t3_xyz789")
        assertTrue("xyz789" in state.hiddenPostIds)
        assertTrue(state.effectivePostHidden(other))
    }

    @Test
    fun hidePhaseFromTheFlowAppliesTheOverrideAndLocks() {
        val (state, requests) = authenticatedState()

        sync(state, ContentSafetyOperation.SET_POST_HIDDEN, ContentSafetyPhase.SENDING, "t3_abc123", hidden = true)

        assertTrue(state.effectivePostHidden(post))
        assertTrue("abc123" in state.hiddenPostIds)
        assertAllControlsLocked(state)
        assertEquals(0, requests.total)

        sync(state, ContentSafetyOperation.SET_POST_HIDDEN, ContentSafetyPhase.FAILED, "t3_abc123", hidden = true)
        assertFalse(state.effectivePostHidden(post))
        assertFalse("abc123" in state.hiddenPostIds)
    }

    @Test
    fun reportSendsTheTrimmedReasonAndASuccessfulHidingReportHidesThePost() {
        val (state, requests) = authenticatedState()

        state.report(post, "   ")
        assertEquals(0, requests.total)

        state.report(post, "  spam ")

        val report = requests.reports.single()
        assertEquals(account.stableId, report.accountStableId)
        assertEquals("t3_abc123", report.thingFullname)
        assertEquals("spam", report.reason)
        assertEquals("android", report.communityName)
        assertAllControlsLocked(state)
        assertFalse(state.effectivePostHidden(post))

        sync(state, ContentSafetyOperation.REPORT_CONTENT, ContentSafetyPhase.SUCCEEDED, "t3_abc123", hidden = true)

        assertTrue(state.effectivePostHidden(post))
        assertTrue("abc123" in state.hiddenPostIds)
        assertAllControlsFree(state)
    }

    @Test
    fun commentReportUsesTheCommentFullnameAndFailureOnlyReleasesTheLock() {
        val (state, requests) = authenticatedState()

        state.report(comment, "harassment")

        val report = requests.reports.single()
        assertEquals("t1_def456", report.thingFullname)
        assertNull(report.communityName)
        assertAllControlsLocked(state)

        sync(state, ContentSafetyOperation.REPORT_CONTENT, ContentSafetyPhase.FAILED, "t1_def456")
        assertAllControlsFree(state)
    }

    @Test
    fun blockIsOptimisticRefusesSelfAndRollsBackOnFailure() {
        val (state, requests) = authenticatedState()
        assertFalse(state.userBlockControlEnabled("Guard"))
        state.setUserBlocked("Guard", true)
        assertEquals(0, requests.total)

        state.setUserBlocked("Spammer", true)

        assertEquals(listOf(RedditUserBlockRequest(account.stableId, "spammer", true)), requests.blocks)
        assertTrue(state.isUserBlocked("spammer"))
        assertTrue(state.userBlockInProgress("u/Spammer"))
        assertAllControlsLocked(state)

        sync(state, ContentSafetyOperation.SET_USER_BLOCKED, ContentSafetyPhase.FAILED, fullname = "u/spammer", username = "spammer", blocked = true)

        assertFalse(state.isUserBlocked("spammer"))
        assertFalse(state.userBlockInProgress("spammer"))
        assertAllControlsFree(state)

        state.setUserBlocked("spammer", true)
        sync(state, ContentSafetyOperation.SET_USER_BLOCKED, ContentSafetyPhase.SUCCEEDED, fullname = "u/spammer", username = "spammer", blocked = true)
        assertTrue(state.isUserBlocked("spammer"))
        assertAllControlsFree(state)
    }

    @Test
    fun theAuthoritativeBlockedListReplacesSettledOverrides() {
        val (state, _) = authenticatedState()
        state.setUserBlocked("spammer", true)
        sync(state, ContentSafetyOperation.SET_USER_BLOCKED, ContentSafetyPhase.SUCCEEDED, fullname = "u/spammer", username = "spammer", blocked = true)
        state.setUserBlocked("friend", false)
        sync(state, ContentSafetyOperation.SET_USER_BLOCKED, ContentSafetyPhase.SUCCEEDED, fullname = "u/friend", username = "friend", blocked = false)
        assertFalse(state.showsAuthoritativeBlockedAccount("friend"))

        val list = RedditBlockedAccountsUiState(
            accountStableId = account.stableId,
            items = listOf(
                RedditBlockedAccount(username = "Spammer", accountFullname = "t2_a"),
                RedditBlockedAccount(username = "troll", accountFullname = "t2_b"),
            ),
            loaded = true,
        )
        sync(state, null, ContentSafetyPhase.IDLE, blockedAccounts = list)

        assertTrue(state.isUserBlocked("spammer"))
        assertTrue(state.isUserBlocked("troll"))
        assertFalse(state.isUserBlocked("friend"))
        assertTrue(state.showsAuthoritativeBlockedAccount("friend"))

        sync(state, null, ContentSafetyPhase.IDLE, blockedAccounts = list.copy(items = list.items.drop(1)))
        assertFalse(state.isUserBlocked("spammer"))
        assertTrue(state.isUserBlocked("troll"))

        sync(state, null, ContentSafetyPhase.IDLE, blockedAccounts = list.copy(accountStableId = "reddit:other"))
        assertTrue(state.isUserBlocked("troll"))
        assertFalse(state.isUserBlocked("spammer"))
    }

    @Test
    fun anotherAccountsFlowStateClearsEverythingIncludingTheHiddenProjection() {
        val (state, _) = authenticatedState()
        state.noteAuthoritativeHiddenPost("t3_xyz789")
        state.togglePostHidden(post)
        state.setUserBlocked("spammer", true)

        sync(
            state,
            ContentSafetyOperation.SET_POST_HIDDEN,
            ContentSafetyPhase.SUCCEEDED,
            "t3_abc123",
            hidden = true,
            accountStableId = "reddit:other",
        )

        assertFalse(state.effectivePostHidden(post))
        assertTrue(state.hiddenPostIds.isEmpty())
        assertFalse(state.isUserBlocked("spammer"))
        assertAllControlsFree(state)
    }

    @Test
    fun anIdleStateWithoutAnOperationReleasesTheLock() {
        val (state, _) = authenticatedState()
        state.report(post, "spam")
        assertAllControlsLocked(state)

        sync(state, null, ContentSafetyPhase.IDLE, fullname = "t3_abc123")

        assertAllControlsFree(state)
    }

    @Test
    fun aLocalAccountHidesLocallyAndCannotReportOrBlock() {
        val state = SubshellState()
        assertFalse(state.activeAccountIsAuthenticated)
        var requested = false
        state.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester = { requested = true },
            reportRequester = { requested = true },
            userBlockRequester = { requested = true },
        )
        assertTrue(state.postVisibilityControlEnabled(post))
        assertFalse(state.contentReportControlEnabled())
        assertFalse(state.userBlockControlEnabled("someone"))

        state.togglePostHidden(post)
        assertTrue(state.effectivePostHidden(post))
        state.togglePostHidden(post)
        assertFalse(state.effectivePostHidden(post))
        state.hidePost("xyz789")
        assertTrue(state.effectivePostHidden(other))

        state.noteAuthoritativeHiddenPost("t3_zzz111")
        assertFalse("zzz111" in state.hiddenPostIds)
        state.report(post, "spam")
        state.setUserBlocked("someone", true)
        assertFalse(state.isUserBlocked("someone"))
        assertFalse(requested)
    }
}
