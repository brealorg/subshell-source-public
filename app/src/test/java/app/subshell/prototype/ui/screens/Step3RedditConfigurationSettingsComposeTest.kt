package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.RedditCompatibilityConfigurationController
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.harness.FakeAndroidKeyStore
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth07c1): Settings → Accounts reads the RedReader compatibility
 * choice from the real encrypted store and says that changes apply on their
 * own (no "restart subshell"). The save/remove dialog holds a text field in an
 * AlertDialog, which never goes idle under Robolectric (HANDOVER-HISTORY
 * 2026-10-10); the save → reload wiring is boundary-guards
 * CONFIGURATION_CHANGE_RELOADS_THE_HOST and the onboarding save in
 * Step3RedditOnboardingComposeTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3RedditConfigurationSettingsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun installKeyStore() {
        FakeAndroidKeyStore.install()
    }

    @After
    fun uninstallKeyStore() {
        EncryptedAuthSessionStore(context).deleteOAuthClientSelection()
        FakeAndroidKeyStore.uninstall()
    }

    private fun showAccounts() {
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, "test")
        val state = SubshellState(initialInteractionSideConfigured = true)
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = SettingsDestination.ACCOUNTS.name,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText("RedReader compatibility"))
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aStoredClientIdShowsAsConfiguredAndAppliesByItself() {
        RedditCompatibilityConfigurationController(context).save(CLIENT_ID)

        showAccounts()
        composeRule.waitUntil(5_000) { count(CONFIGURED) == 1 }

        assertEquals(0, count(NOT_CONFIGURED))
        assertEquals(
            0,
            composeRule.onAllNodesWithText("restart subshell", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().size,
        )
    }

    private companion object {
        // A made-up identifier in Reddit's installed-app format, not a real one.
        const val CLIENT_ID = "Step3_Client-Id01"
        const val CONFIGURED = "Configured locally · changes apply automatically"
        const val NOT_CONFIGURED = "Add a user-supplied OAuth client ID in encrypted no-backup storage"
    }
}
