package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.applock.LocalAppLocked
import app.subshell.prototype.backup.rememberSettingsBackupRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AccountConnectEntryPoints
import app.subshell.prototype.ui.app.AppSheetsHost
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MED6 (OCB-394): a dialog the app opens by itself (onboarding, What's new)
 * after the app lock appeared was drawn above the lock. It now waits until
 * the app is unlocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Med6NoDialogOverLockComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theOnboardingWaitsWhileTheAppIsLocked() {
        val locked = mutableStateOf(true)
        // A fresh install: the interaction side is not chosen yet, so the
        // onboarding dialog is due.
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            initialInteractionSideConfigured = false,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalAppLocked provides locked.value) {
                    AppSheetsHost(
                        state = state,
                        accountConnect = AccountConnectEntryPoints(),
                        backupRepository = rememberSettingsBackupRepository(),
                        accountSwitcherVisibleState = mutableStateOf(false),
                        requestedSettingsDestinationNameState = mutableStateOf(SettingsDestination.ROOT.name),
                        mutedCommunitiesSheetVisibleState = mutableStateOf(false),
                        communityMuteEditorNameState = mutableStateOf(null),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Which side should subshell favor?").assertDoesNotExist()

        locked.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Which side should subshell favor?").assertIsDisplayed()
    }
}
