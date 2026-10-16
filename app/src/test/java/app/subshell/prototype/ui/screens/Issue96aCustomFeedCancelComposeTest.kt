package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.model.Community
import app.subshell.prototype.data.account.CustomFeedCreateFailureKind
import app.subshell.prototype.data.account.CustomFeedCreatePhase
import app.subshell.prototype.data.account.CustomFeedManagePhase
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.hasContentDescription

/**
 * ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1 (96a): the custom feed editor can
 * be closed while it waits for the browser, which cancels the authorization;
 * only the write itself keeps it open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue96aCustomFeedCancelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var communities: List<Community> = emptyList()

    private fun showEditor(phase: CustomFeedCreatePhase, failureKind: CustomFeedCreateFailureKind? = null): () -> Int {
        var resets = 0
        composeRule.setContent {
            CustomFeedsContent(
                modifier = Modifier,
                state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT),
                authenticated = true,
                accountStableId = "reddit:me",
                communities = communities,
                customFeedsState = CustomFeedsUiState(accountStableId = "reddit:me", createPhase = phase, createFailureKind = failureKind),
                customFeedPostsState = CustomFeedPostsUiState(),
                contentPadding = PaddingValues(),
                onRefresh = {},
                onLoadCustomFeedPosts = { _, _, _ -> },
                onAppendCustomFeedPosts = { _, _ -> },
                onCreateCustomFeed = {},
                onUpdateCustomFeed = {},
                onDeleteCustomFeed = {},
                onResetCustomFeedCreate = { resets += 1 },
                onResetCustomFeedManage = {},
                createRequested = true,
                onCreateRequestConsumed = {},
                onCustomFeedFabVisibilityChanged = {},
            )
        }
        composeRule.waitForIdle()
        return { resets }
    }

    private fun editorShown() =
        composeRule.onAllNodesWithText("Create custom feed").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun waitingForTheBrowserTheEditorCanBeCancelled() {
        val resets = showEditor(CustomFeedCreatePhase.AWAITING_CALLBACK)
        assertTrue(editorShown())
        val before = resets()

        composeRule.onNode(hasContentDescription("Cancel") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        assertFalse(editorShown())
        assertTrue(resets() > before)
    }

    @Test
    fun whileCreatingTheEditorStaysOpen() {
        showEditor(CustomFeedCreatePhase.CREATING)

        composeRule.onNode(hasContentDescription("Cancel") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun onlyTheWriteAndTheBrowserLaunchLockClosing() {
        assertEquals(
            setOf(CustomFeedCreatePhase.CREATING, CustomFeedCreatePhase.AUTHORIZING),
            CustomFeedCreatePhase.entries.filter(::customFeedCreateCloseLocked).toSet(),
        )
        assertEquals(
            setOf(
                CustomFeedManagePhase.SAVING,
                CustomFeedManagePhase.DELETING,
                CustomFeedManagePhase.AUTHORIZING,
            ),
            CustomFeedManagePhase.entries.filter(::customFeedManageCloseLocked).toSet(),
        )
    }

    // STEP3 (error01a4): after a create whose outcome is unknown the editor
    // does not offer Create again (the feed may exist); after a plain refusal
    // it does.
    private fun createButtonForAValidFeed(): androidx.compose.ui.test.SemanticsNodeInteraction {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Evening reads")
        val row = hasText("kotlin", substring = true) and hasClickAction()
        val lists = composeRule.onAllNodes(hasScrollToNodeAction())
        for (index in 0 until lists.fetchSemanticsNodes().size) {
            if (runCatching { lists[index].performScrollToNode(row) }.isSuccess) break
        }
        composeRule.onNode(row).performClick()
        composeRule.waitForIdle()
        return composeRule.onNode(hasText("Create") and hasClickAction())
    }

    @Test
    fun anUnknownCreateOutcomeDoesNotOfferCreateAgain() {
        communities = listOf(kotlin)
        showEditor(CustomFeedCreatePhase.FAILED, CustomFeedCreateFailureKind.OUTCOME_UNKNOWN)

        createButtonForAValidFeed().assertIsNotEnabled()
    }

    @Test
    fun aRefusedCreateCanBeSentAgain() {
        communities = listOf(kotlin)
        showEditor(CustomFeedCreatePhase.FAILED, CustomFeedCreateFailureKind.HTTP)

        createButtonForAValidFeed().assertIsEnabled()
    }

    private val kotlin = Community(id = "t5_kotlin", name = "kotlin", description = "", members = "—", accent = 0xFF6750A4L, joined = true)
}
