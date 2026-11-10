package io.github.brealorg.subshell.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.github.brealorg.subshell.data.account.ModeratorReportConfirmation
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.ModeratorReportsUiState
import io.github.brealorg.subshell.data.account.RedditModerationUiState
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditModeratorReportDetails
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a2): the reports dialog changes report alerts only after
 * the reader confirms the pinned item (Cancel sends nothing), offers nothing
 * when Reddit did not say whether reports are ignored, says when report
 * details were cut short, and lists the loaded queue before recently opened
 * items. The row button and the overflow item open the item they belong to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorReportsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val owner = "reddit:owner"
    private val target = ModeratorReportTarget("t3_abc", "subshell")
    private val confirmed = mutableListOf<ModeratorReportConfirmation>()
    private val opened = mutableListOf<ModeratorReportTarget?>()

    private fun item(
        fullname: String = "t3_abc",
        summary: String = "A reported post",
        ignored: Boolean? = false,
        incomplete: Boolean = false,
    ) = RedditModeratorQueueItem(
        fullname = fullname,
        kind = if (fullname.startsWith("t3_")) RedditModeratorQueueKind.POST else RedditModeratorQueueKind.COMMENT,
        communityName = "subshell",
        author = "someone",
        summary = summary,
        reportDetails = RedditModeratorReportDetails(
            userReports = emptyList(),
            moderatorReports = emptyList(),
            totalCount = 0,
            ignored = ignored,
            canModerate = true,
            incomplete = incomplete,
        ),
    )

    private fun loaded(item: RedditModeratorQueueItem = item()) = ModeratorReportsUiState(
        accountStableId = owner,
        visible = true,
        target = target,
        item = item,
        revision = 3,
    )

    private fun show(
        state: ModeratorReportsUiState,
        moderation: RedditModerationUiState = RedditModerationUiState(),
        action: MutableState<RedditModeratorActionUiState> = mutableStateOf(RedditModeratorActionUiState()),
    ) {
        composeRule.setContent {
            SubshellTheme {
                ModeratorReportsDialog(
                    accountStableId = owner,
                    state = state,
                    moderationState = moderation,
                    actionState = action.value,
                    onOpen = { opened += it },
                    onRefresh = {},
                    onConfirm = { confirmed += it },
                    onAuthorize = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        runCatching { composeRule.onNode(hasScrollAction()).performScrollToNode(matcher) }
        composeRule.waitForIdle()
    }

    private fun click(text: String) {
        val node = hasText(text) and hasClickAction()
        scrollTo(node)
        composeRule.onNode(node).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun ignoringReportsWaitsForTheReaderToConfirmThePinnedItem() {
        show(loaded())

        click("Ignore reports…")
        assertEquals(1, count("Ignore reports?"))
        click("Cancel")
        assertEquals(emptyList<ModeratorReportConfirmation>(), confirmed)
        assertEquals(0, count("Ignore reports?"))

        click("Ignore reports…")
        click("Ignore reports")
        assertEquals(1, confirmed.size)
        assertEquals(target, confirmed.single().target)
        assertEquals(RedditModeratorAction.IGNORE_REPORTS, confirmed.single().action)
        assertEquals(3L, confirmed.single().revision)
    }

    @Test
    fun anUnknownReportSettingOffersNoAction() {
        show(loaded(item(ignored = null)))

        scrollTo(hasText("Report handling"))
        assertEquals(1, count("Report status unavailable"))
        assertEquals(0, count("Ignore reports…"))
        assertEquals(0, count("Unignore reports…"))
    }

    @Test
    fun shortenedReportDetailsAreSaid() {
        show(loaded(item(incomplete = true)))

        scrollTo(hasText("Some report details were unavailable or shortened."))
        assertEquals(1, count("Some report details were unavailable or shortened."))
    }

    @Test
    fun theLoadedQueueComesBeforeRecentlyOpenedItems() {
        val recent = ModeratorReportTarget("t3_recent", "subshell")
        show(
            state = ModeratorReportsUiState(accountStableId = owner, visible = true, recentTargets = listOf(recent)),
            moderation = RedditModerationUiState(
                accountStableId = owner,
                moderatedCommunities = setOf("subshell"),
                directoryLoaded = true,
                queueCommunityName = "mod",
                queueLoaded = true,
                queueItems = listOf(item(fullname = "t3_queued", summary = "Queued post")),
            ),
        )

        val queued = composeRule.onAllNodesWithText("Queued post").fetchSemanticsNodes().single()
        val recentRow = composeRule.onAllNodesWithText("t3_recent", substring = true).fetchSemanticsNodes().first()
        assertTrue(queued.boundsInRoot.top < recentRow.boundsInRoot.top)

        click("Queued post")
        assertEquals(listOf<ModeratorReportTarget?>(ModeratorReportTarget("t3_queued", "subshell")), opened)
    }

    @Test
    // ISSUE295_ONE_MODERATE_ENTRY_OPENS_A_SHEET_V1: the ⋮ entry moved into the
    // moderator sheet ("Reports…"); Issue295ModActionsInMenuComposeTest checks it.
    fun theQueueRowsSheetOpensItsOwnItem() {
        val entries = ModeratorReportsEntryPoints(
            hasModeratorAccess = true,
            canInspect = { it == "subshell" },
            open = { opened += it },
        )
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalModeratorReports provides entries) {
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
        click("Reports…")

        assertEquals(listOf(target), opened.toList())
    }
}
