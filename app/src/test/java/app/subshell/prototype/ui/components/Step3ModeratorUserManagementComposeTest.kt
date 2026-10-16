package app.subshell.prototype.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.ModeratorUserManagementUiState
import app.subshell.prototype.data.account.RedditApprovedUser
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.account.RedditMutedUser
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a5a1-a5a3, a5-final-ux-material): in the mod tools
 * workspace a moderator bans, mutes and approves a user only through the
 * editor's confirm button, and unbans/unmutes/unapproves only after the
 * question; the user search shows at most six people and never the reader;
 * Back (system or arrow) closes an open editor, not the workspace; the header
 * returns to the mod tools and refreshes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorUserManagementComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = "reddit:me"
    private val bans = mutableListOf<RedditModeratorUserBanRequest>()
    private val mutes = mutableListOf<RedditModeratorUserMuteRequest>()
    private val approvals = mutableListOf<RedditModeratorUserApprovalRequest>()
    private val events = mutableListOf<String>()
    private val searches = mutableListOf<String>()
    private var people = RedditPeopleSearchUiState()
    private lateinit var dispatcher: OnBackPressedDispatcher

    private val state = ModeratorUserManagementUiState(
        visible = true,
        accountStableId = account,
        communityName = "subshell",
        bannedUsers = listOf(RedditBannedUser("spammer")),
        loaded = true,
        mutedUsers = listOf(RedditMutedUser("noisy")),
        mutedLoaded = true,
        approvedUsers = listOf(RedditApprovedUser("friendly")),
        approvedLoaded = true,
    )

    private fun show() {
        val search = ModeratorUserManagementEntryPoints(
            peopleSearchState = { people },
            searchPeople = { searches += it },
        )
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SubshellTheme {
                CompositionLocalProvider(LocalModeratorUserManagement provides search) {
                    ModeratorUserManagementDialog(
                        accountStableId = account,
                        activeUsername = "me",
                        state = state,
                        onRefresh = { events += "refresh" },
                        onAppend = {},
                        onMutate = { bans += it },
                        onMutateMuted = { mutes += it },
                        onMutateApproved = { approvals += it },
                        onAuthorize = {},
                        onDismiss = { events += "dismiss" },
                        embeddedInModToolsWorkspace = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        val lists = composeRule.onAllNodes(hasScrollToNodeAction())
        for (index in 0 until lists.fetchSemanticsNodes().size) {
            if (runCatching { lists[index].performScrollToNode(matcher) }.isSuccess) break
        }
        composeRule.waitForIdle()
    }

    private fun click(matcher: SemanticsMatcher) {
        scrollTo(matcher)
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun clickText(text: String) = click(hasText(text) and hasClickAction())

    private fun type(field: String, value: String) {
        val node = hasSetTextAction() and hasText(field, substring = true)
        scrollTo(node)
        composeRule.onAllNodes(node).onFirst().performTextInput(value)
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun aBanIsSentOnlyByTheEditorsConfirm() {
        show()
        clickText("Ban user")
        type("Username", "target_user")
        type("Ban reason", "spam links")
        assertEquals(emptyList<RedditModeratorUserBanRequest>(), bans)

        clickText("Confirm ban")

        val sent = bans.single()
        assertEquals("target_user", sent.username)
        assertEquals(true, sent.banned)
        assertEquals("spam links", sent.banReason)
    }

    @Test
    fun anUnbanAsksFirst() {
        show()
        clickText("Unban")
        assertEquals(1, shown("Unban u/spammer?"))
        clickText("Cancel")
        assertEquals(emptyList<RedditModeratorUserBanRequest>(), bans)

        clickText("Unban")
        composeRule.onAllNodes(hasText("Unban") and hasClickAction())[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(false), bans.map { it.banned })
    }

    @Test
    fun theUserSearchShowsAtMostSixPeopleAndNeverTheReader() {
        people = RedditPeopleSearchUiState(
            accountStableId = account,
            searchQuery = "us",
            loaded = true,
            items = listOf("me", "user_a", "user_b", "user_c", "user_d", "user_e", "user_f", "user_g", "user_h")
                .map { RedditPeopleSearchItem(username = it, title = it, description = "") },
        )
        show()
        clickText("Ban user")
        type("Username", "us")
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()

        assertEquals(listOf("us"), searches)
        assertEquals(0, composeRule.onAllNodesWithText("u/me").fetchSemanticsNodes().size)
        assertTrue(shown("u/user_") in 1..6)
    }

    @Test
    fun aMuteAndAnApprovalAreSentOnlyByTheirConfirm() {
        show()
        clickText("Muted")
        clickText("Mute user")
        type("Username", "target_user")
        clickText("Confirm mute")
        assertEquals(listOf("target_user"), mutes.map { it.username })
        assertEquals(true, mutes.single().muted)

        clickText("Approved")
        clickText("Approve user")
        type("Username", "new_friend")
        clickText("Confirm approve")
        assertEquals(listOf("new_friend"), approvals.map { it.username })
        assertEquals(true, approvals.single().approved)
    }

    @Test
    fun unmuteAndUnapproveAskFirst() {
        show()
        clickText("Muted")
        clickText("Unmute")
        assertEquals(1, shown("Unmute u/noisy?"))
        composeRule.onAllNodes(hasText("Unmute") and hasClickAction())[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(false), mutes.map { it.muted })

        clickText("Approved")
        clickText("Unapprove")
        assertEquals(1, shown("Unapprove u/friendly?"))
        composeRule.onAllNodes(hasText("Unapprove") and hasClickAction())[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(false), approvals.map { it.approved })
    }

    @Test
    fun backClosesAnOpenEditorNotTheWorkspace() {
        show()
        clickText("Ban user")
        assertEquals(1, composeRule.onAllNodes(hasText("Confirm ban") and hasClickAction()).fetchSemanticsNodes().size)

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodes(hasText("Confirm ban") and hasClickAction()).fetchSemanticsNodes().size)

        clickText("Muted")
        clickText("Mute user")
        click(hasContentDescription("Back to User management") and hasClickAction())
        assertEquals(0, composeRule.onAllNodes(hasText("Confirm mute") and hasClickAction()).fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), events.filter { it == "dismiss" })
    }

    @Test
    fun theHeaderReturnsToModToolsAndRefreshes() {
        show()
        click(hasContentDescription("Refresh user management") and hasClickAction())
        click(hasContentDescription("Back to Mod tools") and hasClickAction())

        assertEquals(listOf("refresh", "dismiss"), events)
    }
}
