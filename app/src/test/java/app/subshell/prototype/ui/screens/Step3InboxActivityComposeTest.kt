package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.rememberSubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (inbox02a): activity rows say what happened ("Replied to your
 * comment" / "post"); a long press starts a selection; dismissing part of
 * the activity says how many went (with Undo), dismissing all of it says
 * "Activity cleared"; dismissals are kept per account across restarts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3InboxActivityComposeTest {
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
    private val items = listOf(
        InboxItem(id = "t1_a", kind = ActivityKind.REPLY, title = "", sender = "alice", age = "1h", preview = "First answer", unread = true),
        InboxItem(id = "t1_b", kind = ActivityKind.POST_REPLY, title = "", sender = "bob", age = "2h", preview = "Second answer", unread = true),
    )

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun aLongPressSelectsAndDismissingPartThenAllSaysWhatHappened() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        var selection by mutableStateOf<InboxContextualSelection?>(null)
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(accountStableId = account.stableId, items = items, loaded = true),
                onContextualSelectionChanged = { selection = it },
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, shown("Replied to your comment"))
        assertEquals(1, shown("Replied to your post"))

        composeRule.onAllNodesWithText("First answer", substring = true).onFirst().performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(1, selection?.selectedCount)

        composeRule.runOnIdle { selection!!.onDismiss() }
        composeRule.waitForIdle()
        assertEquals(setOf("t1_a"), state.dismissedInboxActivityIds(account.stableId))
        assertEquals(1, shown("1 dismissed"))
        assertEquals(0, shown("First answer"))

        composeRule.onAllNodesWithText("Second answer", substring = true).onFirst().performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { selection!!.onDismiss() }
        composeRule.waitForIdle()
        assertTrue(shown("Activity cleared") >= 1)
    }

    @Test
    fun dismissalsAndTheClearAreKeptPerAccountAcrossARestart() {
        ensureWorkManagerInitialized(context)
        var generation by mutableStateOf(0)
        val states = mutableMapOf<Int, SubshellState>()
        composeRule.setContent {
            if (generation == 0) {
                states[0] = rememberSubshellState(FakeSubshellRepository())
            } else {
                states[1] = rememberSubshellState(FakeSubshellRepository())
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val first = states.getValue(0)
            first.dismissInboxActivityItems("reddit:one", setOf("t1_x"))
            first.clearInboxActivity("reddit:two")
        }

        composeRule.runOnIdle { generation = 1 }
        composeRule.waitForIdle()

        val second = states.getValue(1)
        assertEquals(setOf("t1_x"), second.dismissedInboxActivityIds("reddit:one"))
        assertEquals(emptySet<String>(), second.dismissedInboxActivityIds("reddit:two"))
        assertTrue(second.inboxActivityClearBoundary("reddit:two") > 0L)
        assertEquals(0L, second.inboxActivityClearBoundary("reddit:one"))
    }
}
