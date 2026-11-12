package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
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
 * AUDIT298C_RECREATION_DOES_NOT_LAND_AGAIN_V1: the landing on a linked
 * comment ran again from the start whenever the activity was recreated
 * (dark mode, font size): a second arrival pulse and a second read receipt;
 * the pulse also replayed when the comment scrolled off and back on screen;
 * and its refresh replaced the thread, so the replies under the comment changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Audit298cRecreationKeepsLandingComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p298rc", comments = 3)
    private val targetId = "c298rctarget"
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
    fun aRecreatedScreenDoesNotLandOrSendTheReadReceiptAgain() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val noticeHost = ViewportErrorHostState()
        val landed = mutableListOf<String>()
        val refreshes = mutableListOf<String>()
        val shown = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = RedditCommentSort.BEST,
            comments = listOf(comment("c298rca"), comment(targetId), comment("c298rcb")),
            contentRevision = 2L,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalViewportErrorHostState provides noticeHost,
            ) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    routeFocusedCommentId = targetId,
                    redditCommentsState = shown,
                    onFocusedCommentLandingVerified = { id -> landed += id },
                    onRefreshRedditCommentsPreservingFocusedTarget = { _, _, _, keep -> refreshes += keep },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        assertEquals(listOf(targetId), landed)
        val refreshesBefore = refreshes.size

        restoration.emulateSavedInstanceStateRestore()
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()

        assertEquals("one landing, one read receipt", listOf(targetId), landed)
        // The thread on screen is kept: no second refresh replaces what loaded.
        assertEquals(refreshesBefore, refreshes.size)
    }

    @Test
    fun anArrivalPulsesOnce() {
        assertFalse(focusedArrivalPulseShouldPlay(generation = 0, played = 0))
        assertTrue(focusedArrivalPulseShouldPlay(generation = 1, played = 0))
        // Back on screen, or after recreation: already played.
        assertFalse(focusedArrivalPulseShouldPlay(generation = 1, played = 1))
        // A Retry lands again and pulses again.
        assertTrue(focusedArrivalPulseShouldPlay(generation = 2, played = 1))
    }
}
