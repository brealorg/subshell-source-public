package app.subshell.prototype.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.shortcuts.LauncherShortcutTarget
import app.subshell.prototype.ui.app.LauncherShortcutNoticeScope
import app.subshell.prototype.ui.app.LauncherShortcutTargetEffect
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1: a feed or Inbox shortcut opens for
 * its own account (a feed once the list is loaded) and only explains itself
 * when another account is active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LauncherShortcutTargetEffectComposeTest {
    private val composeRule = createComposeRule()

    // Display-density guard for the Robolectric `lightZ … Infinity` launch
    // flake, which struck this class under land on 2026-09-25.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:$name",
        username = name,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val alice = account("alice")
    private val bob = account("bob")
    private val cats = CustomFeedSummary(
        path = "/user/alice/m/cats",
        displayName = "Cats",
        description = "",
        visibility = "private",
        subredditNames = emptyList(),
    )

    private class Harness(
        val state: SubshellState,
        val feedTarget: MutableState<LauncherShortcutTarget.CustomFeed?> = mutableStateOf(null),
        val inboxTarget: MutableState<LauncherShortcutTarget.Inbox?> = mutableStateOf(null),
        val feeds: MutableState<CustomFeedsUiState> = mutableStateOf(CustomFeedsUiState()),
        val activeCustomFeedPath: MutableState<String?> = mutableStateOf(null),
        val pendingLanding: MutableState<String?> = mutableStateOf("reddit:alice"),
        val notices: ViewportErrorHostState = ViewportErrorHostState(),
        val feedLoads: MutableList<String> = mutableListOf(),
    )

    private fun host(active: AccountSessionSummary): Harness {
        val harness = Harness(
            state = SubshellState(
                initialAccountSessions = listOf(alice, bob),
                initialActiveAccountId = active.stableId,
            ),
        )
        composeRule.setContent {
            LauncherShortcutTargetEffect(
                state = harness.state,
                feedTarget = harness.feedTarget.value,
                onFeedTargetConsumed = { harness.feedTarget.value = null },
                inboxTarget = harness.inboxTarget.value,
                onInboxTargetConsumed = { harness.inboxTarget.value = null },
                customFeedsState = harness.feeds.value,
                onLoadCustomFeeds = { account, _ -> harness.feedLoads += account },
                activeCustomFeedPathState = harness.activeCustomFeedPath,
                pendingAuthenticatedLandingAccountIdState = harness.pendingLanding,
                noticeHost = harness.notices,
            )
        }
        composeRule.waitForIdle()
        return harness
    }

    @Test
    fun aFeedShortcutLoadsTheListThenOpensTheFeedAtHome() {
        val harness = host(alice)
        harness.state.navigate(MainDestination.INBOX)

        harness.feedTarget.value = LauncherShortcutTarget.CustomFeed(alice.stableId, cats.path, 1L)
        composeRule.waitForIdle()
        assertEquals(listOf(alice.stableId), harness.feedLoads)
        assertNull(harness.activeCustomFeedPath.value)

        harness.feeds.value = CustomFeedsUiState(
            accountStableId = alice.stableId,
            feeds = listOf(cats),
            contentRevision = 1L,
        )
        composeRule.waitForIdle()

        assertEquals(cats.path, harness.activeCustomFeedPath.value)
        assertEquals(MainDestination.HOME, harness.state.destination)
        assertNull(harness.pendingLanding.value)
        assertNull(harness.feedTarget.value)
        assertNull(harness.notices.currentRequest)
    }

    @Test
    fun anInboxShortcutOpensTheInbox() {
        val harness = host(alice)

        harness.inboxTarget.value = LauncherShortcutTarget.Inbox(alice.stableId, 1L)
        composeRule.waitForIdle()

        assertEquals(MainDestination.INBOX, harness.state.destination)
        assertNull(harness.inboxTarget.value)
    }

    @Test
    fun anotherAccountsShortcutOnlySaysWhichAccountItNeeds() {
        val harness = host(bob)

        harness.feedTarget.value = LauncherShortcutTarget.CustomFeed(alice.stableId, cats.path, 1L)
        composeRule.waitForIdle()

        assertEquals(bob.stableId, harness.state.activeAccountId)
        assertNull(harness.activeCustomFeedPath.value)
        assertEquals(emptyList<String>(), harness.feedLoads)
        assertNull(harness.feedTarget.value)
        val notice = harness.notices.currentRequest
        assertEquals(LauncherShortcutNoticeScope, notice?.scopeKey)
        assertEquals("Switch to u/alice", notice?.presentation?.title)
    }

    @Test
    fun aDeletedFeedIsReportedAndNothingOpens() {
        val harness = host(alice)
        harness.feeds.value = CustomFeedsUiState(accountStableId = alice.stableId, contentRevision = 1L)
        composeRule.waitForIdle()

        harness.feedTarget.value = LauncherShortcutTarget.CustomFeed(alice.stableId, cats.path, 1L)
        composeRule.waitForIdle()

        assertNull(harness.activeCustomFeedPath.value)
        assertEquals("Custom feed not found", harness.notices.currentRequest?.presentation?.title)
    }
}
