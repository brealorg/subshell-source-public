package io.github.brealorg.subshell.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.github.brealorg.subshell.data.account.ModeratorContentControlConfirmation
import io.github.brealorg.subshell.data.account.ModeratorContentControlsUiState
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorContentState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditModeratorReportDetails
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a3): locking content waits for the reader to confirm
 * the pinned item, which the question names with its community and how to
 * undo it (Cancel sends nothing; a write already running disables the
 * confirm); an unknown distinguish state offers nothing; another author's
 * content says only one's own can be distinguished. The row button and the
 * overflow item open the item they belong to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorContentControlsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val owner = "reddit:owner"
    private val target = ModeratorReportTarget("t3_abc", "subshell")
    private val confirmed = mutableListOf<ModeratorContentControlConfirmation>()
    private val opened = mutableListOf<ModeratorReportTarget>()
    private val action = mutableStateOf(RedditModeratorActionUiState())

    private fun item(author: String = "moderator", distinguished: Boolean? = false) = RedditModeratorQueueItem(
        fullname = "t3_abc",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = author,
        summary = "A post to lock",
        reportDetails = RedditModeratorReportDetails(canModerate = true),
        contentState = RedditModeratorContentState(locked = false, distinguished = distinguished, canModerate = true),
    )

    private fun show(item: RedditModeratorQueueItem = item()) {
        val state = ModeratorContentControlsUiState(
            accountStableId = owner,
            visible = true,
            target = target,
            item = item,
            revision = 2,
        )
        composeRule.setContent {
            SubshellTheme {
                ModeratorContentControlsDialog(
                    accountStableId = owner,
                    activeUsername = "moderator",
                    state = state,
                    actionState = action.value,
                    onRefresh = {},
                    onConfirm = { confirmed += it },
                    onAuthorize = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun lockingWaitsForTheReaderToConfirmThePinnedItem() {
        show()

        click("Lock")
        assertEquals(1, count("Lock this item?"))
        assertEquals(1, count("r/subshell · t3_abc"))
        assertEquals(1, count("You can reverse this with Unlock", substring = true))
        click("Cancel")
        assertEquals(emptyList<ModeratorContentControlConfirmation>(), confirmed)
        assertEquals(0, count("Lock this item?"))

        click("Lock")
        composeRule.onAllNodes(hasText("Lock") and hasClickAction())[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(1, confirmed.size)
        assertEquals(RedditModeratorAction.LOCK, confirmed.single().action)
        assertEquals(target, confirmed.single().target)
    }

    @Test
    fun aWriteAlreadyRunningDisablesTheConfirm() {
        show()

        click("Lock")
        composeRule.runOnIdle { action.value = RedditModeratorActionUiState(phase = RedditModeratorActionPhase.SENDING) }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasText("Lock") and hasClickAction())[1].assertIsNotEnabled()
        assertEquals(emptyList<ModeratorContentControlConfirmation>(), confirmed)
    }

    @Test
    fun anUnknownDistinguishStateOffersNoAction() {
        show(item(distinguished = null))

        assertEquals(1, count("Distinguish state unavailable"))
        composeRule.onNode(hasText("Distinguish as moderator") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun anotherAuthorsContentCannotBeDistinguished() {
        show(item(author = "someone_else"))

        assertEquals(1, count("Only content authored by the active moderator can be distinguished here."))
        composeRule.onNode(hasText("Distinguish as moderator") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    // ISSUE295_ONE_MODERATE_ENTRY_OPENS_A_SHEET_V1: the ⋮ entry moved into the
    // moderator sheet ("Lock or distinguish…"); Issue295ModActionsInMenuComposeTest
    // checks that it opens its own item.
    fun theQueueRowsSheetOpensItsOwnItem() {
        val entries = ModeratorContentControlsEntryPoints(
            canInspect = { it == "subshell" },
            open = { opened += it },
        )
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalModeratorContentControls provides entries) {
                    ModeratorQuickActionsHarness(
                        accountStableId = "reddit:owner",
                        actionState = io.github.brealorg.subshell.data.account.RedditModeratorActionUiState(),
                        onModerate = {},
                        notices = null,
                    ) {
                        ModeratorQueueItemActions(item(), inProgress = false)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(
            androidx.compose.ui.test.hasContentDescription("More moderator actions") and
                androidx.compose.ui.test.hasClickAction(),
        ).performClick()
        composeRule.waitForIdle()
        click("Lock or distinguish…")

        assertEquals(listOf(target), opened.toList())
    }
}
