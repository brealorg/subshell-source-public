package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c4): an open post has "Add a comment" at the bottom; it
 * opens the composer in the same screen, naming the community and post (a
 * comment) or the person answered (a reply), and closing the composer brings
 * "Add a comment" back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3PostCommentComposerComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val post = Post(
        id = "t3_comment_bar",
        community = "android",
        author = "alice",
        age = "1h",
        title = "A post to comment on",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun show(): SubshellState {
        ensureWorkManagerInitialized(context)
        val accounts = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts.first().stableId,
            initialInteractionSideConfigured = true,
        )
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SubshellTheme { PostScreen(post = post, state = state, onBack = {}) }
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun addACommentOpensTheComposerInThePostAndClosingBringsItBack() {
        show()
        assertEquals(1, count("Add a comment"))

        composeRule.onAllNodes(hasText("Add a comment") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, count("Commenting on r/android · A post to comment on"))
        assertEquals(1, composeRule.onAllNodes(hasContentDescription("Post comment")).fetchSemanticsNodes().size)
        assertTrue(count("A post to comment on") >= 2)

        composeRule.onAllNodes(hasContentDescription("Close composer") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(0, count("Commenting on r/android"))
        assertEquals(1, count("Add a comment"))
    }

    @Test
    fun aReplyNamesThePersonAnswered() {
        val state = show()
        val comment = Comment(id = "t1_bob", author = "bob", age = "1h", body = "First!", score = 1, depth = 0, accent = 0xFF336699)

        composeRule.runOnIdle { state.openCommentComposer(post, comment) }
        composeRule.waitForIdle()

        assertEquals(1, count("Replying to u/bob · A post to comment on"))
        assertEquals(1, composeRule.onAllNodes(hasContentDescription("Post reply")).fetchSemanticsNodes().size)
    }
}
