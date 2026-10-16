package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.ModerationQueueEntryPoints
import app.subshell.prototype.ui.app.ModmailEntryPoints
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02d5a, mod01a22, modmail06): on Inbox the New-message button
 * belongs to a signed-in Reddit account only; a local account has no way to
 * start a chat, and the Mod tab has none either. The Inbox badge adds the
 * account's unread Modmail to its unread messages, never another account's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3NewMessageButtonGateComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    // A signed-in account is always on the device (else the app shows the
    // sign-in onboarding instead of Inbox); [source] picks the active one.
    private fun messageButtonsOnInbox(source: AccountSessionSource): Int {
        val signedIn = AccountSessionSummary(
            stableId = "reddit:test",
            username = "someone",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val local = AccountSessionSummary(
            stableId = "local:test",
            username = "local",
            source = AccountSessionSource.LOCAL_FIXTURE,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Local",
        )
        val active = if (source == AccountSessionSource.AUTHENTICATED) signedIn else local
        val state = SubshellState(
            initialAccountSessions = listOf(signedIn, local),
            initialActiveAccountId = active.stableId,
            initialInteractionSideConfigured = true,
        )
        state.navigate(MainDestination.INBOX)
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        // The first-run backup folder prompt would cover the screen.
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return composeRule.onAllNodesWithText("Message", useUnmergedTree = true).fetchSemanticsNodes().size
    }

    @Test
    fun aSignedInAccountCanStartAChatFromInbox() {
        assertEquals(1, messageButtonsOnInbox(AccountSessionSource.AUTHENTICATED))
    }

    @Test
    fun aLocalAccountHasNoNewMessageButton() {
        assertEquals(0, messageButtonsOnInbox(AccountSessionSource.LOCAL_FIXTURE))
    }

    private val moderator = AccountSessionSummary(
        stableId = "reddit:test",
        username = "someone",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedInState(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        return SubshellState(
            initialAccountSessions = listOf(moderator),
            initialActiveAccountId = moderator.stableId,
            initialInteractionSideConfigured = true,
        )
    }

    @Test
    fun theModTabHasNoNewMessageButton() {
        val state = signedInState()
        state.navigate(MainDestination.INBOX)
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditModeration = ModerationQueueEntryPoints(
                    redditModerationState = RedditModerationUiState(
                        accountStableId = moderator.stableId,
                        moderatedCommunities = setOf("subshell"),
                        directoryLoaded = true,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Message", useUnmergedTree = true).fetchSemanticsNodes().size)

        composeRule.onAllNodes(hasText("Mod") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithText("Message", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theInboxBadgeAddsOnlyThisAccountsUnreadModmail() {
        val state = signedInState()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val unreadItems = SampleData.inbox.take(4).mapIndexed { index, item ->
            item.copy(id = "t4_unread$index", unread = true)
        }
        var modmail by mutableStateOf(
            RedditModmailUiState(
                accountStableId = moderator.stableId,
                unreadCounts = RedditModmailUnreadCounts(new = 2, inProgress = 1),
            ),
        )
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditInbox = InboxEntryPoints(
                    redditInboxState = RedditInboxUiState(
                        accountStableId = moderator.stableId,
                        items = unreadItems,
                        loaded = true,
                    ),
                ),
                redditModmail = ModmailEntryPoints(redditModmailState = modmail),
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, inboxBadges("7"))

        composeRule.runOnIdle { modmail = modmail.copy(accountStableId = "reddit:other") }
        composeRule.waitForIdle()
        assertEquals(1, inboxBadges("4"))
    }

    private fun inboxBadges(count: String): Int =
        composeRule.onAllNodes(
            hasClickAction() and (hasText("Inbox") or hasContentDescription("Inbox")) and hasText(count),
        ).fetchSemanticsNodes().size
}
