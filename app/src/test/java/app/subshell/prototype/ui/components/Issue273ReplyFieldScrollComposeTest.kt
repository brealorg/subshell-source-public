package app.subshell.prototype.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.CommentLoadingEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.screens.ConversationScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.ThemeMode
import app.subshell.prototype.visual.VisualFixtures
import app.subshell.prototype.visual.VisualIdentityRepository
import app.subshell.prototype.visual.VisualRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE273_ONLY_THE_THREAD_MOVES_THE_TOP_BAR_V1 (#273, reader report): a
 * drag in the reply field over a post or a conversation must not move the
 * screen behind it; a drag in the thread still hides the top bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue273ReplyFieldScrollComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val longText = (1..40).joinToString("\n") { "Line $it of a long reply" }

    /** The top bar's Back button: it moves with the bar. */
    private fun barTop() =
        composeRule.onAllNodesWithContentDescription("Back").onFirst().getBoundsInRoot().top

    private fun replyField() = composeRule
        .onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag(COMPACT_REPLY_COMPOSER_TEST_TAG)))
        .onFirst()

    @Test
    fun aDragInTheCommentReplyFieldLeavesThePostScreenAlone() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            initialThemeMode = ThemeMode.DARK,
            initialInteractionSideConfigured = true,
            initialAccountSessions = VisualFixtures.accounts,
            initialActiveAccountId = VisualFixtures.active.stableId,
            repository = VisualRepository(),
            identityMetadataRepository = VisualIdentityRepository,
        )
        var comments by mutableStateOf(RedditCommentsUiState())
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = VisualFixtures.subscriptions),
                redditComments = CommentLoadingEntryPoints(
                    redditCommentsState = comments,
                    onLoadRedditComments = { _, postId, sort ->
                        val post = VisualFixtures.original(postId)
                        comments = VisualFixtures.comments(postId, sort, state.commentsForPost(post))
                    },
                ),
            )
        }
        composeRule.waitForIdle()
        val post = VisualFixtures.posts.first { it.kind == PostKind.TEXT }
        composeRule.runOnIdle {
            state.openFromMainFeed(post)
            state.openCommentComposer(post)
        }
        composeRule.waitForIdle()
        replyField().performTextReplacement(longText)
        composeRule.waitForIdle()
        val before = barTop()

        replyField().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertEquals("the top bar did not move", before.value, barTop().value, 0.5f)

        // The thread itself still hides the bar.
        composeRule.onAllNodesWithText(post.title).onFirst().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("a drag in the thread hides the bar", barTop() < before)
    }

    @Test
    fun aDragInTheConversationReplyFieldLeavesTheConversationAlone() {
        val item = SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }
        composeRule.setContent {
            SubshellTheme {
                Box(Modifier.fillMaxSize()) {
                    ConversationScreen(item = item, onBack = {}, replyParentFullname = "t4_reply")
                }
            }
        }
        composeRule.waitForIdle()
        replyField().performTextReplacement(longText)
        composeRule.waitForIdle()
        val before = barTop()

        replyField().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertEquals("the top bar did not move", before.value, barTop().value, 0.5f)
    }
}
