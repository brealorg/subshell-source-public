package app.subshell.prototype.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditModeratorReportDetails
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a1): Spam asks first ("Mark this post as spam?") and
 * sends one SPAM request for exactly that item only after "Mark as spam"
 * (Cancel sends nothing); it is not offered while another action runs; the
 * question goes away when the item or account changes and is not brought
 * back after the screen is recreated; the inbox queue card offers Approve,
 * Remove and Spam.
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

    private fun show() {
        composeRule.setContent {
            SubshellTheme {
                ModeratorSpamAction(item = current, accountStableId = account, actionUiState = action, onModerate = { requests += it })
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun question() = composeRule.onAllNodesWithText("Mark this post as spam?").fetchSemanticsNodes().size

    @Test
    fun spamIsSentOnceForThatItemOnlyAfterTheReaderConfirms() {
        show()

        click("Spam")
        assertEquals(1, question())
        click("Cancel")
        assertEquals(emptyList<RedditModeratorActionRequest>(), requests)
        assertEquals(0, question())

        click("Spam")
        click("Mark as spam")
        val sent = requests.single()
        assertEquals(RedditModeratorAction.SPAM, sent.action)
        assertEquals("t3_abc", sent.thingFullname)
        assertEquals("subshell", sent.communityName)
        assertEquals("reddit:owner", sent.accountStableId)
    }

    @Test
    fun spamIsNotOfferedWhileAnotherActionRuns() {
        show()
        click("Spam")
        composeRule.runOnIdle { action = RedditModeratorActionUiState(phase = RedditModeratorActionPhase.SENDING) }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasText("Mark as spam") and hasClickAction()).onFirst().assertIsNotEnabled()
        composeRule.onAllNodes(hasText("Spam") and hasClickAction()).onFirst().assertIsNotEnabled()
        assertEquals(emptyList<RedditModeratorActionRequest>(), requests)
    }

    @Test
    fun theQuestionGoesAwayWhenTheItemOrAccountChanges() {
        show()
        click("Spam")
        composeRule.runOnIdle { current = item(fullname = "t3_other") }
        composeRule.waitForIdle()
        assertEquals(0, question())

        click("Spam")
        composeRule.runOnIdle { account = "reddit:someone_else" }
        composeRule.waitForIdle()
        assertEquals(0, question())
        assertEquals(emptyList<RedditModeratorActionRequest>(), requests)
    }

    @Test
    fun theQuestionIsNotBroughtBackAfterRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SubshellTheme {
                ModeratorSpamAction(item = current, accountStableId = account, actionUiState = action, onModerate = { requests += it })
            }
        }
        click("Spam")
        assertEquals(1, question())

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(0, question())
    }

    @Test
    fun theInboxQueueCardOffersApproveRemoveAndSpam() {
        composeRule.setContent {
            SubshellTheme {
                InboxModeratorQueueItemCard(
                    item = current,
                    accountStableId = account,
                    actionUiState = action,
                    onOpen = {},
                    onModerate = { requests += it },
                    onRequestRemove = {},
                )
            }
        }
        composeRule.waitForIdle()

        for (label in listOf("Approve", "Remove", "Spam")) {
            assertEquals(label, 1, composeRule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().size)
        }
        click("Spam")
        click("Mark as spam")
        assertEquals(RedditModeratorAction.SPAM, requests.single().action)
    }
}
