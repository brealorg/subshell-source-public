package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModmailUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.inbox.ModmailNotificationTarget
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE105_NOTIFICATION_TARGET_OPENS_ONCE_V1: a modmail notification whose
 * conversation failed to load stayed pending and re-opened that conversation
 * on every later selection and Back, so no other conversation could be read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue105ModmailNotificationTargetComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:mod",
        username = "mod_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val failingTarget = "gone105"
    private var target by mutableStateOf<ModmailNotificationTarget?>(
        ModmailNotificationTarget(account.stableId, failingTarget, eventKey = "event-105"),
    )
    private var modmail by mutableStateOf(
        RedditModmailUiState(accountStableId = account.stableId, loaded = true),
    )
    private val opened = mutableListOf<String>()

    private fun inboxFromTheNotification() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditModerationState = RedditModerationUiState(
                    accountStableId = account.stableId,
                    moderatedCommunities = setOf("subshell"),
                ),
                redditModmailState = modmail,
                onOpenRedditModmailConversation = { _, id ->
                    opened += id
                    // The coordinator selects first, then loads.
                    modmail = modmail.copy(selectedConversationId = id, threadLoading = true, thread = null)
                },
                modmailNotificationTarget = target,
                onModmailNotificationConsumed = { target = null },
            )
        }
        composeRule.waitForIdle()
    }

    private val tabs = mutableListOf<InboxPrimaryTab>()
    private val sections = mutableListOf<InboxModSection>()

    // STEP3 (next01a3c): the notification also tells the parent (the host
    // that keeps the chosen tab and Mod section) that it switched to Modmail.
    private fun inboxFromTheNotificationWithParentRecorders() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditModerationState = RedditModerationUiState(
                    accountStableId = account.stableId,
                    moderatedCommunities = setOf("subshell"),
                ),
                redditModmailState = modmail,
                onOpenRedditModmailConversation = { _, id ->
                    opened += id
                    modmail = modmail.copy(selectedConversationId = id, threadLoading = true, thread = null)
                },
                modmailNotificationTarget = target,
                onModmailNotificationConsumed = { target = null },
                onPrimaryTabChanged = { tabs += it },
                onModSectionChanged = { sections += it },
            )
        }
        composeRule.waitForIdle()
    }

    private fun update(change: (RedditModmailUiState) -> RedditModmailUiState) {
        composeRule.runOnIdle { modmail = change(modmail) }
        composeRule.waitForIdle()
    }

    @Test
    fun aTargetThatFailsToLoadIsDoneAndNeverReopens() {
        inboxFromTheNotification()
        assertEquals(listOf(failingTarget), opened)

        // Reddit answers 404: the selection stays, no thread.
        update { it.copy(threadLoading = false) }
        assertNull(target)

        // The reader taps another conversation, then goes Back.
        update { it.copy(selectedConversationId = "other105", threadLoading = false) }
        update { it.copy(selectedConversationId = null) }

        assertEquals(listOf(failingTarget), opened)
    }

    @Test
    fun pickingAnotherConversationBeforeTheTargetLoadsEndsTheTarget() {
        inboxFromTheNotification()

        update { it.copy(selectedConversationId = "other105", threadLoading = true) }

        assertNull(target)
        assertEquals(listOf(failingTarget), opened)
    }

    @Test
    fun theNotificationTellsTheParentItSwitchedToModmail() {
        inboxFromTheNotificationWithParentRecorders()

        assertEquals(listOf(failingTarget), opened)
        assertTrue(tabs.toString(), InboxPrimaryTab.MOD in tabs)
        assertTrue(sections.toString(), InboxModSection.MODMAIL in sections)
    }
}
