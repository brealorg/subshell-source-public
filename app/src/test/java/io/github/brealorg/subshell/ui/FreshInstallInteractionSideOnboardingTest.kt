package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A fresh install must still be asked which side to favor after the Reddit
 * onboarding. The state is built by the production factory on empty
 * preferences; the first composition runs the drawer-side migration (FOLLOW
 * becomes an explicit side) — that migration must not write
 * `interaction_side`, because its presence is what marks the side as
 * configured on the next launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class FreshInstallInteractionSideOnboardingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstLaunchDoesNotMarkTheInteractionSideAsConfigured() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val preferences = context.getSharedPreferences("subshell_preferences", 0)
        assertFalse(preferences.contains(SubshellSettings.INTERACTION_SIDE))
        var state: SubshellState? = null

        composeRule.setContent {
            state = rememberSubshellState().also { current ->
                SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = current)
            }
        }
        composeRule.waitForIdle()

        val launched = requireNotNull(state)
        assertTrue(launched.commentActionOnboardingVisible)
        // The drawer side is the default RIGHT (DEFAULTS102_OWNER_SETTINGS_BECOME_DEFAULTS_V1,
        // owner 2026-11-11; it used to be FOLLOW and was written out as RIGHT) …
        assertEquals(DrawerSidePreference.RIGHT, launched.drawerSidePreference)
        // … but the side itself is not, so the next launch still asks.
        assertFalse(preferences.contains(SubshellSettings.INTERACTION_SIDE))
        assertFalse(preferences.contains(SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT))
    }
}
