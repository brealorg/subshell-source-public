package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.app.publishedCommentOf
import app.subshell.prototype.ui.screens.PostScreen
import app.subshell.prototype.ui.state.PublishedCommentTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMMENTJUMP01_THE_THREAD_SCROLLS_TO_A_POSTED_COMMENT_V1: after posting, the
 * thread scrolls to the reader's new comment instead of leaving them at the
 * top of the post; the target is used once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CommentJump01ComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun comment(id: String, body: String) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0xFF336699,
    )

    @Test
    fun aPostedCommentIsScrolledIntoView() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = SampleData.posts.first().copy(id = "t3_jump", comments = 31)
        val thread = (1..30).map { index -> comment("t1_c$index", "Older comment $index") } +
            comment("t1_mine", "My new comment")
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            comments = thread,
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(post = post, state = state, onBack = {}, redditCommentsState = comments)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("My new comment").assertDoesNotExist()

        // As the comment-write success does (Reddit names the post as a fullname).
        composeRule.runOnIdle { state.notePublishedComment("t3_jump", "t1_mine") }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("My new comment").assertIsDisplayed()
        assertNull(state.publishedComment)
    }

    @Test
    fun aSuccessfulWriteForTheActiveAccountNamesItsComment() {
        fun written(account: String, phase: CommentWritePhase = CommentWritePhase.SUCCEEDED) = CommentWriteUiState(
            phase = phase,
            accountStableId = account,
            postId = "t3_jump",
            targetStableKey = "post:t3_jump",
            submittedComment = comment("t1_mine", "My new comment").takeIf { phase == CommentWritePhase.SUCCEEDED },
        )

        assertEquals("t3_jump" to "t1_mine", publishedCommentOf(written("reddit:me"), "reddit:me"))
        // Finished after a switch to another account: nothing moves.
        assertNull(publishedCommentOf(written("reddit:me"), "reddit:other"))
        assertNull(publishedCommentOf(written("reddit:me", CommentWritePhase.FAILED), "reddit:me"))
    }

    @Test
    fun aTargetBelongsToItsPostWithOrWithoutTheFullnamePrefix() {
        val target = PublishedCommentTarget(postId = "t3_abc", commentId = "t1_x")

        assertTrue(target.isOn("abc"))
        assertTrue(target.isOn("t3_abc"))
        assertFalse(target.isOn("t3_other"))
    }

    @Test
    fun aLocalCommentIsAJumpTargetToo() {
        val state = SubshellState()
        val post = SampleData.posts.first()
        assertTrue(state.openCommentComposer(post))
        state.updateCommentComposerDraft(requireNotNull(state.commentComposerDraft).copy(body = "A local reply."))

        val created = requireNotNull(state.publishLocalCommentComposer(post))

        assertEquals(PublishedCommentTarget(post.id, created.id), state.publishedComment)
    }

    @Test
    fun anOlderTargetIsNotClearedByConsumingAnother() {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.notePublishedComment("t3_a", "t1_first")
        val first = state.publishedComment!!
        state.notePublishedComment("t3_a", "t1_second")

        state.consumePublishedComment(first)

        assertEquals("t1_second", state.publishedComment?.commentId)
    }
}
