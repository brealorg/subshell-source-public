package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostFollowUiState
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
 * STEP3 (followpost01a1b): SubshellApp hands a follow from SubshellState to
 * the activity's post-follow runtime for the active account, and a follow
 * that needs authorization asks for it once per follow, not again when the
 * same state is published again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3PostFollowBridgeComposeTest {
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

    private var followState by mutableStateOf(PostFollowUiState())
    private val follows = mutableListOf<PostFollowRequest>()
    private var authorizations = 0

    private fun compose(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalPostFollowRuntime provides PostFollowRuntimeEntryPoints(
                    state = followState,
                    onSetFollowed = { request -> follows += request },
                    onAuthorize = { authorizations += 1 },
                ),
            ) {
                SubshellApp(
                    videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                    state = state,
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun aFollowReachesTheActivityForTheActiveAccount() {
        val state = compose()

        composeRule.runOnIdle { state.togglePostFollow(post) }

        assertEquals(listOf(PostFollowRequest(alice.stableId, "t3_aaa", followed = true)), follows)
    }

    @Test
    fun aFollowThatNeedsAuthorizationAsksOncePerFollow() {
        compose()
        val needsGrant = PostFollowUiState(
            phase = PostFollowPhase.AUTHORIZATION_REQUIRED,
            accountStableId = alice.stableId,
            postFullname = "t3_aaa",
            followed = true,
        )

        composeRule.runOnIdle { followState = needsGrant }
        composeRule.waitForIdle()
        assertEquals(1, authorizations)

        // The same follow, published again, does not ask again.
        composeRule.runOnIdle { followState = needsGrant.copy(completionRevision = 3L) }
        composeRule.waitForIdle()
        assertEquals(1, authorizations)

        // Another post's follow asks again.
        composeRule.runOnIdle { followState = needsGrant.copy(postFullname = "t3_bbb") }
        composeRule.waitForIdle()
        assertEquals(2, authorizations)
    }
}
