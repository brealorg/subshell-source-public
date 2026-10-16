package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
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
 * STEP3 (mod01a22, modmail01a1, modmail06): the Inbox gets a Mod tab only for
 * an account that moderates; choosing it reads the queue across all moderated
 * communities once, an empty queue says it is clear, a queue post opens
 * through the moderation resolver (not the inbox one), the tab's badge counts
 * queue items plus unread Modmail, and the Modmail section asks for its grant
 * and reads Modmail when chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3InboxModTabComposeTest {
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
    private val events = mutableListOf<String>()

    private fun item(id: String) = RedditModeratorQueueItem(
        fullname = "t3_$id",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "someone",
        summary = "Reported $id",
        permalink = "/r/subshell/comments/$id/a_title/",
    )

    private fun moderating(
        items: List<RedditModeratorQueueItem>? = null,
    ) = RedditModerationUiState(
        accountStableId = account.stableId,
        moderatedCommunities = setOf("subshell", "android"),
        directoryLoaded = true,
        queueCommunityName = if (items != null) "mod" else null,
        queueItems = items.orEmpty(),
        queueLoaded = items != null,
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

    private fun show(
        moderation: () -> RedditModerationUiState = { moderating() },
        modmail: () -> RedditModmailUiState = { RedditModmailUiState() },
    ) {
        composeRule.setContent {
            InboxScreen(
                state = state,
                onResolveRedditInboxTarget = { acct, id, _ -> events += "inbox:$acct:$id" },
                redditModerationState = moderation(),
                onLoadRedditModQueue = { acct, community, force -> events += "queue:$acct:$community:$force" },
                onResolveRedditModerationTarget = { acct, id, _ -> events += "modtarget:$acct:$id" },
                redditModmailState = modmail(),
                onLoadRedditModmail = { acct, force -> events += "modmail:$acct:$force" },
                onAuthorizeRedditModmail = { acct -> events += "authorize:$acct" },
            )
        }
        composeRule.waitForIdle()
    }

    private fun tab(label: String) =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun select(label: String) {
        composeRule.onAllNodes(tab(label)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aModeratorGetsAModTabThatReadsTheWholeQueueOnce() {
        show()

        for (label in listOf("Activity", "Messages", "Mod")) {
            assertEquals(label, 1, composeRule.onAllNodes(tab(label)).fetchSemanticsNodes().size)
        }
        assertEquals(emptyList<String>(), events.filter { it.startsWith("queue:") })

        select("Mod")

        assertEquals(listOf("queue:reddit:test:mod:false"), events.filter { it.startsWith("queue:") })
        assertEquals(1, count("Across 2 moderated communities"))
    }

    @Test
    fun anAccountThatModeratesNothingHasNoModTab() {
        show(moderation = { moderating().copy(moderatedCommunities = emptySet()) })

        assertEquals(0, composeRule.onAllNodes(tab("Mod")).fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodes(tab("Activity")).fetchSemanticsNodes().size)
    }

    @Test
    fun anEmptyQueueSaysItIsClear() {
        show(moderation = { moderating(items = emptyList()) })
        select("Mod")

        assertEquals(1, count("Moderation queue is clear"))
        assertEquals(1, count("No reported posts or comments need review."))
    }

    @Test
    fun aQueuePostOpensThroughTheModerationResolver() {
        show(moderation = { moderating(items = listOf(item("post123"))) })
        select("Mod")

        composeRule.onAllNodes(hasText("Reported post123") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("modtarget:reddit:test:post123"), events.filter { it.startsWith("modtarget:") })
        assertEquals(emptyList<String>(), events.filter { it.startsWith("inbox:") })
    }

    @Test
    fun theModBadgeCountsQueueItemsAndUnreadModmail() {
        var modmail by mutableStateOf(
            RedditModmailUiState(
                accountStableId = account.stableId,
                unreadCounts = RedditModmailUnreadCounts(new = 1, inProgress = 2),
            ),
        )
        show(
            moderation = { moderating(items = listOf(item("aaa1"), item("bbb2"))) },
            modmail = { modmail },
        )

        assertEquals(1, composeRule.onAllNodes(tab("Mod") and hasText("5")).fetchSemanticsNodes().size)

        // Another account's Modmail counts are not this account's.
        composeRule.runOnIdle { modmail = modmail.copy(accountStableId = "reddit:other") }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodes(tab("Mod") and hasText("2")).fetchSemanticsNodes().size)
    }

    @Test
    fun theModmailSectionAsksForItsGrantAndReadsModmail() {
        show(
            modmail = {
                RedditModmailUiState(accountStableId = account.stableId, authorizationRequired = true)
            },
        )
        select("Mod")
        select("Modmail")

        assertTrue(events.contains("modmail:reddit:test:false"))

        composeRule.onAllNodes(hasText("Enable Modmail") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("authorize:reddit:test"), events.filter { it.startsWith("authorize:") })
    }
}
