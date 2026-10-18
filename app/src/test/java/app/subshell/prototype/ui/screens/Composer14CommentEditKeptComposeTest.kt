package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER14_COMMENT_EDIT_KEPT_V1 (ui review round 3 C8): an open comment
 * edit survives the comment briefly leaving the loaded thread; it used to
 * close and lose the typed text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Composer14CommentEditKeptComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theLastCopyIsKeptUntilTheCommentComesBack() {
        val memory = EditingCommentMemory()
        val first = comment("First")
        assertNull(memory.keep(null))
        assertEquals(first, memory.keep(first))
        assertEquals(first, memory.keep(null))
        val updated = comment("Updated")
        assertEquals(updated, memory.keep(updated))
    }

    @Test
    fun anOpenEditKeepsItsTextWhileTheCommentIsBrieflyMissing() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "me",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = state.posts.first()
        val mine = comment("My comment")
        val comments = mutableStateOf(listOf(mine))
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = RedditCommentsUiState(
                        accountStableId = account.stableId,
                        postId = post.id,
                        sort = RedditCommentSort.BEST,
                        comments = comments.value,
                        fullDiscussionLoaded = true,
                    ),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("comment:t1_mine")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Comment options").onFirst().performClick()
        composeRule.onNodeWithText("Edit comment").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput(" edited")
        composeRule.waitForIdle()

        composeRule.runOnIdle { comments.value = emptyList() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { comments.value = listOf(mine) }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("My comment edited").fetchSemanticsNodes().size)
    }

    private fun comment(body: String) = Comment(
        id = "t1_mine", author = "me", age = "1h", body = body, score = 1, depth = 0, accent = 0L,
    )
}
