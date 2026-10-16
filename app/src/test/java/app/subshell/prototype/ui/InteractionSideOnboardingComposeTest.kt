package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the interaction-side onboarding dialog as hosted by
 * SubshellApp today, written before InteractionSideOnboarding and its two
 * preview composables move to their own file. Current behaviour, not a
 * specification: the dialog shows while the side is unconfigured, choosing
 * a side previews it (the confirm button follows), confirming closes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class InteractionSideOnboardingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogPreviewsTheChosenSideAndConfirmDismissesIt() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSide = InteractionSide.RIGHT,
            initialInteractionSideConfigured = false,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
            )
        }
        composeRule.waitForIdle()
        assertTrue(state.commentActionOnboardingVisible)
        composeRule.onNodeWithText("Which side should subshell favor?").assertIsDisplayed()
        composeRule.onNodeWithText("Use Right").assertIsDisplayed()

        composeRule.onNode(hasText("Left") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        assertEquals(InteractionSide.LEFT, state.interactionSide)
        assertTrue(state.commentActionOnboardingVisible)
        composeRule.onNodeWithText("Use Left").assertIsDisplayed()

        composeRule.onNodeWithText("Use Left").performClick()
        composeRule.waitForIdle()
        assertFalse(state.commentActionOnboardingVisible)
        assertEquals(InteractionSide.LEFT, state.interactionSide)
        composeRule.onNodeWithText("Which side should subshell favor?").assertDoesNotExist()
    }
}
