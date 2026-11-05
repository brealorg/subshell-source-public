package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.applock.LocalAppLocked
import io.github.brealorg.subshell.backup.rememberSettingsBackupRepository
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.app.AccountConnectEntryPoints
import io.github.brealorg.subshell.ui.app.AppSheetsHost
import io.github.brealorg.subshell.ui.screens.SettingsDestination
import io.github.brealorg.subshell.ui.theme.SubshellTheme
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
        // The backup folder question is due (the side is chosen; since #271 a
        // fresh install answers both in the setup flow instead).
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            initialInteractionSideConfigured = true,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        context.getSharedPreferences("subshell_backup_preferences", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
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
        composeRule.onNodeWithText("Protect your settings").assertDoesNotExist()

        locked.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Protect your settings").assertIsDisplayed()
    }
}
