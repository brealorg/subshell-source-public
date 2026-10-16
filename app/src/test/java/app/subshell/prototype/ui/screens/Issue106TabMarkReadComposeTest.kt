package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE106_TAB_MARK_READ_STAYS_IN_ITS_TAB_V1: a tab's "mark all read" used
 * api/read_all_messages when the other tab showed nothing unread, which
 * marked unloaded and newly arrived private messages read on Reddit. It now
 * marks only that tab's loaded unread items, one by one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue106TabMarkReadComposeTest {
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

    private fun item(id: String, kind: ActivityKind, unread: Boolean = true) = InboxItem(
        id = id,
        kind = kind,
        title = "Title $id",
        sender = "someone_else",
        age = "1h",
        preview = "Preview $id",
        unread = unread,
    )

    private fun inboxWith(items: List<InboxItem>, seen: Set<String> = emptySet()): List<Pair<String, String>> {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val reads = mutableListOf<Pair<String, String>>()
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(
                    accountStableId = account.stableId,
                    items = items,
                    locallySeenIds = seen,
                    loaded = true,
                ),
                onMarkRedditInboxRead = { accountId, itemId -> reads += accountId to itemId },
            )
        }
        composeRule.waitForIdle()
        return reads
    }

    @Test
    fun markAllActivityReadMarksOnlyTheLoadedActivity() {
        val reads = inboxWith(
            listOf(
                item("t1_reply1", ActivityKind.REPLY),
                item("t1_reply2", ActivityKind.REPLY, unread = false),
                item("t1_mention", ActivityKind.MENTION),
            ),
        )

        composeRule.onNodeWithContentDescription("Mark all activity as read").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("reddit:me" to "t1_reply1", "reddit:me" to "t1_mention"), reads)
    }

    @Test
    fun markAllMessagesReadMarksOnlyTheLoadedMessages() {
        val reads = inboxWith(listOf(item("t4_message", ActivityKind.MESSAGE)))

        composeRule.onNodeWithText("Messages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Mark all messages as read").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("reddit:me" to "t4_message"), reads)
    }

    @Test
    fun anItemSeenHereButStillUnreadOnRedditIsSentAgain() {
        val reads = inboxWith(
            listOf(item("t1_unseen", ActivityKind.REPLY), item("t1_seen", ActivityKind.REPLY)),
            seen = setOf("t1_seen"),
        )

        composeRule.onNodeWithContentDescription("Mark all activity as read").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("reddit:me" to "t1_unseen", "reddit:me" to "t1_seen"), reads)
    }

    // LOWUI7_READ_STATE_ON_THE_FOCUSED_ROW_V1 (OCB-224): the row TalkBack
    // focuses (the clickable one) says Read or Unread.
    @Test
    fun theFocusedActivityRowSaysWhetherItIsRead() {
        inboxWith(
            listOf(
                item("t1_reply1", ActivityKind.REPLY),
                item("t1_reply2", ActivityKind.REPLY, unread = false),
            ),
        )

        fun stateOf(id: String) = composeRule
            .onNode(hasClickAction() and hasText(id, substring = true))
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]

        assertEquals("Unread", stateOf("t1_reply1"))
        assertEquals("Read", stateOf("t1_reply2"))
    }
}
