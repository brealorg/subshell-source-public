package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUDIT298C_LANDING_SPACE_SHRINKS_OUT_OF_SIGHT_V1: a comment link to the last
 * comment of a thread lands with space below it; the first drag released the
 * link's hold, the space vanished, and the list snapped by up to 0.6 screen.
 * Pure helpers for the reading line, the jump target and the pulse are here too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Audit298cLandingSpaceComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val post: Post = SampleData.posts.first().copy(id = "t3_p298ls", comments = 30)
    private val targetId = "c29"

    private fun comment(id: String) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "Comment $id",
        score = 1,
        depth = 0,
        accent = 0xFF336699,
    )

    @Test
    fun aSmallDragAfterLandingAtTheEndMovesTheListOnlyByTheDrag() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val shown = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = RedditCommentSort.BEST,
            comments = (0 until 30).map { comment("c$it") },
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    routeFocusedCommentId = targetId,
                    redditCommentsState = shown,
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        fun top() = composeRule.onNodeWithText("Comment $targetId").fetchSemanticsNode().boundsInRoot.top
        val landedTop = top()

        // The reader pulls the list down a little, slowly (no fling).
        composeRule.onNode(hasScrollToKeyAction()).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, 12f), delayMillis = 50) }
            moveBy(Offset(0f, 0f), delayMillis = 400)
            up()
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        val moved = top() - landedTop
        assertTrue("moved with the finger only (moved=$moved px)", moved in 0f..200f)
    }

    @Test
    fun theSpaceOnlyShrinksOnceTheLinkLetsGo() {
        assertEquals(500, focusedLandingSpaceToKeepPx(anchorOwned = true, keptPx = 0, onScreenPx = 500))
        assertEquals(400, focusedLandingSpaceToKeepPx(anchorOwned = false, keptPx = 500, onScreenPx = 400))
        // Scrolled back down to the end: it does not grow again.
        assertEquals(400, focusedLandingSpaceToKeepPx(anchorOwned = false, keptPx = 400, onScreenPx = 600))
        assertEquals(0, focusedLandingSpaceToKeepPx(anchorOwned = false, keptPx = 400, onScreenPx = 0))
        assertEquals(300, focusedLandingSpaceOnScreenPx(itemOffsetPx = 1_700, itemSizePx = 1_100, viewportEndOffset = 2_000))
        assertEquals(0, focusedLandingSpaceOnScreenPx(itemOffsetPx = 2_100, itemSizePx = 1_100, viewportEndOffset = 2_000))
    }

    @Test
    fun readingStartsBelowTheStuckHeader() {
        // AUDIT298C_REPLY_TARGET_NOT_UNDER_THE_HEADER_V1
        assertEquals(140, commentReadingTopPx(viewportStartOffset = 0, headerBottomPx = 140))
        assertEquals(0, commentReadingTopPx(viewportStartOffset = 0, headerBottomPx = null))
        // A reply target under the header is moved down below it.
        assertEquals(
            -100,
            replyTargetScrollDeltaPx(
                itemTopPx = 40,
                itemSizePx = 200,
                viewportStartPx = commentReadingTopPx(0, 140),
                availableBottomPx = 1_500,
                marginPx = 8,
            ),
        )
    }

    @Test
    fun theReadingRowIsTheFirstRowBelowTheHeaderThatStays() {
        fun row(id: String) = CommentViewportRow(kind = CommentViewportRowKind.COMMENT, comment = comment(id))
        val oldRows = listOf(row("a"), row("b"), row("c"))
        val newRows = listOf(row("x"), row("a"), row("c"))
        val header = CommentViewportVisibleItem(COMMENT_CONTROLS_ITEM_KEY, 3, 0, 140)
        val visible = listOf(
            header,
            CommentViewportVisibleItem("comment:a", 4, -60, 180),
            CommentViewportVisibleItem("comment:b", 5, 120, 300),
            CommentViewportVisibleItem("comment:c", 6, 420, 300),
        )
        // a ends under the header (bottom 120 < 140); b is read but goes away; c stays.
        val anchor = commentReadingAnchor(visible, oldRows, newRows, readingTopPx = 140, headerKey = COMMENT_CONTROLS_ITEM_KEY)
        assertEquals("comment:c", anchor?.key)
        assertEquals(420, anchor?.offsetPx)
        // The post itself on screen: Compose's own hold.
        val withPost = listOf(CommentViewportVisibleItem("post-body", 1, 0, 600)) + visible
        assertEquals(null, commentReadingAnchor(withPost, oldRows, newRows, 140, COMMENT_CONTROLS_ITEM_KEY))
        // Nothing changed: nothing to hold.
        assertEquals(null, commentReadingAnchor(visible, oldRows, oldRows, 140, COMMENT_CONTROLS_ITEM_KEY))
        // b reaches under the header (top 120 < 140): c is the first row whose top is read.
        assertEquals("comment:c", commentReadingRowToSave(visible, 140, COMMENT_CONTROLS_ITEM_KEY)?.key)
        // One tall row fills the screen: it is the place.
        val tall = listOf(header, CommentViewportVisibleItem("comment:t", 4, -900, 3_000))
        assertEquals("comment:t", commentReadingRowToSave(tall, 140, COMMENT_CONTROLS_ITEM_KEY)?.key)
        assertFalse(commentReadingRowToSave(withPost, 140, COMMENT_CONTROLS_ITEM_KEY) != null)
    }
}
