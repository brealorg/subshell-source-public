package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.RedditCompatibilityConfigurationController
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.harness.FakeAndroidKeyStore
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.subshell.prototype.auth.RedditApiValues
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.theme.SubshellTheme
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
 * #240 (ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1): Settings' RedReader
 * compatibility dialog shows the redirect URI and user agent read only; Edit
 * warns first, the fields then show their own errors and keep Save off while
 * a value is invalid; Reset to defaults is offered only for changed values.
 * A fresh install edits them in onboarding's Advanced section
 * (ISSUE240_ONBOARDING_EDITS_THE_REDDIT_API_VALUES_V1) and saves them with
 * the Client ID.
 * The section is tested outside its AlertDialog: a text field in a dialog
 * window never goes idle under Robolectric (HANDOVER-HISTORY 2026-10-10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue240RedditApiValuesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private var resets = 0

    private fun show(stored: RedditApiValues): RedditApiValuesDraft {
        val draft = RedditApiValuesDraft(stored)
        composeRule.setContent {
            SubshellTheme {
                RedditApiValuesSection(
                    stored = stored,
                    draft = draft,
                    enabled = true,
                    onResetToDefaults = { resets += 1 },
                )
            }
        }
        composeRule.waitForIdle()
        return draft
    }

    private fun count(text: String) =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun theValuesAreReadOnlyUntilTheReaderConfirmsTheWarning() {
        val draft = show(DEFAULTS)

        composeRule.onNodeWithText(RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI).assertIsDisplayed()
        composeRule.onNodeWithText(RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals("nothing to reset while the values are the defaults", 0, count("Reset to defaults"))

        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()
        assertEquals(1, count("Wrong values break sign-in."))
        assertEquals(0, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Edit anyway").performClick()
        composeRule.waitForIdle()

        assertTrue(draft.editing)
        assertEquals(2, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun cancellingTheWarningLeavesTheValuesReadOnly() {
        val draft = show(DEFAULTS)

        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertFalse(draft.editing)
        assertEquals(0, count("Wrong values break sign-in."))
        assertEquals(0, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun anInvalidValueShowsItsErrorAndKeepsSaveOff() {
        val draft = show(DEFAULTS)
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Edit anyway").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction() and hasText("Redirect URI"))
            .performTextReplacement("https://example.com/callback")
        composeRule.onNode(hasSetTextAction() and hasText("User agent"))
            .performTextReplacement("")
        composeRule.waitForIdle()

        assertEquals(1, count("A web address can’t be the sign-in callback"))
        assertEquals(1, count("Enter a user agent"))
        assertFalse(redditApiSaveEnabled("", clientIdValid = false, stored = DEFAULTS, draft = draft))

        composeRule.onNode(hasSetTextAction() and hasText("Redirect URI"))
            .performTextReplacement("myreader://auth_done")
        composeRule.onNode(hasSetTextAction() and hasText("User agent"))
            .performTextReplacement("org.example.reader/2.0")
        composeRule.waitForIdle()

        assertEquals(0, count("A web address can’t be the sign-in callback"))
        assertEquals(0, count("Enter a user agent"))
        assertTrue(
            "a blank client ID keeps the stored one and saves the edited values",
            redditApiSaveEnabled("", clientIdValid = false, stored = DEFAULTS, draft = draft),
        )
    }

    @Test
    fun useDefaultsFillsTheShippedValuesBackIn() {
        val draft = show(CUSTOM)
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Edit anyway").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Use defaults").performClick()
        composeRule.waitForIdle()

        assertEquals(RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI, draft.redirectUri)
        assertEquals(RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT, draft.userAgent)
        assertTrue(redditApiSaveEnabled("", clientIdValid = false, stored = CUSTOM, draft = draft))
    }

    @Test
    fun changedValuesOfferResetToDefaultsInOneTap() {
        show(CUSTOM)

        assertEquals(1, count("Changed from the defaults"))
        composeRule.onNodeWithText("Reset to defaults").performClick()
        composeRule.waitForIdle()

        assertEquals(1, resets)
    }

    @Test
    fun saveNeedsAClientIdUntilOneIsStored() {
        val unconfigured = DEFAULTS.copy(clientIdConfigured = false)
        val draft = RedditApiValuesDraft(unconfigured)

        assertFalse(redditApiSaveEnabled("", clientIdValid = false, stored = unconfigured, draft = draft))
        assertTrue(redditApiSaveEnabled(CLIENT_ID, clientIdValid = true, stored = unconfigured, draft = draft))
        assertFalse(
            "nothing to save: blank client ID and untouched values",
            redditApiSaveEnabled("", clientIdValid = false, stored = DEFAULTS, draft = RedditApiValuesDraft(DEFAULTS)),
        )
    }

    @Test
    fun aFreshInstallSavesEditedValuesWithTheClientId() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        FakeAndroidKeyStore.install()
        try {
            var reloads = 0
            composeRule.setContent {
                SubshellTheme {
                    RedditOnboardingScreen(
                        authenticatedAccountUsername = null,
                        connectAccountState = ConnectAccountUiState(
                            phase = ConnectAccountPhase.IDLE,
                            providerLabel = "Reddit",
                        ),
                        onConnectAccount = {},
                        onCancelConnectAccount = {},
                        onResetConnectAccount = {},
                        onRedditConfigurationChanged = { reloads += 1 },
                        onFinish = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) { count("I already have a Client ID") > 0 }
            clickFirst("I already have a Client ID")
            clickFirst("Advanced")
            composeRule.onNode(hasSetTextAction() and hasText("Client ID")).performTextInput(CLIENT_ID)
            composeRule.onNode(hasSetTextAction() and hasText("Redirect URI"))
                .performTextReplacement("https://example.com/callback")
            composeRule.waitForIdle()
            composeRule.onNode(hasText("Save Client ID") and hasClickAction()).assertIsNotEnabled()

            composeRule.onNode(hasSetTextAction() and hasText("Redirect URI"))
                .performTextReplacement(CUSTOM.redirectUri)
            composeRule.onNode(hasSetTextAction() and hasText("User agent"))
                .performTextReplacement(CUSTOM.userAgent)
            composeRule.waitForIdle()
            composeRule.onNode(hasText("Save Client ID") and hasClickAction()).assertIsEnabled()
            clickFirst("Save Client ID")
            composeRule.waitUntil(5_000) { reloads > 0 }

            assertEquals(CUSTOM, RedditCompatibilityConfigurationController(context).apiValues())
        } finally {
            EncryptedAuthSessionStore(context).deleteOAuthClientSelection()
            FakeAndroidKeyStore.uninstall()
        }
    }

    private fun clickFirst(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private companion object {
        const val CLIENT_ID = "Issue240_Client-Id01"
        val DEFAULTS = RedditApiValues(
            clientIdConfigured = true,
            redirectUri = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
            userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
        )
        val CUSTOM = DEFAULTS.copy(
            redirectUri = "myreader://auth_done",
            userAgent = "org.example.reader/2.0",
        )
    }
}
