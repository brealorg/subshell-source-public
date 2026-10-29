package app.subshell.prototype.ui

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import app.subshell.prototype.ui.theme.AppUiScale
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE278 (reader request, owner 2026-10-28): one UI size scales the whole
 * interface; touch targets keep 48 dp in physical size, and the layout's window
 * size class is read without the scale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w560dp-h891dp")
class Issue278UiSizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class Seen {
        lateinit var outside: Density
        lateinit var inside: Density
        var touchTarget: Dp = 0.dp
        var sizeClass: WindowSizeClass? = null
        var visibleFontScale = 0f
    }

    private fun measure(scale: Float): Seen {
        val seen = Seen()
        composeRule.setContent {
            seen.outside = LocalDensity.current
            SubshellTheme(uiScale = scale) {
                seen.inside = LocalDensity.current
                seen.touchTarget = LocalMinimumInteractiveComponentSize.current
                seen.sizeClass = currentUnscaledWindowSizeClass()
                seen.visibleFontScale = visibleFontScale()
            }
        }
        composeRule.waitForIdle()
        return seen
    }

    @Test
    fun aSmallerUiScalesEveryDpAndKeepsTheTextScale() {
        val seen = measure(AppUiScale.SMALLEST.factor)

        assertEquals(seen.outside.density * 0.85f, seen.inside.density, 0.001f)
        assertEquals("text follows the dp, Text size stays its own", seen.outside.fontScale, seen.inside.fontScale, 0.001f)
    }

    @Test
    fun touchTargetsStay48DpInPhysicalSize() {
        val seen = measure(AppUiScale.SMALLEST.factor)

        val physicalPx = seen.touchTarget.value * seen.inside.density
        assertEquals(48f * seen.outside.density, physicalPx, 0.5f)
    }

    @Test
    fun theLayoutClassIgnoresTheUiSize() {
        // 560 dp is a compact width; at 85 % it would read as 659 dp (medium: the rail).
        val seen = measure(AppUiScale.SMALLEST.factor)

        val sizeClass = requireNotNull(seen.sizeClass)
        assertFalse(sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND))
        assertFalse(useNavigationRail(sizeClass))
    }

    @Test
    fun theSettingIsStoredAndBackedUp() {
        val settings = SubshellSettings(appUiScale = AppUiScale.SMALLER)
        val map = settings.toStorageMap()

        assertEquals("SMALLER", map[SubshellSettings.APP_UI_SCALE])
        assertTrue(SubshellSettings.APP_UI_SCALE in SubshellSettings.storageKeys)
        assertEquals(AppUiScale.SMALLER, SubshellSettings.fromStorageMap(map).appUiScale)
        assertEquals("nothing changes for anyone who doesn't choose it", AppUiScale.DEFAULT, SubshellSettings().appUiScale)
    }

    @Test
    fun largeTextIsJudgedAsItLooksOnScreen() {
        // Large text (here 1.32 = system 1.15 x Text size 1.15) asks for the tall
        // bar; at 85 % UI size it looks like 1.12 and the normal bar fits.
        val seen = measure(AppUiScale.SMALLEST.factor)

        assertEquals(seen.outside.fontScale * 0.85f, seen.visibleFontScale, 0.001f)
        assertEquals(64.dp, navigationBarBaseHeightForFontScale(1.32f * 0.85f))
        assertEquals(88.dp, navigationBarBaseHeightForFontScale(1.32f))
    }
}
