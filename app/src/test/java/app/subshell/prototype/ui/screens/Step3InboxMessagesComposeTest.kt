package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.SubshellState
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02a): the Messages tab groups messages under Today,
 * Yesterday and Earlier, in that order; a message row shows its sender,
 * subject ("Direct chat" for a chat), preview and age, and offers no
 * Dismiss; and while it is open, the avatars of real senders are asked for
 * — never of a community, a deleted account or Reddit itself. A message row
 * tells TalkBack whether it is read and names the conversation it opens
 * (a11y01a1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3InboxMessagesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val askedUsers = CopyOnWriteArrayList<String>()
    private val identities = object : IdentityMetadataRepository {
        override fun cachedCommunityIdentity(name: String): Community? = null
        override suspend fun refreshCommunityIdentity(name: String, fallback: Community) = RepositoryResult.Content(fallback)
        override fun cachedUserIdentity(username: String): UserFixture? = null
        override suspend fun refreshUserIdentity(username: String, fallback: UserFixture): RepositoryResult<UserFixture> {
            askedUsers += username
            return RepositoryResult.Content(fallback)
        }
    }
    private val nowSeconds = System.currentTimeMillis() / 1_000

    private fun message(
        id: String,
        sender: String,
        title: String,
        preview: String,
        agoSeconds: Long,
        age: String = "1h",
        unread: Boolean = false,
    ) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = title,
        sender = sender,
        age = age,
        preview = preview,
        createdEpochSeconds = nowSeconds - agoSeconds,
        unread = unread,
    )

    private fun messagesTab(items: List<InboxItem>) {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            identityMetadataRepository = identities,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(accountStableId = account.stableId, items = items, loaded = true),
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Messages").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun top(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().single().boundsInRoot.top

    @Test
    fun messagesAreGroupedTodayYesterdayEarlier() {
        messagesTab(
            listOf(
                message("t4_now", "alice", "Lunch", "See you", agoSeconds = 60),
                message("t4_yesterday", "bob", "Plans", "Tomorrow?", agoSeconds = 24 * 3_600L),
                message("t4_old", "carol", "Old", "Long ago", agoSeconds = 3 * 24 * 3_600L),
            ),
        )

        assertTrue(top("Today") < top("Yesterday"))
        assertTrue(top("Yesterday") < top("Earlier"))
    }

    @Test
    fun aMessageRowShowsWhoWhatAndWhenWithoutDismiss() {
        messagesTab(listOf(message("t4_chat", "alice", "[direct chat room]", "Hi there", agoSeconds = 60, age = "1m")))

        val row = composeRule.onAllNodesWithText("Hi there", substring = true).fetchSemanticsNodes().first()
        val rowTexts = generateSequence(row) { it.parent }
            .flatMap { node -> node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text } }
            .toList()
        assertTrue(rowTexts.toString(), rowTexts.any { "alice" in it })
        assertTrue(rowTexts.toString(), rowTexts.any { "Direct chat" in it })
        assertTrue(rowTexts.toString(), rowTexts.any { "1m" in it })
        assertEquals(0, composeRule.onAllNodesWithText("Dismiss", substring = true).fetchSemanticsNodes().size)
        val actions = generateSequence(row) { it.parent }
            .flatMap { node -> node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label } }
            .toList()
        assertTrue(actions.toString(), actions.none { "Dismiss" in it })
        assertTrue(generateSequence(row) { it.parent }.none { SemanticsActions.OnLongClick in it.config })
    }

    @Test
    fun onlyRealSendersAvatarsAreAskedFor() {
        messagesTab(
            listOf(
                message("t4_a", "alice", "Hi", "From a person", agoSeconds = 60),
                message("t4_b", "r/mods", "Notice", "From a community", agoSeconds = 120),
                message("t4_c", "[deleted]", "Gone", "From nobody", agoSeconds = 180),
                message("t4_d", "reddit", "Welcome", "From Reddit", agoSeconds = 240),
            ),
        )
        composeRule.waitUntil(5_000) { askedUsers.isNotEmpty() }
        composeRule.waitForIdle()

        assertTrue(askedUsers.toString(), askedUsers.any { it.equals("alice", ignoreCase = true) })
        assertFalse(askedUsers.toString(), askedUsers.any { it.contains("mods", ignoreCase = true) || it == "[deleted]" || it.equals("reddit", ignoreCase = true) })
    }

    @Test
    fun aMessageRowSaysWhetherItIsRead() {
        messagesTab(
            listOf(
                message("t4_new", "zed_sender", "Lunch plans", "See you", agoSeconds = 60, unread = true),
                message("t4_seen", "yan_sender", "Old news", "Seen it", agoSeconds = 120, unread = false),
            ),
        )

        // The row's state sits on the clickable surface; its sender is a text
        // inside it.
        fun rowState(sender: String): String? {
            val name = composeRule.onAllNodesWithText(sender, useUnmergedTree = true)
                .fetchSemanticsNodes().single().boundsInRoot
            return composeRule.onAllNodes(hasClickAction())
                .fetchSemanticsNodes()
                .single { node ->
                    node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) != null &&
                        node.boundsInRoot.contains(name.center)
                }
                .config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        }

        assertEquals("Unread", rowState("zed_sender"))
        assertEquals("Read", rowState("yan_sender"))
    }
}
