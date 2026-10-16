package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.inbox.InboxNotificationTarget
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.app.AppEffectsBridge
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.ModmailEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (inbox02a): a notification for a reply looks its post up by the
 * post's id (not the inbox item's), opens that post at the exact comment and
 * leaves the read mark to the thread that verifies the comment; a post reply
 * without a comment target is marked read once its post is open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3InboxNotificationTargetComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val post = SampleData.posts.first().copy(id = "t3_p1")
    private val resolved = mutableListOf<Pair<String, String>>()
    private val markedRead = mutableListOf<String>()

    private fun open(item: InboxItem): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val inbox = InboxEntryPoints(
            redditInboxState = RedditInboxUiState(
                accountStableId = alice.stableId,
                items = listOf(item),
                loaded = true,
                contentRevision = 1L,
            ),
            onLoadRedditInbox = { _, _ -> },
            onMarkRedditInboxRead = { _, id -> markedRead += id },
            onResolveRedditInboxTarget = { account, postId, answer ->
                resolved += account to postId
                answer(post)
            },
            inboxNotificationTarget = InboxNotificationTarget(
                accountStableId = alice.stableId,
                inboxItemId = item.id,
                eventKey = "event-${item.id}",
            ),
            onInboxNotificationConsumed = {},
        )
        composeRule.setContent {
            AppEffectsBridge(
                state = state,
                redditInbox = inbox,
                redditModmail = ModmailEntryPoints(),
                redditFeed = FeedEntryPoints(),
                inContext = false,
                drawerState = DrawerState(DrawerValue.Closed),
                youInteractionSource = MutableInteractionSource(),
                hapticFeedback = NoHapticFeedback,
                accountSwitcherVisibleState = mutableStateOf(false),
                suppressYouClickState = mutableStateOf(false),
            )
        }
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun aReplyNotificationOpensItsPostAtTheCommentAndLeavesTheReadMarkToTheThread() {
        val state = open(
            InboxItem(
                id = "t1_reply",
                kind = ActivityKind.REPLY,
                title = "comment reply",
                sender = "bob",
                age = "1m",
                preview = "Answer",
                unread = true,
                targetPostId = "t3_p1",
                targetCommentId = "t1_reply",
            ),
        )

        assertEquals(listOf(alice.stableId to "t3_p1"), resolved)
        val route = state.currentNavigationRoute as SubshellRoute.Post
        assertEquals("t3_p1", route.postRoute.postId)
        assertEquals("t1_reply", route.postRoute.focusedCommentId)
        assertEquals(emptyList<String>(), markedRead)
    }

    @Test
    fun aPostReplyWithoutACommentTargetIsMarkedReadOnceItsPostIsOpen() {
        val state = open(
            InboxItem(
                id = "t1_top",
                kind = ActivityKind.POST_REPLY,
                title = "post reply",
                sender = "bob",
                age = "1m",
                preview = "Answer",
                unread = true,
                targetPostId = "t3_p1",
            ),
        )

        assertEquals(listOf(alice.stableId to "t3_p1"), resolved)
        assertEquals("t3_p1", (state.currentNavigationRoute as SubshellRoute.Post).postRoute.postId)
        assertEquals(listOf("t1_top"), markedRead)
    }

    private object NoHapticFeedback : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }
}
