package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ModeratorContentControlsUiState
import io.github.brealorg.subshell.data.account.ModeratorLogUiState
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.ModeratorReportsUiState
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.app.ModerationQueueEntryPoints
import io.github.brealorg.subshell.ui.app.ModeratorToolsDialogs
import io.github.brealorg.subshell.ui.app.ModeratorToolsEntryPoints
import io.github.brealorg.subshell.ui.components.ModeratorToolsNavigationEntryPoints
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The root moderator dialogs in their own host. Two rules they carry: they
 * belong to an authenticated account only, and the mod-log dialog is the
 * root owner — while a moderator-tools workspace is open for a community,
 * the workspace shows the log instead and the root dialog stays away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ModeratorToolsDialogsComposeTest {
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
    fun theModLogDialogRendersForAnAuthenticatedAccountWithNoWorkspaceOpen() {
        host(authenticated = true, workspaceCommunity = null)

        composeRule.onNodeWithText("Mod log", substring = true).assertIsDisplayed()
    }

    @Test
    fun theModLogDialogStaysAwayWhileAWorkspaceOwnsThatCommunity() {
        host(authenticated = true, workspaceCommunity = "kotlin")

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Mod log", substring = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun nothingRendersForAnAccountThatIsNotAuthenticated() {
        host(authenticated = false, workspaceCommunity = null)

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Mod log", substring = true)
                .fetchSemanticsNodes().size,
        )
    }

    // STEP3 (moderation02a2, a3): the root host shows the reports and the
    // content-controls dialog once for the active account.
    @Test
    fun theReportsAndContentControlsDialogsRenderOnceForTheActiveAccount() {
        host(authenticated = true, workspaceCommunity = "kotlin") { account ->
            ModeratorToolsEntryPoints(
                moderatorReportsState = ModeratorReportsUiState(accountStableId = account, visible = true),
                moderatorContentControlsState = ModeratorContentControlsUiState(
                    accountStableId = account,
                    visible = true,
                    target = ModeratorReportTarget("t3_abc", "kotlin"),
                ),
            )
        }

        assertEquals(1, composeRule.onAllNodesWithText("Find reports").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Moderation controls").fetchSemanticsNodes().size)
    }

    @Test
    fun theReportsAndContentControlsDialogsStayAwayForAnotherAccount() {
        host(authenticated = true, workspaceCommunity = "kotlin") {
            ModeratorToolsEntryPoints(
                moderatorReportsState = ModeratorReportsUiState(accountStableId = "reddit:someone_else", visible = true),
                moderatorContentControlsState = ModeratorContentControlsUiState(
                    accountStableId = "reddit:someone_else",
                    visible = true,
                    target = ModeratorReportTarget("t3_abc", "kotlin"),
                ),
            )
        }

        assertEquals(0, composeRule.onAllNodesWithText("Find reports").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Moderation controls").fetchSemanticsNodes().size)
    }

    private fun host(
        authenticated: Boolean,
        workspaceCommunity: String?,
        tools: ((String) -> ModeratorToolsEntryPoints)? = null,
    ) {
        val sessions = if (authenticated) {
            localFixtureAccountSummaries(listOf("root")) + alice
        } else {
            localFixtureAccountSummaries(listOf("root"))
        }
        val state = SubshellState(
            initialAccountSessions = sessions,
            initialActiveAccountId = if (authenticated) alice.stableId else sessions.first().stableId,
        )
        val logState = ModeratorLogUiState(
            visible = true,
            accountStableId = state.activeAccountId,
            communityName = "kotlin",
            loaded = true,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                ModeratorToolsDialogs(
                    state = state,
                    redditModeration = ModerationQueueEntryPoints(),
                    moderatorTools = tools?.invoke(state.activeAccountId) ?: ModeratorToolsEntryPoints(moderatorLogState = logState),
                    moderatorToolsNavigation = ModeratorToolsNavigationEntryPoints(),
                    moderatorToolsWorkspaceCommunity = workspaceCommunity,
                )
            }
        }
        composeRule.waitForIdle()
    }
}
