package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.redditAccountHistoryPostQueryKey
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (safety01a3 G6/G7): SubshellApp hands Hide and Report from
 * SubshellState to the activity's callbacks for the active account; a safety
 * action that needs authorization asks for it once per action; and after
 * Reddit confirms a hide with its hidden history refreshed, the active
 * account's Hidden list is read again (another account's answer reads
 * nothing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ContentSafetyBridgeComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val bob = alice.copy(stableId = "reddit:bob", username = "bob")
    private val post = Post(
        id = "t3_aaa",
        community = "homelab",
        author = "someone",
        age = "1h",
        title = "A post",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )
    private val repository = HiddenKeyCountingRepository()
    private val hiddenKey: RepositoryQueryKey = redditAccountHistoryPostQueryKey(
        accountStableId = alice.stableId,
        username = alice.username,
        kind = RedditAccountHistoryKind.HIDDEN,
    )

    private var safetyState by mutableStateOf(ContentSafetyUiState())
    private val visibility = mutableListOf<RedditPostVisibilityRequest>()
    private val reports = mutableListOf<RedditContentReportRequest>()
    private var authorizations = 0

    private fun compose(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            repository = repository,
            initialAccountSessions = listOf(alice, bob),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                contentSafetyState = safetyState,
                onSetPostHidden = { request -> visibility += request },
                onReportContent = { request -> reports += request },
                onAuthorizeContentSafety = { authorizations += 1 },
            )
        }
        composeRule.waitForIdle()
        return state
    }

    private fun hideSucceeded(account: String, revision: Long) = ContentSafetyUiState(
        phase = ContentSafetyPhase.SUCCEEDED,
        operation = ContentSafetyOperation.SET_POST_HIDDEN,
        accountStableId = account,
        thingFullname = post.id,
        hidden = true,
        hiddenHistoryReconciliation = HiddenHistoryReconciliation.REFRESHED,
        completionRevision = revision,
    )

    @Test
    fun hideAndReportReachTheActivityForTheActiveAccount() {
        val state = compose()

        composeRule.runOnIdle { state.togglePostHidden(post) }
        assertEquals(listOf(RedditPostVisibilityRequest(alice.stableId, post.id, true)), visibility)

        // Reddit's answer ends the hide; the next safety action may start.
        composeRule.runOnIdle { safetyState = hideSucceeded(alice.stableId, revision = 1L) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.report(post, "spam") }

        assertEquals(listOf(RedditContentReportRequest(alice.stableId, post.id, "spam", "homelab")), reports)
    }

    @Test
    fun anActionThatNeedsAuthorizationAsksOncePerAction() {
        compose()
        val needsGrant = ContentSafetyUiState(
            phase = ContentSafetyPhase.AUTHORIZATION_REQUIRED,
            operation = ContentSafetyOperation.SET_POST_HIDDEN,
            accountStableId = alice.stableId,
            thingFullname = post.id,
            hidden = true,
        )

        composeRule.runOnIdle { safetyState = needsGrant }
        composeRule.waitForIdle()
        assertEquals(1, authorizations)

        // The same action, re-published, does not ask again.
        composeRule.runOnIdle { safetyState = needsGrant.copy(statusCode = 403) }
        composeRule.waitForIdle()
        assertEquals(1, authorizations)

        // Another post's action asks again.
        composeRule.runOnIdle { safetyState = needsGrant.copy(thingFullname = "t3_bbb") }
        composeRule.waitForIdle()
        assertEquals(2, authorizations)
    }

    @Test
    fun aConfirmedHideReadsTheActiveAccountsHiddenListAgainOnly() {
        compose()
        val before = repository.loadsFor(hiddenKey)

        // Another account's confirmation reads nothing (the reload would read
        // the active account's list, so count that list's reads).
        composeRule.runOnIdle { safetyState = hideSucceeded(bob.stableId, revision = 1L) }
        composeRule.waitForIdle()

        composeRule.runOnIdle { safetyState = hideSucceeded(alice.stableId, revision = 2L) }
        composeRule.waitForIdle()

        // One read: the active account's confirmation only.
        assertEquals(before + 1, repository.loadsFor(hiddenKey))
    }
}

/** FakeSubshellRepository that counts post page loads per query key. */
private class HiddenKeyCountingRepository(
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    private val loads = java.util.concurrent.ConcurrentHashMap<RepositoryQueryKey, Int>()

    fun loadsFor(key: RepositoryQueryKey): Int = loads[key] ?: 0

    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        loads.merge(request.queryKey, 1, Int::plus)
        return delegate.posts(request)
    }
}
