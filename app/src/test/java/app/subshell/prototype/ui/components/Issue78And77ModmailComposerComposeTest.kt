package app.subshell.prototype.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE77_MODMAIL_DRAFT_SURVIVES_RECREATION_V1: the draft, mode and identity
 * were restored and then wiped by an effect that ran on every entry.
 * ISSUE78_MODMAIL_COMPOSER_LOCKED_WHILE_SENDING_V1: the field stayed editable
 * while sending, and the success clear erased what was typed after Send.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue78And77ModmailComposerComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private fun conversation(id: String) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = "Subject $id",
        participantName = "someone",
        preview = "Hi",
        lastUpdatedEpochSeconds = 1L,
        unread = false,
        isInternal = false,
        messageCount = 1,
    )

    private var conversationId by mutableStateOf("abc1")
    private var sendInProgress by mutableStateOf(false)
    private var sendRevision by mutableLongStateOf(1L)

    private val field = hasSetTextAction()

    private fun fieldText(): String =
        composeRule.onNode(field).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun show(): StateRestorationTester {
        val tester = StateRestorationTester(composeRule)
        tester.setContent {
            SubshellTheme {
                InboxModmailComposer(
                    conversation = conversation(conversationId),
                    moderatorUsername = "mod_user",
                    sendInProgress = sendInProgress,
                    sendOutcomeUnknown = false,
                    sendRevision = sendRevision,
                    onSend = { _, _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()
        return tester
    }

    @Test
    fun aDraftAndAnInternalNoteSurviveRecreationAfterAnEarlierSend() {
        val tester = show()
        composeRule.onNodeWithText("Internal note").performClick()
        composeRule.onNode(field).performTextInput("a long internal note")
        composeRule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals("a long internal note", fieldText())
        composeRule.onNodeWithText("Visible to moderators only").assertExists()
    }

    @Test
    fun theFieldIsLockedWhileSendingAndASuccessClearsItOnce() {
        show()
        composeRule.onNode(field).performTextInput("sent text")
        composeRule.waitForIdle()

        sendInProgress = true
        composeRule.waitForIdle()
        composeRule.onNode(field).performTextInput(" typed after send")
        composeRule.waitForIdle()
        assertEquals("sent text", fieldText())

        sendInProgress = false
        sendRevision = 2L
        composeRule.waitForIdle()
        assertEquals("", fieldText())

        composeRule.onNode(field).performTextInput("next draft")
        composeRule.waitForIdle()
        assertEquals("next draft", fieldText())
    }

    @Test
    fun anotherConversationStartsWithAnEmptyReply() {
        show()
        composeRule.onNodeWithText("Internal note").performClick()
        composeRule.onNode(field).performTextInput("for abc1")
        composeRule.waitForIdle()

        conversationId = "def2"
        composeRule.waitForIdle()

        assertEquals("", fieldText())
        composeRule.onNodeWithText("Visible to moderators only").assertDoesNotExist()
        composeRule.onNode(field).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
    }
}
