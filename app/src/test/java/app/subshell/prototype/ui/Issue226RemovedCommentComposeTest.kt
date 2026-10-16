package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.screens.PostScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ISSUE226_REMOVED_COMMENT_IS_MARKED_V1: a removed comment says so in the thread. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue226RemovedCommentComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun onlyTheRemovedCommentCarriesTheRemovedLabel() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val account = AccountSessionSummary(
            stableId = "reddit:mod",
            username = "mod_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = SampleData.posts.first().copy(id = "t3_p226", comments = 2)
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            comments = listOf(
                Comment(id = "kept", author = "someone", age = "1h", body = "Still here", score = 1, depth = 0, accent = 0xFF336699),
                Comment(
                    id = "gone",
                    author = "someone_else",
                    age = "1h",
                    body = "Removed text",
                    score = 1,
                    depth = 0,
                    accent = 0xFF336699,
                    removedByModerator = true,
                ),
            ),
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Removed", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
