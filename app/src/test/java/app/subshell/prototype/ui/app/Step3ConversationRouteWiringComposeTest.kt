package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.screens.InboxModSection
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02b, 02c, 02c2): the open conversation sends a typed reply
 * request for the active account (thread or direct chat), offers no composer
 * to a local account, sends a lost authorization to the inbox sign-in, asks
 * for the chat partner's identity, and leaving a direct chat after a sent
 * message reloads the inbox from Reddit.
 *
 * STEP3 (inbox01b12): the open conversation follows the live inbox (Reddit's
 * newer copy of the item replaces the snapshot it was opened with), pulling
 * the list reloads the inbox for the active account only when that inbox is
 * live, and an unread live conversation is marked read once — not when the
 * reader already saw it or a mark is already on its way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ConversationRouteWiringComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val requests = mutableListOf<MessageReplyRequest>()
    private val events = mutableListOf<String>()
    private val marks = mutableListOf<Pair<String, String>>()
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

    private fun item(id: String, title: String, sender: String) =
        InboxItem(id = id, kind = ActivityKind.MESSAGE, title = title, sender = sender, age = "now", preview = "hello")

    private fun show(
        item: InboxItem,
        signedIn: Boolean = true,
        reply: MessageReplyUiState = MessageReplyUiState(),
        inbox: () -> RedditInboxUiState = { RedditInboxUiState() },
    ): SubshellState {
        val state = if (signedIn) {
            SubshellState(
                initialAccountSessions = listOf(account),
                initialActiveAccountId = account.stableId,
                identityMetadataRepository = identities,
            )
        } else {
            SubshellState(identityMetadataRepository = identities)
        }
        state.navigate(MainDestination.INBOX)
        state.openInboxItem(item)
        val route = state.currentNavigationRoute as SubshellRoute.Conversation
        composeRule.setContent {
            if (state.currentNavigationRoute is SubshellRoute.Conversation) {
                ConversationRouteHost(
                    redditModeration = ModerationQueueEntryPoints(),
                    redditModmail = ModmailEntryPoints(),
                    commentMutations = CommentMutationEntryPoints(),
                    redditInbox = InboxEntryPoints(
                        redditInboxState = inbox(),
                        onMarkRedditInboxRead = { accountId, itemId -> marks += accountId to itemId },
                        onLoadRedditInbox = { accountId, force -> events += "load:$accountId:$force" },
                        onAuthorizeRedditInbox = { accountId -> events += "authorize:$accountId" },
                    ),
                    redditMessaging = MessagingEntryPoints(
                        messageReplyState = reply,
                        onSendRedditMessageReply = { requests += it },
                        onResetRedditMessageReplyState = { events += "reset" },
                    ),
                    state = state,
                    route = route,
                    padding = PaddingValues(),
                    expandedWindowLayout = false,
                    inboxPrimaryTabState = mutableStateOf(InboxPrimaryTab.MESSAGES),
                    inboxModSectionState = mutableStateOf(InboxModSection.QUEUE),
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun type(value: String) {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(value)
        composeRule.waitForIdle()
    }

    @Test
    fun aThreadReplyIsATypedRequestForTheActiveAccount() {
        show(item("t4_pm", "Plans", "bob"))
        type("hello")
        click(hasContentDescription("Send reply") and hasClickAction())

        val sent = requests.single()
        assertEquals("reddit:test", sent.accountStableId)
        assertEquals("t4_pm", sent.inboxItemId)
        assertEquals("t4_pm", sent.parentFullname)
        assertEquals(MessageReplyChannel.PRIVATE_MESSAGE_THREAD, sent.channel)
        assertEquals("hello", sent.text)
    }

    @Test
    fun aDirectChatMessageIsADirectChatRequest() {
        show(item("t4_chat", "[direct chat room]", "peer"))
        type("hi")
        click(hasContentDescription("Send message") and hasClickAction())

        val sent = requests.single()
        assertEquals(MessageReplyChannel.DIRECT_CHAT, sent.channel)
        assertEquals("peer", sent.directChatParticipantUsername)
    }

    @Test
    fun aLocalAccountGetsNoComposer() {
        show(item("t4_pm", "Plans", "bob"), signedIn = false)

        assertEquals(0, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun aLostAuthorizationSendsTheReaderToTheInboxSignIn() {
        show(
            item("t4_pm", "Plans", "bob"),
            reply = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                accountStableId = "reddit:test",
                inboxItemId = "t4_pm",
                parentFullname = "t4_pm",
                failureKind = MessageReplyFailureKind.AUTHORIZATION_REQUIRED,
            ),
        )

        click(hasText("Reconnect") and hasClickAction())

        assertEquals(listOf("authorize:reddit:test", "reset"), events)
    }

    @Test
    fun theChatPartnersIdentityIsAsked() {
        show(item("t4_chat", "[direct chat room]", "peer"))

        composeRule.waitUntil(5_000) { "peer" in askedUsers }
    }

    @Test
    fun aCommunitysIdentityIsNotAskedAsAPerson() {
        show(item("t4_mod", "Notice", "r/mods"))
        composeRule.waitUntil(5_000) { "me" in askedUsers }

        assertTrue(askedUsers.toString(), askedUsers.none { it.contains("mods", ignoreCase = true) })
    }

    @Test
    fun leavingADirectChatAfterASentMessageReloadsTheInbox() {
        val state = show(
            item("t4_chat", "[direct chat room]", "peer"),
            reply = MessageReplyUiState(
                phase = MessageReplyPhase.SUCCEEDED,
                accountStableId = "reddit:test",
                inboxItemId = "t4_chat",
                parentFullname = "t4_chat",
                sentText = "hi",
                completedAtEpochSeconds = System.currentTimeMillis() / 1_000L,
                completionRevision = 1L,
            ),
        )

        composeRule.runOnIdle { state.closeConversation() }
        composeRule.waitForIdle()

        assertTrue(events.toString(), "load:reddit:test:true" in events)
    }

    private fun pull() {
        composeRule.onAllNodes(hasScrollAction())[0].performTouchInput { swipeDown() }
        composeRule.waitForIdle()
    }

    private fun live(vararg items: InboxItem, accountId: String = "reddit:test") =
        RedditInboxUiState(accountStableId = accountId, items = items.toList(), loaded = true)

    @Test
    fun theConversationFollowsRedditsNewerCopyOfTheItem() {
        val opened = item("t4_thread", "Old subject", "bob")
        show(opened, inbox = { live(opened.copy(title = "Fresh subject")) })

        assertEquals(1, composeRule.onAllNodesWithText("Fresh subject").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Old subject").fetchSemanticsNodes().size)
    }

    @Test
    fun pullingALiveConversationReloadsTheInbox() {
        val opened = item("t4_thread", "Plans", "bob")
        show(opened, inbox = { live(opened) })

        pull()

        assertTrue(events.toString(), "load:reddit:test:true" in events)
    }

    @Test
    fun anotherAccountsInboxIsNotLiveSoPullingAsksNothing() {
        val opened = item("t4_thread", "Plans", "bob")
        show(opened, inbox = { live(opened, accountId = "reddit:other") })

        pull()

        assertEquals(emptyList<String>(), events.filter { it.startsWith("load:") })
        assertEquals(0, composeRule.onAllNodesWithText("Offline preview — no new data fetched").fetchSemanticsNodes().size)
    }

    @Test
    fun anUnreadLiveConversationIsMarkedReadOnce() {
        val opened = item("t4_thread", "Plans", "bob").copy(unread = true)
        var inbox by mutableStateOf(live(opened))
        show(opened, inbox = { inbox })

        assertEquals(listOf("reddit:test" to "t4_thread"), marks.toList())

        // Reddit answers again while the mark is on its way: no second mark.
        composeRule.runOnIdle {
            inbox = inbox.copy(readMutationItemIds = setOf("t4_thread"), contentRevision = 2L)
        }
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:test" to "t4_thread"), marks.toList())
    }

    @Test
    fun aConversationTheReaderAlreadySawIsNotMarkedAgain() {
        val opened = item("t4_thread", "Plans", "bob").copy(unread = true)
        show(opened, inbox = { live(opened).copy(locallySeenIds = setOf("t4_thread")) })

        assertEquals(emptyList<Pair<String, String>>(), marks.toList())
    }
}
