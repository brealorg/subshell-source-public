package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
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
 * ISSUE124_INBOX_TARGET_FAILURES_ARE_SHOWN_V1 / ISSUE107_ONE_OPEN_PER_INBOX_ITEM_V1:
 * a reply opened from the inbox is read from Reddit first. A failure did
 * nothing, a repeat tap pushed the post twice, and a late answer opened over
 * whatever screen showed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue124InboxTargetOpenComposeTest {
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
    private val reply = InboxItem(
        id = "t1_reply124",
        kind = ActivityKind.REPLY,
        title = "A reply to open",
        sender = "someone_else",
        age = "1h",
        preview = "Hello",
        unread = true,
        targetPostId = "t3_p124",
        targetCommentId = "t1_c124",
    )
    private val post: Post = SampleData.posts.first().copy(id = "t3_p124")
    private val noticeHost = ViewportErrorHostState()
    private val answers = mutableListOf<(Post?) -> Unit>()

    private fun inbox(): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalViewportErrorHostState provides noticeHost) {
                InboxScreen(
                    state = state,
                    redditInboxState = RedditInboxUiState(
                        accountStableId = account.stableId,
                        items = listOf(reply),
                        loaded = true,
                    ),
                    onResolveRedditInboxTarget = { _, _, onResolved -> answers += onResolved },
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun tapTheReply() {
        composeRule.onNodeWithText("A reply to open", substring = true).performClick()
        composeRule.waitForIdle()
    }

    private fun answer(post: Post?) {
        composeRule.runOnIdle { answers.forEach { it(post) } }
        composeRule.waitForIdle()
    }

    @Test
    fun aRepeatTapReadsOnceShowsProgressAndOpensOnce() {
        val state = inbox()
        val depthBefore = state.navigationRouteDepth

        tapTheReply()
        tapTheReply()
        assertEquals(1, answers.size)
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Opening").fetchSemanticsNodes().size)

        answer(post)

        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertEquals(depthBefore + 1, state.navigationRouteDepth)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Opening").fetchSemanticsNodes().size)
    }

    @Test
    fun aPostThatCannotBeLoadedSaysSo() {
        inbox()
        tapTheReply()

        answer(null)

        assertEquals("Couldn't open this post", noticeHost.currentRequest?.presentation?.title)
    }

    @Test
    fun anAnswerAfterLeavingTheInboxIsDropped() {
        val state = inbox()
        tapTheReply()
        composeRule.runOnIdle { state.navigate(MainDestination.SUBS) }
        composeRule.waitForIdle()

        answer(post)

        assertFalse(state.currentNavigationRoute is SubshellRoute.Post)
    }
}
