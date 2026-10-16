package app.subshell.prototype.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE45_BAR_ICONS_FOLLOW_THE_APP_THEME_AFTER_ROTATION_V1 (#45): with the
 * light theme on a dark system, a rotation (handled in place) ran
 * enableEdgeToEdge(), which turned the bar icons white, and the theme never
 * set its dark icons again.
 *
 * STEP3 (theme01): the dark theme asks for light bar icons on a light system,
 * and a theme told not to manage the bars leaves the window alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night")
class Issue45BarIconsAfterRotationComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theLightThemeSetsDarkBarIconsAgainAfterARotation() {
        val portrait = Configuration(composeRule.activity.resources.configuration)
        var configuration by mutableStateOf(portrait)
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                SubshellTheme(mode = ThemeMode.LIGHT) { Text("feed") }
            }
        }
        composeRule.waitForIdle()
        val activity = composeRule.activity
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)

        // What enableEdgeToEdge() does on a dark system.
        composeRule.runOnUiThread {
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            configuration = Configuration(portrait).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = portrait.screenHeightDp
                screenHeightDp = portrait.screenWidthDp
            }
        }
        composeRule.waitForIdle()

        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun theDarkThemeSetsLightBarIconsOnALightSystem() {
        val activity = composeRule.activity
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        // Dark icons first, as a light system's window has them.
        composeRule.runOnUiThread {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
        composeRule.setContent {
            SubshellTheme(mode = ThemeMode.DARK) { Text("feed") }
        }
        composeRule.waitForIdle()

        assertFalse(controller.isAppearanceLightStatusBars)
        assertFalse(controller.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun aThemeThatDoesNotManageTheBarsLeavesTheWindowAlone() {
        val activity = composeRule.activity
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        composeRule.runOnUiThread {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
        composeRule.setContent {
            SubshellTheme(mode = ThemeMode.DARK, manageSystemBarContrast = false) { Text("viewer") }
        }
        composeRule.waitForIdle()

        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }
}
