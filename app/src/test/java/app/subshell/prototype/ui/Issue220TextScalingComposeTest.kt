package app.subshell.prototype.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.sp
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.appTextScaleDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #220 (ISSUE220_APP_TEXT_SIZE_KEEPS_NONLINEAR_SCALING_V1): the in-app
 * text size used a linear Density, so on top of a large system font a 30 sp
 * headline grew by the full product of both scales. Android 14 damps large
 * text non-linearly; the app's own scale now goes through the same converter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", fontScale = 2f)
class Issue220TextScalingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun headlinePx(textScale: Float): Pair<Float, Float> {
        var px = 0f
        var pixelDensity = 0f
        composeRule.setContent {
            SubshellTheme(textScale = textScale) {
                val density = LocalDensity.current
                px = with(density) { 30.sp.toPx() }
                pixelDensity = density.density
            }
        }
        composeRule.waitForIdle()
        return px to pixelDensity
    }

    @Test
    fun theAppsTextSizeOnALargeSystemFontIsDampedLikeTheSystemsOwn() {
        val (px, density) = headlinePx(1.3f)
        val linear = 30f * 2f * 1.3f * density
        assertTrue("30 sp at 200 % × 130 % was $px px, linear would be $linear", px < linear * 0.9f)
        // Larger than at the system size alone (38 px on the platform's 2.0 curve).
        assertTrue(px > 38f * density)
    }

    @Test
    fun aboveThePlatformsLargestTableSizesGrowSmoothly() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val configuration = context.resources.configuration
        fun px(scale: Float) = with(appTextScaleDensity(context, configuration, scale)) { 30.sp.toPx() }
        // No jump at 2.0 (the platform alone goes from its curve to linear there).
        assertEquals(px(2f), px(2.0001f), 0.05f)
        assertTrue(px(2.6f) > px(2f))
        assertTrue(px(2.6f) < 30f * 2.6f * context.resources.displayMetrics.density * 0.9f)
    }

    @Test
    fun withoutAnAppTextSizeTheSystemDensityIsUsedAsIs() {
        var outside = 0f
        var inside = 0f
        composeRule.setContent {
            outside = with(LocalDensity.current) { 30.sp.toPx() }
            SubshellTheme(textScale = 1f) {
                inside = with(LocalDensity.current) { 30.sp.toPx() }
            }
        }
        composeRule.waitForIdle()
        assertEquals(outside, inside, 0.01f)
    }
}
