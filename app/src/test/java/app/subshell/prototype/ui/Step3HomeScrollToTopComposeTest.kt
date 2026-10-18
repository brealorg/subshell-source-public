package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.HomeScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (scroll01): choosing Home again while on Home brings the feed back to
 * its first post, once. Leaving Home (a post opens over it) and coming back
 * does not replay that request: the feed returns where the reader left it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3HomeScrollToTopComposeTest {
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

    private var homeShown by mutableStateOf(true)

    private fun compose(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            repository = TextPostsRepository(),
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    if (homeShown) HomeScreen(state = state)
                }
            }
        }
        composeRule.waitUntil(10_000) { shown(FIRST) }
        return state
    }

    private fun shown(title: String) = composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()

    private fun scrollAwayFromTheTop() {
        repeat(4) {
            composeRule.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        assertTrue("the feed has left its first post", !shown(FIRST))
    }

    @Test
    fun choosingHomeAgainBringsTheFeedBackToItsFirstPost() {
        val state = compose()
        scrollAwayFromTheTop()

        composeRule.runOnIdle { state.navigate(MainDestination.HOME) }
        composeRule.waitForIdle()

        assertTrue("the first post is back on screen", shown(FIRST))
    }

    @Test
    fun comingBackToHomeDoesNotReplayAnEarlierScrollToTop() {
        val state = compose()
        composeRule.runOnIdle { state.navigate(MainDestination.HOME) }
        composeRule.waitForIdle()
        scrollAwayFromTheTop()

        // A post opens over Home and Back returns to it.
        composeRule.runOnIdle { homeShown = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { homeShown = true }
        composeRule.waitForIdle()

        assertTrue("the feed is where the reader left it, not at the top", !shown(FIRST))
    }

    private companion object {
        const val FIRST = "Home post 0"
    }
}

/** Forty short text posts for every feed. */
private class TextPostsRepository(
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    private val posts = (0 until 40).map { index ->
        Post(
            "t3_home$index",
            "homelab",
            "author$index",
            "1h",
            "Home post $index",
            score = index,
            comments = 0,
            kind = PostKind.TEXT,
        )
    }

    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> =
        if (request.cursor == null) RepositoryResult.Content(posts, nextCursor = null) else RepositoryResult.Empty()
}
