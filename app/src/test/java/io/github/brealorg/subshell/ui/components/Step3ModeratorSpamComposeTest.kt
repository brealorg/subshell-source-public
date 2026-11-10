package io.github.brealorg.subshell.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditModeratorReportDetails
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a1): Spam asks first ("Mark this post as spam?") and
 * sends one SPAM request for exactly that item only after "Mark as spam"
 * (Cancel sends nothing); it is not sent while another action runs; the
 * question goes away when the account changes and is not brought back after
 * the screen is recreated. ISSUE295_QUEUE_USES_THE_SHEET_V1: the queue card
 * offers Approve and Remove, and Spam is in the Moderate sheet its ⋮ opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorSpamComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val requests = mutableListOf<RedditModeratorActionRequest>()
    private var action by mutableStateOf(RedditModeratorActionUiState())

    private fun item(fullname: String = "t3_abc", kind: RedditModeratorQueueKind = RedditModeratorQueueKind.POST) = RedditModeratorQueueItem(
        fullname = fullname,
        kind = kind,
        communityName = "subshell",
        author = "someone",
        summary = "A spammy post",
        reportDetails = RedditModeratorReportDetails(canModerate = true),
    )

    private var current by mutableStateOf(item())
    private var account by mutableStateOf("reddit:owner")

    @androidx.compose.runtime.Composable
    private fun card() {
        SubshellTheme {
            ModeratorQuickActionsHarness(
                accountStableId = account,
                actionState = action,
                onModerate = { requests += it },
                notices = null,
            ) {
                InboxModeratorQueueItemCard(item = current, actionUiState = action, onOpen = {})
            }
        }
    }

    private fun show() {
        composeRule.setContent { card() }
        composeRule.waitForIdle()
    }

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun askSpam() {
        composeRule.onAllNodes(hasContentDescription("More moderator actions") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        click("Spam")
    }

    private fun question() = composeRule.onAllNodesWithText("Mark this post as spam?").fetchSemanticsNodes().size

    @Test
    fun spamIsSentOnceForThatItemOnlyAfterTheReaderConfirms() {
        show()

        askSpam()
        assertEquals(1, question())
        click("Cancel")
        assertEquals(emptyList<RedditModeratorActionRequest>(), requests)
        assertEquals(0, question())

        askSpam()
        click("Mark as spam")
        val sent = requests.single()
        assertEquals(RedditModeratorAction.SPAM, sent.action)
        assertEquals("t3_abc", sent.thingFullname)
        assertEquals("subshell", sent.communityName)
        assertEquals("reddit:owner", sent.accountStableId)
    }

    @Test
    fun spamIsNotSentWhileAnotherActionRuns() {
        show()
        askSpam()
        composeRule.runOnIdle { action = RedditModeratorActionUiState(phase = RedditModeratorActionPhase.SENDING) }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasText("Mark as spam") and hasClickAction()).onFirst().assertIsNotEnabled()
        assertEquals(emptyList<RedditModeratorActionRequest>(), requests)
    }

    @Test
    fun theQuestionGoesAwayWhenTheAccountChanges() {
        show()
        askSpam()
        assertEquals(1, question())
        composeRule.runOnIdle { account = "reddit:someone_else" }
        composeRule.waitForIdle()
        assertEquals(0, question())
        assertEquals(emptyList<RedditModeratorActionRequest>(), requests)
    }

    @Test
    fun theQuestionIsNotBroughtBackAfterRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { card() }
        askSpam()
        assertEquals(1, question())

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(0, question())
    }

    @Test
    fun theInboxQueueCardOffersApproveRemoveAndTheSheet() {
        show()

        for (label in listOf("Approve", "Remove")) {
            assertEquals(label, 1, composeRule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().size)
        }
        assertEquals(0, composeRule.onAllNodes(hasText("Spam") and hasClickAction()).fetchSemanticsNodes().size)
        askSpam()
        click("Mark as spam")
        assertEquals(RedditModeratorAction.SPAM, requests.single().action)
    }
}
