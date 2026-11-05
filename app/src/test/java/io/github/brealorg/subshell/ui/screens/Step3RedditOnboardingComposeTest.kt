package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.auth.RedditCompatibilityConfigurationController
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ConnectAccountPhase
import io.github.brealorg.subshell.data.account.ConnectAccountUiState
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
import io.github.brealorg.subshell.harness.FakeAndroidKeyStore
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.SubshellApp
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.app.AccountConnectEntryPoints
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth07c2, owner approved: tests on the real encrypted store): the
 * first-run Reddit onboarding is shown while the device has no Reddit account
 * and not once one is signed in; its store links open only RedReader's
 * Google Play and F-Droid pages; a pasted Client ID is saved in the real store
 * and the app is asked to reload its auth host; the Advanced section shows
 * the RedReader redirect URI and user agent as editable defaults (#240,
 * Issue240RedditApiValuesComposeTest); once configured, Connect
 * Reddit starts the app's one connect flow. The Android key store is the test
 * stand-in [FakeAndroidKeyStore].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3RedditOnboardingComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val local = AccountSessionSummary(
        stableId = "local:test",
        username = "local",
        source = AccountSessionSource.LOCAL_FIXTURE,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Local",
    )
    private val signedIn = AccountSessionSummary(
        stableId = "reddit:test",
        username = "someone",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private var reloads = 0
    private var connects = 0

    @Before
    fun installKeyStore() {
        FakeAndroidKeyStore.install()
    }

    @After
    fun uninstallKeyStore() {
        EncryptedAuthSessionStore(context).deleteOAuthClientSelection()
        FakeAndroidKeyStore.uninstall()
    }

    private fun showApp(account: AccountSessionSummary) {
        ensureWorkManagerInitialized(context)
        // The first-run backup folder prompt would cover the screen.
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                accountConnect = AccountConnectEntryPoints(
                    connectAccountState = ConnectAccountUiState(
                        phase = ConnectAccountPhase.IDLE,
                        providerLabel = "Reddit",
                    ),
                    onConnectAccount = { connects += 1 },
                ),
                onRedditConfigurationChanged = { reloads += 1 },
            )
        }
        composeRule.waitForIdle()
    }

    private fun showScreen(uriHandler: UriHandler) {
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    RedditOnboardingScreen(
                        authenticatedAccountUsername = null,
                        connectAccountState = ConnectAccountUiState(
                            phase = ConnectAccountPhase.IDLE,
                            providerLabel = "Reddit",
                        ),
                        onConnectAccount = { connects += 1 },
                        onCancelConnectAccount = {},
                        onResetConnectAccount = {},
                        onRedditConfigurationChanged = { reloads += 1 },
                        onFinish = {},
                    )
                }
            }
        }
        waitFor(INTRO)
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    // The configuration is read on the IO dispatcher, which waitForIdle does not wait for.
    private fun waitFor(text: String) {
        composeRule.waitUntil(5_000) { count(text) > 0 }
    }

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun aDeviceWithoutARedditAccountIsShownTheOnboarding() {
        showApp(local)

        waitFor(INTRO)
    }

    @Test
    fun aSignedInRedditAccountIsNotShownTheOnboarding() {
        showApp(signedIn)

        assertEquals(0, count(INTRO))
        assertEquals(0, count("Ready to connect"))
    }

    @Test
    fun theStoreLinksOpenOnlyRedReadersPages() {
        val opened = mutableListOf<String>()
        showScreen(
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            },
        )

        click("Show me how to get a Client ID")
        composeRule.onAllNodes(hasContentDescription("Get it on Google Play") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodes(hasContentDescription("Get it on F-Droid") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                "https://play.google.com/store/apps/details?id=org.quantumbadger.redreader",
                "https://f-droid.org/packages/org.quantumbadger.redreader",
            ),
            opened,
        )
    }

    @Test
    fun aPastedClientIdIsStoredAndTheAppReloadsItsAuthHost() {
        showApp(local)
        waitFor(INTRO)

        click("I already have a Client ID")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(CLIENT_ID)
        composeRule.waitForIdle()
        click("Save Client ID")
        composeRule.waitUntil(5_000) { reloads > 0 }
        composeRule.waitForIdle()

        assertEquals(1, reloads)
        assertTrue(RedditCompatibilityConfigurationController(context).isConfigured())
    }

    @Test
    fun advancedShowsTheRedReaderRedirectAndUserAgentAsDefaults() {
        showScreen(
            object : UriHandler {
                override fun openUri(uri: String) = Unit
            },
        )

        click("I already have a Client ID")
        click("Advanced")

        assertEquals(1, count("Redirect URI"))
        assertEquals(1, count(RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI))
        assertEquals(1, count("User agent"))
        assertEquals(1, count(RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT))
    }

    @Test
    fun aConfiguredDeviceConnectsThroughTheAppsConnectFlow() {
        RedditCompatibilityConfigurationController(context).save(CLIENT_ID)
        showApp(local)
        waitFor("Ready to connect")

        click("Connect Reddit")

        assertEquals(1, connects)
        assertEquals(0, reloads)
    }

    private companion object {
        const val INTRO = "Connect subshell to Reddit"

        // A made-up identifier in Reddit's installed-app format, not a real one.
        const val CLIENT_ID = "Step3_Client-Id01"
    }
}
