package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ModeratorLogUiState
import io.github.brealorg.subshell.data.account.RedditModerationUiFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorLogEntry
import io.github.brealorg.subshell.data.account.RedditModmailConversation
import io.github.brealorg.subshell.data.account.UserFlairFailureKind
import io.github.brealorg.subshell.data.account.UserFlairOption
import io.github.brealorg.subshell.data.account.UserFlairSnapshot
import io.github.brealorg.subshell.data.account.UserFlairUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.components.AccountSettingsRuntime
import io.github.brealorg.subshell.ui.components.InboxModmailComposer
import io.github.brealorg.subshell.ui.components.ModeratorLogDialog
import io.github.brealorg.subshell.ui.components.UserFlairSheet
import io.github.brealorg.subshell.ui.screens.SettingsDestination
import io.github.brealorg.subshell.ui.screens.SettingsScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUX1: LOWUX1_MODMAIL_SEND_WAITS_FOR_A_READ_CHANGE_V1 (OCB-135),
 * LOWUX1_MOD_LOG_PAGE_FAILURE_AT_THE_BOTTOM_V1 (OCB-147),
 * LOWUX1_FLAIR_APPLY_FAILURE_KEEPS_THE_CHOICE_V1 (OCB-167),
 * LOWUX1_FAILED_LOGOUT_IS_SAID_V1 (OCB-268).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUx1ComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    @Test
    fun modmailSendWaitsWhileAReadChangeRuns() {
        composeRule.setContent {
            SubshellTheme {
                InboxModmailComposer(
                    conversation = RedditModmailConversation(
                        id = "c135",
                        communityName = "subshell",
                        subject = "Hello",
                        participantName = "someone",
                        preview = "",
                        lastUpdatedEpochSeconds = 1L,
                        unread = true,
                        isInternal = false,
                        messageCount = 1,
                    ),
                    moderatorUsername = "me_user",
                    sendInProgress = false,
                    sendOutcomeUnknown = false,
                    sendRevision = 0L,
                    onSend = { _, _, _ -> },
                    sendBlocked = true,
                )
            }
        }
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("A reply")
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Send Modmail reply").assertIsNotEnabled()
    }

    @Test
    fun aFailedModLogPageIsRetriedAtTheBottomAsAPage() {
        var appends = 0
        var refreshes = 0
        composeRule.setContent {
            SubshellTheme {
                ModeratorLogDialog(
                    accountStableId = account.stableId,
                    state = ModeratorLogUiState(
                        visible = true,
                        accountStableId = account.stableId,
                        communityName = "subshell",
                        entries = listOf(
                            RedditModeratorLogEntry(id = "e1", communityName = "subshell", moderatorName = "me_user", action = "removelink"),
                        ),
                        loaded = true,
                        nextCursor = "next1",
                        failureKind = RedditModerationUiFailureKind.TRANSPORT,
                        failureRetryable = true,
                    ),
                    onRefresh = { refreshes += 1 },
                    onAppend = { appends += 1 },
                    onAuthorize = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, appends)
        assertEquals(1, composeRule.onAllNodesWithText("Couldn't load more of the mod log").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()

        assertEquals(1, appends)
        assertEquals(0, refreshes)
    }

    @Test
    fun aFailedFlairApplyKeepsTheListAndSaysItFailed() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        state.synchronizeUserFlairUiState(
            UserFlairUiState(
                accountStableId = account.stableId,
                communityName = "subshell",
                snapshot = UserFlairSnapshot(
                    communityName = "subshell",
                    options = listOf(UserFlairOption(templateId = "t1", text = "Helper")),
                ),
                loaded = true,
                failureKind = UserFlairFailureKind.HTTP,
            ),
        )
        composeRule.setContent {
            SubshellTheme { UserFlairSheet(state = state, communityName = "subshell", onDismiss = {}) }
        }
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithText("Couldn't load user flair").fetchSemanticsNodes().size)
        assertTrue(composeRule.onAllNodesWithText("Helper").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Reddit didn't apply the flair", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aFailedLogoutIsSaid() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = SettingsDestination.ACCOUNTS.name,
                    accountSettingsRuntime = AccountSettingsRuntime(
                        onLogoutActiveAccount = { onComplete -> onComplete(false) },
                    ),
                )
            }
        }
        composeRule.waitForIdle()
        val logoutRow = hasText("Log out") and hasText("u/me_user")
        composeRule.onNode(hasScrollAction()).performScrollToNode(logoutRow)
        composeRule.onNode(logoutRow).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Log out") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()

        assertTrue(
            composeRule.onAllNodesWithText("Couldn't log out of u/me_user", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
