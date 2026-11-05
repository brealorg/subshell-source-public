package io.github.brealorg.subshell.export

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ConnectAccountPhase
import io.github.brealorg.subshell.data.account.ConnectAccountUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.components.AccountSettingsRuntime
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.screens.RedditDataExportRow
import io.github.brealorg.subshell.ui.screens.SettingsDestination
import io.github.brealorg.subshell.ui.screens.SettingsScreen
import io.github.brealorg.subshell.ui.screens.settingsSearchResult
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1: Settings › Accounts has "Export
 * your Reddit data" for a signed-in account; a tap starts one export (and says
 * so in the tray), the row shows how the newest export went, and a tap while
 * one runs starts nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RedditExport01SettingsRowComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val me = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @After
    fun tearDown() {
        if (WorkManager.isInitialized()) WorkManager.getInstance(context).cancelUniqueWork(REDDIT_EXPORT_UNIQUE_WORK)
    }

    @Test
    fun aSignedInAccountCanStartAnExport() {
        ensureWorkManagerInitialized(context)
        val notices = ViewportErrorHostState()
        val state = SubshellState(
            initialAccountSessions = listOf(me),
            initialActiveAccountId = me.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalViewportErrorHostState provides notices) {
                SubshellTheme {
                    SettingsScreen(
                        state = state,
                        backupRepository = SettingsBackupRepository(context, "test"),
                        onBack = {},
                        initialDestinationName = SettingsDestination.ACCOUNTS.name,
                        accountSettingsRuntime = AccountSettingsRuntime(
                            connectAccountState = ConnectAccountUiState(phase = ConnectAccountPhase.IDLE, providerLabel = "Reddit"),
                            onConnectAccount = {},
                            onResetConnectAccount = {},
                            onReconnectAccount = {},
                            onLogoutActiveAccount = { onComplete -> onComplete(false) },
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val row = hasText("Export your Reddit data")
        composeRule.onNode(hasScrollAction()).performScrollToNode(row)
        composeRule.onNode(row).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(REDDIT_EXPORT_UNIQUE_WORK).get()
        assertEquals(1, work.size)
        assertEquals("Exporting your Reddit data", notices.currentRequest?.presentation?.title)
    }

    @Test
    fun theRowSaysHowTheExportGoesAndATapWhileItRunsStartsNothing() {
        val status = androidx.compose.runtime.mutableStateOf<RedditDataExportStatus?>(null)
        var started = 0
        composeRule.setContent {
            SubshellTheme { RedditDataExportRow(status = status.value, onExport = { started += 1 }) }
        }
        fun tap() {
            composeRule.onNode(hasText("Export your Reddit data")).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        fun shows(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

        assertTrue(shows("as a ZIP in Downloads"))
        tap()
        assertEquals(1, started)

        status.value = RedditDataExportStatus.Running(done = 3, total = 11)
        composeRule.waitForIdle()
        assertTrue(shows("Exporting… 3 of 11 lists"))
        tap()
        status.value = RedditDataExportStatus.Waiting
        composeRule.waitForIdle()
        tap()
        assertEquals(1, started)

        status.value = RedditDataExportStatus.Saved("Downloads/subshell/x.zip", items = 1234, incompleteLists = 1)
        composeRule.waitForIdle()
        assertTrue(shows("1234 items saved to Downloads/subshell/x.zip. 1 list is incomplete"))
        tap()
        assertEquals(2, started)
    }

    @Test
    fun settingsSearchFindsTheExport() {
        assertTrue("export-reddit-data" in settingsSearchResult("export reddit").itemKeys)
        assertTrue("export-reddit-data" in settingsSearchResult("takeout").itemKeys)
        assertEquals(setOf("backup"), settingsSearchResult("backup export").itemKeys)
    }
}
