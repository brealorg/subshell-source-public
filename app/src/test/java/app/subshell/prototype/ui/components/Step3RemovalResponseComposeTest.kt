package app.subshell.prototype.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.data.account.RedditRemovalStepOutcome
import app.subshell.prototype.data.moderation.ModeratorSavedResponse
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (modresponse01a1–a3): the removal message in both remove dialogs. An
 * empty message blocks the confirm, the written message travels with the
 * request, a failed message is said after the removal, and saved responses
 * fill the draft and are saved on request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3RemovalResponseComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = "reddit:mod"
    private val item = RedditModeratorQueueItem(
        fullname = "t3_abc123",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "someone",
        summary = "A reported post",
    )
    private val saved = mutableListOf<Pair<String, String>>()
    private fun savedResponses(vararg responses: ModeratorSavedResponse) = SavedResponseActions(
        responses = responses.toList(),
        onSave = { title, body -> saved += title to body; ModeratorSavedResponse("new", title, body, 1L) },
        onDelete = {},
    )

    private fun showSheet(
        actionUiState: RedditModeratorActionUiState = RedditModeratorActionUiState(),
        responses: SavedResponseActions = savedResponses(),
        onModerate: (RedditModeratorActionRequest) -> Unit = {},
    ) {
        composeRule.setContent {
            SubshellTheme {
                ModeratorToolsSheet(
                    communityLabel = "r/subshell",
                    communityName = "subshell",
                    accountStableId = account,
                    uiState = RedditModerationUiState(
                        accountStableId = account,
                        queueCommunityName = "subshell",
                        queueItems = listOf(item),
                        queueLoaded = true,
                    ),
                    onDismiss = {},
                    onRefresh = {},
                    onAppend = {},
                    actionUiState = actionUiState,
                    onModerate = onModerate,
                    savedResponses = responses,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText("Remove") and hasClickAction()).onFirst().performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun showQueueDialog(onModerate: (RedditModeratorActionRequest) -> Unit) {
        composeRule.setContent {
            SubshellTheme {
                InboxModeratorRemovalDialog(
                    item = item,
                    accountStableId = account,
                    actionUiState = RedditModeratorActionUiState(),
                    removalReasonState = RedditRemovalReasonUiState(),
                    onLoadRemovalReasons = { _, _ -> },
                    onAuthorizeRemovalReasons = {},
                    onModerate = onModerate,
                    onDismiss = {},
                    savedResponses = savedResponses(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun turnMessageOnAndWrite(text: String?) {
        composeRule.onNodeWithText("Send a removal message").performClick()
        composeRule.waitForIdle()
        if (text != null) composeRule.onNode(hasSetTextAction()).performTextInput(text)
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun anEmptyMessageBlocksTheRemoveAndAWrittenOneTravelsWithIt() {
        var moderated: RedditModeratorActionRequest? = null
        showSheet(onModerate = { moderated = it })

        turnMessageOnAndWrite(text = null)
        composeRule.onNodeWithText("Remove and send").assertIsNotEnabled()

        composeRule.onNodeWithText("Write").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("Rule 1")
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Remove and send").assertIsEnabled().performClick()

        assertEquals(RedditModeratorAction.REMOVE, moderated?.action)
        assertEquals(RedditRemovalMessageType.PRIVATE, moderated?.removalMessage?.type)
        assertEquals("Rule 1", moderated?.removalMessage?.body)
    }

    @Test
    fun withoutAMessageTheRemoveCarriesNone() {
        var moderated: RedditModeratorActionRequest? = null
        showSheet(onModerate = { moderated = it })

        // The queue card has its own Remove; the dialog's confirm is the last one.
        composeRule.onAllNodes(hasText("Remove") and hasClickAction()).onLast().performClick()

        assertEquals(RedditModeratorAction.REMOVE, moderated?.action)
        assertNull(moderated?.removalMessage)
    }

    @Test
    fun theQueueDialogBlocksAnEmptyMessageAndSendsAWrittenOne() {
        var moderated: RedditModeratorActionRequest? = null
        showQueueDialog(onModerate = { moderated = it })

        turnMessageOnAndWrite(text = null)
        composeRule.onNodeWithText("Remove and send").assertIsNotEnabled()
        composeRule.onNodeWithText("Write").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("Rule 2")
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Remove and send").performClick()

        assertEquals("Rule 2", moderated?.removalMessage?.body)
    }

    @Test
    fun aMessageThatWasNotSentIsSaidAfterTheRemoval() {
        val removed = RedditModeratorActionRequest(
            accountStableId = account,
            communityName = "subshell",
            thingFullname = item.fullname,
            action = RedditModeratorAction.REMOVE,
        )
        val partial = RedditModeratorActionUiState(
            phase = RedditModeratorActionPhase.SUCCEEDED,
            request = removed,
            removalMessageOutcome = RedditRemovalStepOutcome.FAILED,
        )
        composeRule.setContent {
            SubshellTheme {
                InboxModerationActionStatus(uiState = partial, onRetry = {}, onAuthorize = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reddit removed the item, but the removal message was not sent.").assertExists()
    }

    @Test
    fun aSavedResponseFillsTheDraft() {
        var moderated: RedditModeratorActionRequest? = null
        showSheet(
            responses = savedResponses(ModeratorSavedResponse("r1", "Spam", "Please read rule 3.", 1L)),
            onModerate = { moderated = it },
        )
        composeRule.onNodeWithText("Send a removal message").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Use saved response…").performClick()
        composeRule.onAllNodes(hasText("Spam") and hasClickAction()).onLast().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Remove and send").performClick()

        assertEquals("Please read rule 3.", moderated?.removalMessage?.body)
    }

    @Test
    fun saveNeedsATitleAndTextAndSavesTheDraftUnderItsTitle() {
        assertEquals(false, saveAsResponseEnabled("", "Please read rule 3."))
        assertEquals(false, saveAsResponseEnabled("Rule three", " "))
        assertEquals(true, saveAsResponseEnabled("Rule three", "Please read rule 3."))

        assertEquals(true, saveAsResponse("Rule three", "Please read rule 3.", savedResponses()))
        assertEquals(listOf("Rule three" to "Please read rule 3."), saved)
    }

    @Test
    fun aFullListKeepsTheSaveDialogOpen() {
        val full = SavedResponseActions(emptyList(), onSave = { _, _ -> null }, onDelete = {})

        assertEquals(false, saveAsResponse("New one", "Text", full))
    }

    @Test
    fun theRemovalMessageTypesKeepRedditsWireValues() {
        assertEquals(
            listOf("public", "public_as_subreddit", "private", "private_exposed"),
            RedditRemovalMessageType.entries.map { it.apiValue },
        )
    }
}
