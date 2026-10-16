package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (inboxm3a1): the Inbox tabs carry their unread counts (capped at
 * "99+"), a tab with nothing unread has no badge, choosing a tab selects it
 * and shows its rows, and a moderator's Mod tab splits into Queue and Modmail
 * with Modmail's own unread count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3InboxWorkspaceBadgesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private lateinit var state: SubshellState

    @Before
    fun setUp() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
    }

    private fun item(id: String, kind: ActivityKind, unread: Boolean) = InboxItem(
        id = id,
        kind = kind,
        title = "Title $id",
        sender = "someone_else",
        age = "1h",
        preview = "Preview $id",
        unread = unread,
    )

    private fun show(
        items: List<InboxItem>,
        moderation: RedditModerationUiState = RedditModerationUiState(),
        modmail: RedditModmailUiState = RedditModmailUiState(),
    ) {
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(
                    accountStableId = account.stableId,
                    items = items,
                    loaded = true,
                ),
                redditModerationState = moderation,
                redditModmailState = modmail,
            )
        }
        composeRule.waitForIdle()
    }

    private fun tab(label: String) =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun tabs(label: String, badge: String) =
        composeRule.onAllNodes(tab(label) and hasText(badge)).fetchSemanticsNodes().size

    private fun select(label: String) {
        composeRule.onAllNodes(tab(label)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun selected(label: String): Boolean =
        composeRule.onAllNodes(tab(label)).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.Selected) == true

    @Test
    fun eachTabCarriesItsUnreadCountCappedAt99() {
        val activity = (1..3).map { item("t1_a$it", ActivityKind.REPLY, unread = true) }
        val messages = (1..120).map { item("t4_m$it", ActivityKind.MESSAGE, unread = true) }
        show(activity + messages)

        assertEquals(1, tabs("Activity", "3"))
        assertEquals(1, tabs("Messages", "99+"))
        assertTrue(selected("Activity"))

        select("Messages")

        assertTrue(selected("Messages"))
        assertTrue(composeRule.onAllNodesWithText("Title t4_m1", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aTabWithNothingUnreadHasNoBadge() {
        show(
            listOf(
                item("t1_a1", ActivityKind.REPLY, unread = false),
                item("t4_m1", ActivityKind.MESSAGE, unread = false),
            ),
        )

        val activityTexts = composeRule.onAllNodes(tab("Activity")).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        val messageTexts = composeRule.onAllNodes(tab("Messages")).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertEquals(listOf("Activity"), activityTexts)
        assertEquals(listOf("Messages"), messageTexts)
    }

    @Test
    fun aModeratorsModTabSplitsIntoQueueAndModmailWithModmailsOwnCount() {
        show(
            items = emptyList(),
            moderation = RedditModerationUiState(
                accountStableId = account.stableId,
                moderatedCommunities = setOf("subshell"),
                directoryLoaded = true,
            ),
            modmail = RedditModmailUiState(
                accountStableId = account.stableId,
                unreadCounts = RedditModmailUnreadCounts(new = 2),
            ),
        )

        select("Mod")

        assertEquals(1, composeRule.onAllNodes(tab("Queue")).fetchSemanticsNodes().size)
        assertEquals(1, tabs("Modmail", "2"))
    }
}
