package app.subshell.prototype.media

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import app.subshell.prototype.applock.LocalAppLocked
import app.subshell.prototype.applock.LocalAppLockedNow
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MEDIA07 (#150): the app lock flips during the ON_STOP/ON_START dispatch, before
 * any recomposition. The video observer read the lock through composition, so on
 * return it still saw "unlocked" and resumed the video behind the lock window.
 * It now reads the lock live and resumes only after unlock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Media07AppLockVideoResumeComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aReturnToALockedAppResumesVideoOnlyAfterUnlock() {
        val state = SubshellState()
        val locked = mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppLocked provides locked.value,
                LocalAppLockedNow provides { locked.value },
            ) {
                ProvideVideoPlaybackCoordinator(state, VideoPlaybackCoordinator(composeRule.activity)) {}
            }
        }
        composeRule.waitForIdle()
        assertTrue(state.googleFeedVideoForeground)

        // Leave the app; while it is stopped the lock comes on (no frame runs).
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(state.googleFeedVideoForeground)
        locked.value = true
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertFalse("video resumed behind the lock", state.googleFeedVideoForeground)

        composeRule.runOnIdle { locked.value = false }
        composeRule.waitForIdle()
        assertTrue(state.googleFeedVideoForeground)
    }
}
