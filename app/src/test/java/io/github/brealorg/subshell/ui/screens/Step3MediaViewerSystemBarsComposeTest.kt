package io.github.brealorg.subshell.ui.screens

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.model.RemoteCommentMedia
import io.github.brealorg.subshell.model.RemoteCommentMediaKind
import io.github.brealorg.subshell.ui.SubshellApp
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (theme01 MEDIA_VIEWERS_RETAIN_DARK_SYSTEM_BAR_OVERRIDE): with the light
 * theme, the app gives the bars dark icons, but a full-screen image or a
 * comment image is shown on black, so the viewer turns the bar icons light and
 * keeps them light after a rotation (the app theme must not take them back
 * while a viewer is open). Closing the viewer gives the theme its icons back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3MediaViewerSystemBarsComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val image = SampleData.posts.first { it.kind == PostKind.IMAGE }
    private val signedIn = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun showApp(): Pair<SubshellState, () -> Unit> {
        val activity = composeRule.activity
        ensureWorkManagerInitialized(activity.application)
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            // A local account alone gets the sign-in onboarding instead of
            // the app; a signed-in account kept on the device avoids it.
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + signedIn,
            initialInteractionSideConfigured = true,
        )
        state.updateThemeMode(ThemeMode.LIGHT)
        val portrait = Configuration(activity.resources.configuration)
        var configuration by mutableStateOf(portrait)
        val coordinator = testVideoPlaybackCoordinator(activity).apply { release() }
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                SubshellApp(videoPlaybackCoordinator = coordinator, state = state)
            }
        }
        composeRule.waitForIdle()
        val rotate = {
            composeRule.runOnUiThread {
                configuration = Configuration(portrait).apply {
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                    screenWidthDp = portrait.screenHeightDp
                    screenHeightDp = portrait.screenWidthDp
                }
            }
            composeRule.waitForIdle()
        }
        return state to rotate
    }

    private fun controller(): WindowInsetsControllerCompat {
        val window = composeRule.activity.window
        return WindowCompat.getInsetsController(window, window.decorView)
    }

    private fun assertLightIcons(message: String) {
        assertFalse(message, controller().isAppearanceLightStatusBars)
        assertFalse(message, controller().isAppearanceLightNavigationBars)
    }

    private fun assertDarkIcons(message: String) {
        assertTrue(message, controller().isAppearanceLightStatusBars)
        assertTrue(message, controller().isAppearanceLightNavigationBars)
    }

    @Test
    fun anImageViewerKeepsLightBarIconsThroughARotationAndGivesThemBack() {
        val (state, rotate) = showApp()
        assertDarkIcons("the light theme draws dark bar icons")

        composeRule.runOnIdle { state.openMedia(image) }
        composeRule.waitForIdle()

        rotate()
        assertLightIcons("a rotation does not hand the bars back to the theme")

        composeRule.runOnIdle { state.closeMedia() }
        composeRule.waitForIdle()
        assertDarkIcons("closing the viewer gives the theme its icons back")
    }

    @Test
    fun aCommentImageViewerKeepsLightBarIconsThroughARotation() {
        val (state, rotate) = showApp()

        composeRule.runOnIdle {
            assertTrue(
                state.openCommentMedia(
                    commentId = "t1_step3_bars",
                    media = RemoteCommentMedia(
                        uri = "https://i.redd.it/step3_bars.png",
                        kind = RemoteCommentMediaKind.IMAGE,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        assertLightIcons("a comment image on black gets light bar icons")

        rotate()
        assertLightIcons("a rotation does not hand the bars back to the theme")

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        assertDarkIcons("leaving the comment image gives the theme its icons back")
    }
}
