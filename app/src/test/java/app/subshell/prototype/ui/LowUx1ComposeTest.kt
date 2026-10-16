package app.subshell.prototype.ui

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
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ModeratorLogUiState
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.account.RedditModeratorLogEntry
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.UserFlairFailureKind
import app.subshell.prototype.data.account.UserFlairOption
import app.subshell.prototype.data.account.UserFlairSnapshot
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.components.AccountSettingsRuntime
import app.subshell.prototype.ui.components.InboxModmailComposer
import app.subshell.prototype.ui.components.ModeratorLogDialog
import app.subshell.prototype.ui.components.UserFlairSheet
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
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
