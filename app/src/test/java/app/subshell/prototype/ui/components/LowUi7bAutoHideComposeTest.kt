package app.subshell.prototype.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LOWUI7b: self-hiding chrome follows the system's accessibility timeout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowUi7bAutoHideComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun recommending(millis: Long) = object : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long = millis
    }

    private fun autoHideWith(manager: AccessibilityManager): Long {
        var result = -1L
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides manager) {
                result = rememberAccessibleAutoHideMillis(2_500L)
            }
        }
        composeRule.waitForIdle()
        return result
    }

    // LOWUI7_AUTO_HIDE_FOLLOWS_THE_ACCESSIBILITY_TIMEOUT_V1 (OCB-142/318)
    @Test
    fun aLongerAccessibilityTimeoutIsUsed() {
        assertEquals(30_000L, autoHideWith(recommending(30_000L)))
    }

    @Test
    fun theDelayIsNeverShorterThanTheBase() {
        assertEquals(2_500L, autoHideWith(recommending(100L)))
    }
}
