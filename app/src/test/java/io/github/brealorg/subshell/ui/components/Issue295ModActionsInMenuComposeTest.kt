package io.github.brealorg.subshell.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditRemovalReasonUiState
import io.github.brealorg.subshell.data.account.RedditRemovalStepOutcome
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.error.rememberViewportErrorHostState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #295: a moderator approves, removes or marks as spam from a post's or
 * comment's ⋮ menu; Remove opens the queue's removal form; Reddit's answer is
 * one tray notice; authorization is asked for when Reddit needs it.
 * ISSUE295_MOD_ACTIONS_IN_THE_MENU_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue295ModActionsInMenuComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val owner = "reddit:owner"
    private val post = ModeratorReportTarget("t3_abc", "subshell")

    private fun click(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun theRequestCarriesTheTargetAndAction() {
        val request = moderatorQuickActionRequest(owner, ModeratorReportTarget("t1_def", "subshell"), RedditModeratorAction.SPAM)
        assertEquals("subshell", request?.communityName)
        assertEquals("t1_def", request?.thingFullname)
        assertEquals(RedditModeratorAction.SPAM, request?.action)
        assertNull(moderatorQuickActionRequest(owner, ModeratorReportTarget("t3_abc"), RedditModeratorAction.APPROVE))
        assertEquals("Post removed", moderatorQuickActionSuccessTitle(request!!.copy(thingFullname = "t3_x", action = RedditModeratorAction.REMOVE)))
        assertEquals("Comment marked as spam", moderatorQuickActionSuccessTitle(request))
        assertEquals("Comment approved", moderatorQuickActionSuccessTitle(request.copy(action = RedditModeratorAction.APPROVE)))
    }

    @Test
    fun onlyAModeratorOfTheCommunitySeesOneModerateEntry() {
        val sheets = mutableListOf<ModeratorReportTarget>()
        val entries = ModeratorQuickActionsEntryPoints(
            canModerate = { community -> community == "subshell" },
            moderate = { _, _ -> },
            requestRemoval = {},
            openSheet = { target, _ -> sheets += target },
        )
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalModeratorQuickActions provides entries) {
                    DropdownMenu(expanded = true, onDismissRequest = {}) {
                        ModeratorMenuItem(post, onDismissMenu = {})
                        ModeratorMenuItem(ModeratorReportTarget("t3_other", "elsewhere"), onDismissMenu = {})
                        ModeratorMenuItem(null, onDismissMenu = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, count("Moderate…"))
        assertEquals(0, count("Approve"))
        click("Moderate…")
        assertEquals(listOf(post), sheets)
    }

    @Test
    fun theSheetSendsEachActionAndReopensReportsAndControlsForItsItem() {
        val comment = ModeratorReportTarget("t1_def", "subshell")
        val quick = ModeratorQuickActionsState().apply { sheetTarget = comment }
        val sent = mutableListOf<RedditModeratorActionRequest>()
        val reports = mutableListOf<ModeratorReportTarget?>()
        val controls = mutableListOf<ModeratorReportTarget>()
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalModeratorReports provides ModeratorReportsEntryPoints(true, { true }, { reports += it }),
                    LocalModeratorContentControls provides ModeratorContentControlsEntryPoints({ true }, { controls += it }),
                ) {
                    ModeratorQuickActionsHost(
                        quickActions = quick,
                        accountStableId = owner,
                        actionState = RedditModeratorActionUiState(),
                        removalReasonState = RedditRemovalReasonUiState(),
                        onLoadRemovalReasons = { _, _, _ -> },
                        onAuthorizeRemovalReasons = {},
                        onModerate = { request -> sent += request },
                        onAuthorize = {},
                        savedResponses = SavedResponseActions(responses = emptyList(), onSave = { _, _ -> null }, onDelete = {}),
                        notices = null,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, count("Moderate comment · r/subshell"))

        click("Approve")
        assertNull(quick.sheetTarget)
        assertEquals(RedditModeratorAction.APPROVE, sent.last().action)
        assertEquals("t1_def", sent.last().thingFullname)
        assertEquals(sent.last(), quick.awaiting)

        composeRule.runOnIdle { quick.sheetTarget = comment }
        composeRule.waitForIdle()
        // ISSUE295_SPAM_ASKS_FROM_THE_SHEET_TOO_V1: Spam asks first, as in the queue.
        click("Spam")
        assertEquals(RedditModeratorAction.APPROVE, sent.last().action)
        assertEquals(1, count("Mark this comment as spam?"))
        click("Cancel")
        assertEquals(1, sent.size)
        composeRule.runOnIdle { quick.sheetTarget = comment }
        composeRule.waitForIdle()
        click("Spam")
        click("Mark as spam")
        assertEquals(RedditModeratorAction.SPAM, sent.last().action)
        assertEquals(sent.last(), quick.awaiting)

        composeRule.runOnIdle { quick.sheetTarget = comment }
        composeRule.waitForIdle()
        click("Reports…")
        composeRule.runOnIdle { quick.sheetTarget = comment }
        composeRule.waitForIdle()
        click("Lock or distinguish…")
        assertEquals(listOf<ModeratorReportTarget?>(comment), reports)
        assertEquals(listOf(comment), controls)

        composeRule.runOnIdle { quick.sheetTarget = comment }
        composeRule.waitForIdle()
        click("Remove…")
        assertEquals(comment, quick.pendingRemoval)
        assertEquals(1, count("Remove this comment?"))
        assertEquals(2, sent.size)
    }

    // ISSUE295_MODERATOR_SETS_POST_FLAIR_V1
    @Test
    fun aPostsSheetOffersChangeFlairAndACommentsDoesNot() {
        var changed = 0
        val quick = ModeratorQuickActionsState().apply {
            sheetTarget = post
            sheetChangeFlair = { changed++ }
        }
        composeRule.setContent {
            SubshellTheme {
                ModeratorQuickActionsHost(
                    quickActions = quick,
                    accountStableId = owner,
                    actionState = RedditModeratorActionUiState(),
                    removalReasonState = RedditRemovalReasonUiState(),
                    onLoadRemovalReasons = { _, _, _ -> },
                    onAuthorizeRemovalReasons = {},
                    onModerate = {},
                    onAuthorize = {},
                    savedResponses = SavedResponseActions(responses = emptyList(), onSave = { _, _ -> null }, onDelete = {}),
                    notices = null,
                )
            }
        }
        composeRule.waitForIdle()
        click("Change flair…")
        assertEquals(1, changed)
        assertNull(quick.sheetTarget)
        assertNull(quick.sheetChangeFlair)

        composeRule.runOnIdle {
            quick.sheetChangeFlair = { changed++ }
            quick.sheetTarget = ModeratorReportTarget("t1_def", "subshell")
        }
        composeRule.waitForIdle()
        assertEquals(0, count("Change flair…"))
    }

    @Test
    fun removeOpensTheRemovalFormAndRedditsAnswerIsOneNotice() {
        val quick = ModeratorQuickActionsState().apply { pendingRemoval = post }
        val sent = mutableListOf<RedditModeratorActionRequest>()
        val action = mutableStateOf(RedditModeratorActionUiState())
        lateinit var notices: ViewportErrorHostState
        composeRule.setContent {
            notices = rememberViewportErrorHostState()
            SubshellTheme {
                ModeratorQuickActionsHost(
                    quickActions = quick,
                    accountStableId = owner,
                    actionState = action.value,
                    removalReasonState = RedditRemovalReasonUiState(),
                    onLoadRemovalReasons = { _, _, _ -> },
                    onAuthorizeRemovalReasons = {},
                    onModerate = { request -> sent += request },
                    onAuthorize = {},
                    savedResponses = SavedResponseActions(responses = emptyList(), onSave = { _, _ -> null }, onDelete = {}),
                    notices = notices,
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, count("Remove this post?"))

        click("Remove")
        assertEquals(1, sent.size)
        assertEquals(RedditModeratorAction.REMOVE, sent.single().action)
        assertEquals("t3_abc", sent.single().thingFullname)
        assertEquals(sent.single(), quick.awaiting)
        assertNull(quick.pendingRemoval)

        // Another request finishing (the queue's) says nothing here.
        action.value = RedditModeratorActionUiState(
            phase = RedditModeratorActionPhase.SUCCEEDED,
            request = sent.single().copy(thingFullname = "t3_zzz"),
            completionRevision = 1,
        )
        composeRule.waitForIdle()
        assertNull(notices.currentRequest)

        action.value = RedditModeratorActionUiState(
            phase = RedditModeratorActionPhase.SUCCEEDED,
            request = sent.single(),
            completionRevision = 2,
            removalMessageOutcome = RedditRemovalStepOutcome.NOT_REQUESTED,
        )
        composeRule.waitForIdle()
        assertEquals("Post removed", notices.currentRequest?.presentation?.title)
        assertNull(quick.awaiting)
    }

    @Test
    fun whenRedditNeedsAuthorizationTheReaderIsAskedOnce() {
        val request = moderatorQuickActionRequest(owner, post, RedditModeratorAction.APPROVE)!!
        val quick = ModeratorQuickActionsState().apply { awaiting = request }
        var authorized = 0
        composeRule.setContent {
            SubshellTheme {
                ModeratorQuickActionsHost(
                    quickActions = quick,
                    accountStableId = owner,
                    actionState = RedditModeratorActionUiState(
                        phase = RedditModeratorActionPhase.AUTHORIZATION_REQUIRED,
                        request = request,
                    ),
                    removalReasonState = RedditRemovalReasonUiState(),
                    onLoadRemovalReasons = { _, _, _ -> },
                    onAuthorizeRemovalReasons = {},
                    onModerate = {},
                    onAuthorize = { authorized++ },
                    savedResponses = SavedResponseActions(responses = emptyList(), onSave = { _, _ -> null }, onDelete = {}),
                    notices = null,
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, count("Allow moderation"))
        click("Allow")
        assertEquals(1, authorized)
        click("Cancel")
        assertNull(quick.awaiting)
        assertEquals(0, count("Allow moderation"))
    }
}
