package app.subshell.prototype.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.backup.rememberSettingsBackupRepository
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AccountConnectEntryPoints
import app.subshell.prototype.ui.app.AppSheetsHost
import app.subshell.prototype.ui.screens.HelpScreen
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.update.UPDATE01_ACTION_DEBUG_PREVIEW_WHATS_NEW
import app.subshell.prototype.update.UPDATE01_BETA_CHANNEL
import app.subshell.prototype.update.UPDATE01_MANIFEST_SCHEMA_VERSION
import app.subshell.prototype.update.UpdateManifestRelease
import app.subshell.prototype.update.UpdatePresentationCoordinator
import app.subshell.prototype.update.UpdatePresentationRequest
import app.subshell.prototype.update.UpdateStateStore
import app.subshell.prototype.update.consumeUpdatePresentationIntent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (update01a1b): an update found in the background is offered on Home
 * only, never over the backup onboarding; an explicit open shows it anywhere,
 * once; the debug preview does nothing outside debug; Help shows the changelog
 * inline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3UpdatePresentationComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val title = "subshell update available"
    private val release = UpdateManifestRelease(
        schemaVersion = UPDATE01_MANIFEST_SCHEMA_VERSION,
        channel = UPDATE01_BETA_CHANNEL,
        versionCode = BuildConfig.VERSION_CODE + 1,
        versionName = "next-beta",
        publishedAt = "2026-10-11T00:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/vnext-beta",
        apkSha256 = "0".repeat(64),
        headline = "A newer subshell beta is available.",
    )

    @After
    fun clearRequest() {
        UpdatePresentationCoordinator.request.value?.let(UpdatePresentationCoordinator::clear)
    }

    private fun showSheets(destination: MainDestination, onboardingDone: Boolean = true) {
        ensureWorkManagerInitialized(context)
        UpdateStateStore(context).saveAvailableRelease(release)
        if (onboardingDone) SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(initialInteractionSideConfigured = true)
        state.navigate(destination)
        composeRule.setContent {
            SubshellTheme {
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
        composeRule.waitForIdle()
    }

    private fun dialogs() = composeRule.onAllNodesWithText(title).fetchSemanticsNodes().size

    @Test
    fun anUpdateFoundInTheBackgroundIsOfferedOnHome() {
        showSheets(MainDestination.HOME)
        assertEquals(1, dialogs())
    }

    @Test
    fun anUpdateFoundInTheBackgroundWaitsAwayFromHome() {
        showSheets(MainDestination.SUBS)
        assertEquals(0, dialogs())
    }

    @Test
    fun theBackupOnboardingComesBeforeAnUpdateOffer() {
        showSheets(MainDestination.HOME, onboardingDone = false)
        assertEquals(0, dialogs())
    }

    @Test
    fun anExplicitOpenShowsTheUpdateAnywhereOnce() {
        showSheets(MainDestination.SUBS)
        composeRule.runOnIdle { UpdatePresentationCoordinator.openAvailable(release) }
        composeRule.waitForIdle()
        assertEquals(1, dialogs())
    }

    @Test
    fun theDebugPreviewDoesNothingOutsideDebug() {
        val intent = Intent(UPDATE01_ACTION_DEBUG_PREVIEW_WHATS_NEW)
        assertFalse(consumeUpdatePresentationIntent(context, intent, BuildConfig.VERSION_CODE, debug = false))
        assertEquals(null, UpdatePresentationCoordinator.request.value)

        assertTrue(consumeUpdatePresentationIntent(context, intent, BuildConfig.VERSION_CODE, debug = true))
        assertTrue(UpdatePresentationCoordinator.request.value is UpdatePresentationRequest.PreviewWhatsNew)
    }

    @Test
    fun helpShowsTheChangelogInline() {
        ensureWorkManagerInitialized(context)
        composeRule.setContent { SubshellTheme { HelpScreen(onBack = {}) } }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Changelog"))

        assertTrue(composeRule.onAllNodesWithText("Changelog").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, composeRule.onAllNodesWithText("No bundled changelog is available in this build.").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("View changelog").fetchSemanticsNodes().size)
    }
}
