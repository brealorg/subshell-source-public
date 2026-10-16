package app.subshell.prototype.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #31: the pure black flag puts the dark theme on true black while
 * the container tones keep a visible step, and it never touches light.
 * (The pickers set it together with Dark; see ThemeChoiceTest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PureBlackDarkThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backgroundAndBaseSurfacesAreBlackAndContainersStillStepUp() {
        val scheme = pureBlackColorScheme(DarkColors)

        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertEquals(Color.Black, scheme.surfaceContainerLowest)
        val steps = listOf(
            scheme.surfaceContainerLowest,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
            scheme.surfaceBright,
        ).map(Color::luminance)
        assertTrue(steps.zipWithNext().all { (lower, higher) -> higher > lower })
        // Accents and the lines that separate content come from the base.
        assertEquals(DarkColors.primary, scheme.primary)
        assertEquals(DarkColors.outline, scheme.outline)
        assertEquals(DarkColors.outlineVariant, scheme.outlineVariant)
        assertEquals(DarkColors.onSurface, scheme.onSurface)
    }

    private fun schemeFor(mode: ThemeMode, pureBlack: Boolean): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            SubshellTheme(mode = mode, pureBlack = pureBlack, manageSystemBarContrast = false) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()
        return scheme
    }

    @Test
    fun darkThemeUsesPureBlackOnlyWhenAskedTo() {
        assertEquals(Color.Black, schemeFor(ThemeMode.DARK, pureBlack = true).background)
    }

    @Test
    fun darkThemeWithoutTheOptionIsUnchanged() {
        val scheme = schemeFor(ThemeMode.DARK, pureBlack = false)
        assertEquals(DarkColors.background, scheme.background)
        assertNotEquals(Color.Black, scheme.background)
    }

    @Test
    fun lightThemeIgnoresTheOption() {
        assertEquals(LightColors.background, schemeFor(ThemeMode.LIGHT, pureBlack = true).background)
    }
}
