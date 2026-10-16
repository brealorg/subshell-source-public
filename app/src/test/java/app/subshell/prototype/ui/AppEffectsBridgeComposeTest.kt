package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.inbox.InboxNotificationTarget
import app.subshell.prototype.inbox.ModmailNotificationTarget
import app.subshell.prototype.ui.app.AppEffectsBridge
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.ModmailEntryPoints
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app effects in their own bridge: an inbox notification for the
 * active account loads the inbox and, once loaded without its item, falls
 * back to the inbox root; a notification for another account does nothing;
 * a modmail notification opens the inbox root and loads modmail; the
 * drawer and account-switcher requests open them; an open drawer loads the
 * cached subscriptions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppEffectsBridgeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun anInboxNotificationLoadsTheInboxAndFallsBackToTheRootWhenItsItemIsGone() {
        val loads = mutableListOf<Pair<String, Boolean>>()
        var consumed = 0
        val inbox = mutableStateOf(
            InboxEntryPoints(
                onLoadRedditInbox = { account, force -> loads += account to force },
                inboxNotificationTarget = InboxNotificationTarget(
                    accountStableId = alice.stableId,
                    inboxItemId = "t4_gone",
                    eventKey = "event-1",
                ),
                onInboxNotificationConsumed = { consumed += 1 },
            ),
        )
        val state = host(inbox = inbox)
        assertEquals(listOf(alice.stableId to true), loads)
        assertEquals(0, consumed)

        inbox.value = inbox.value.copy(
            // A finished load bumps the content revision, which the effect keys on.
            redditInboxState = RedditInboxUiState(
                accountStableId = alice.stableId,
                loaded = true,
                contentRevision = 1L,
            ),
        )
        composeRule.waitForIdle()

        assertEquals(1, consumed)
        assertEquals(MainDestination.INBOX, state.destination)
    }

    // ISSUE72_NOTIFICATION_WAITS_FOR_THE_RELOAD_V1: the inbox was already
    // loaded (without the new message) when the notification was tapped.
    @Test
    fun aNotificationOverAnAlreadyLoadedInboxWaitsForTheReload() {
        var consumed = 0
        val markedRead = mutableListOf<String>()
        val loadedBefore = RedditInboxUiState(
            accountStableId = alice.stableId,
            items = listOf(message("t4_old")),
            loaded = true,
            contentRevision = 5L,
        )
        val inbox = mutableStateOf(
            InboxEntryPoints(
                redditInboxState = loadedBefore,
                onLoadRedditInbox = { _, _ -> },
                onMarkRedditInboxRead = { _, id -> markedRead += id },
                inboxNotificationTarget = InboxNotificationTarget(
                    accountStableId = alice.stableId,
                    inboxItemId = "t4_new",
                    eventKey = "event-72",
                ),
                onInboxNotificationConsumed = { consumed += 1 },
            ),
        )
        val state = host(inbox = inbox)
        assertEquals(0, consumed)
        assertEquals(MainDestination.HOME, state.destination)

        inbox.value = inbox.value.copy(redditInboxState = loadedBefore.copy(loading = true))
        composeRule.waitForIdle()
        inbox.value = inbox.value.copy(
            redditInboxState = loadedBefore.copy(
                items = listOf(message("t4_new"), message("t4_old")),
                contentRevision = 6L,
            ),
        )
        composeRule.waitForIdle()

        assertEquals(1, consumed)
        assertEquals(listOf("t4_new"), markedRead)
    }

    // STEP3 (inbox01b22): the notified message, once the reloaded inbox has it,
    // opens as its own conversation (not just the Inbox list).
    @Test
    fun aNotifiedMessageOpensItsConversationOnceTheReloadHasIt() {
        val loadedBefore = RedditInboxUiState(
            accountStableId = alice.stableId,
            items = listOf(message("t4_old")),
            loaded = true,
            contentRevision = 5L,
        )
        val inbox = mutableStateOf(
            InboxEntryPoints(
                redditInboxState = loadedBefore,
                onLoadRedditInbox = { _, _ -> },
                inboxNotificationTarget = InboxNotificationTarget(
                    accountStableId = alice.stableId,
                    inboxItemId = "t4_new",
                    eventKey = "event-72",
                ),
            ),
        )
        val state = host(inbox = inbox)

        inbox.value = inbox.value.copy(redditInboxState = loadedBefore.copy(loading = true))
        composeRule.waitForIdle()
        inbox.value = inbox.value.copy(
            redditInboxState = loadedBefore.copy(
                items = listOf(message("t4_new"), message("t4_old")),
                contentRevision = 6L,
            ),
        )
        composeRule.waitForIdle()

        val route = state.currentNavigationRoute
        assertTrue(route.toString(), route is SubshellRoute.Conversation)
        assertEquals("t4_new", (route as SubshellRoute.Conversation).inboxItemId)
    }

    private fun message(id: String) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "Message $id",
        sender = "someone",
        age = "1m",
        preview = "Hello",
        unread = true,
    )

    @Test
    fun aNotificationForAnotherAccountDoesNothing() {
        val loads = mutableListOf<Pair<String, Boolean>>()
        val modmailLoads = mutableListOf<Pair<String, Boolean>>()
        val state = host(
            inbox = mutableStateOf(
                InboxEntryPoints(
                    onLoadRedditInbox = { account, force -> loads += account to force },
                    inboxNotificationTarget = InboxNotificationTarget("reddit:bob", "t4_x", "event-2"),
                ),
            ),
            modmail = ModmailEntryPoints(
                onLoadRedditModmail = { account, force -> modmailLoads += account to force },
                modmailNotificationTarget = ModmailNotificationTarget("reddit:bob", "c1", "event-3"),
            ),
        )

        assertEquals(emptyList<Pair<String, Boolean>>(), loads)
        assertEquals(emptyList<Pair<String, Boolean>>(), modmailLoads)
        assertEquals(MainDestination.HOME, state.destination)
    }

    @Test
    fun aModmailNotificationOpensTheInboxRootAndLoadsModmail() {
        val modmailLoads = mutableListOf<Pair<String, Boolean>>()
        val state = host(
            modmail = ModmailEntryPoints(
                onLoadRedditModmail = { account, force -> modmailLoads += account to force },
                modmailNotificationTarget = ModmailNotificationTarget(alice.stableId, "c1", "event-4"),
            ),
        )

        assertEquals(listOf(alice.stableId to true), modmailLoads)
        assertEquals(MainDestination.INBOX, state.destination)
    }

    @Test
    fun theAccountSwitcherRequestOpensTheSwitcher() {
        val switcherVisible = mutableStateOf(false)
        val state = host(accountSwitcherVisible = switcherVisible)

        state.requestAccountSwitcher()
        composeRule.waitForIdle()

        assertTrue(switcherVisible.value)
    }

    @Test
    fun theDrawerRequestOpensTheDrawerWhichLoadsTheCachedSubscriptions() {
        val subscriptionLoads = mutableListOf<String>()
        val drawerState = DrawerState(DrawerValue.Closed)
        val state = host(
            drawerState = drawerState,
            feed = FeedEntryPoints(onLoadCachedRedditSubscriptions = { subscriptionLoads += it }),
        )
        assertEquals(emptyList<String>(), subscriptionLoads)

        state.requestDrawer()
        composeRule.waitForIdle()

        assertEquals(DrawerValue.Open, drawerState.currentValue)
        assertEquals(listOf(alice.stableId), subscriptionLoads)
    }

    private fun host(
        inbox: MutableState<InboxEntryPoints> = mutableStateOf(InboxEntryPoints()),
        modmail: ModmailEntryPoints = ModmailEntryPoints(),
        feed: FeedEntryPoints = FeedEntryPoints(),
        drawerState: DrawerState = DrawerState(DrawerValue.Closed),
        accountSwitcherVisible: MutableState<Boolean> = mutableStateOf(false),
    ): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val interactionSource = MutableInteractionSource()
        composeRule.setContent {
            AppEffectsBridge(
                state = state,
                redditInbox = inbox.value,
                redditModmail = modmail,
                redditFeed = feed,
                inContext = false,
                drawerState = drawerState,
                youInteractionSource = interactionSource,
                hapticFeedback = NoHapticFeedback,
                accountSwitcherVisibleState = accountSwitcherVisible,
                suppressYouClickState = mutableStateOf(false),
            )
        }
        composeRule.waitForIdle()
        return state
    }

    private object NoHapticFeedback : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }
}
