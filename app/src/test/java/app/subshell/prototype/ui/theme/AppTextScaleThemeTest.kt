package app.subshell.prototype.ui.theme

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #32: the in-app text size multiplies the system font scale for everything inside the theme. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], fontScale = 1.15f)
class AppTextScaleThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun fontScaleInside(textScale: Float): Float {
        var inside = 0f
        composeRule.setContent {
            SubshellTheme(textScale = textScale, manageSystemBarContrast = false) {
                inside = LocalDensity.current.fontScale
            }
        }
        composeRule.waitForIdle()
        return inside
    }

    @Test
    fun theDefaultKeepsTheSystemScale() {
        assertEquals(1.15f, fontScaleInside(AppTextScale.DEFAULT.factor), 0.001f)
    }

    @Test
    fun aLargerTextSizeMultipliesTheSystemScale() {
        assertEquals(1.15f * 1.30f, fontScaleInside(AppTextScale.LARGER.factor), 0.001f)
    }

    @Test
    fun theStepsAreSmallDefaultLargeLarger() {
        assertEquals(listOf(0.85f, 1.0f, 1.15f, 1.30f), AppTextScale.entries.map(AppTextScale::factor))
        assertEquals(listOf("85 %", "100 %", "115 %", "130 %"), AppTextScale.entries.map(AppTextScale::label))
    }
}
