package app.subshell.prototype.harness

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The renderer-flake rule must repair the state that produces
 * `lightZ … Infinity`: a default display that kept its size but lost its
 * density. Reproduced here deliberately with the shadow setter; the rule
 * has to restore density 1.0 (160 dpi) without touching the 1280x800 size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class RetryOnRobolectricRendererFlakeTest {
    @Test
    fun aDensitylessDefaultDisplayIsRebuiltBeforeTheTestWithItsSizeKept() {
        val display = defaultDisplay()
        shadowOf(display).setDensityDpi(0)
        assertEquals(0f, realMetrics(display).density, 0f)

        var densityDuringTest = Float.NaN
        var sizeDuringTest = ""
        val statement = RetryOnRobolectricRendererFlake().apply(
            object : Statement() {
                override fun evaluate() {
                    val metrics = realMetrics(defaultDisplay())
                    densityDuringTest = metrics.density
                    sizeDuringTest = "${metrics.widthPixels}x${metrics.heightPixels}"
                }
            },
            Description.createTestDescription(javaClass, "guarded"),
        )
        statement.evaluate()

        assertEquals(1f, densityDuringTest, 0f)
        assertEquals("1280x800", sizeDuringTest)
    }

    @Test
    fun aHealthyDefaultDisplayIsLeftAlone() {
        val display = defaultDisplay()
        val before = realMetrics(display)
        var seen = ""
        RetryOnRobolectricRendererFlake().apply(
            object : Statement() {
                override fun evaluate() {
                    val metrics = realMetrics(defaultDisplay())
                    seen = "${metrics.density}@${metrics.widthPixels}x${metrics.heightPixels}"
                }
            },
            Description.createTestDescription(javaClass, "healthy"),
        ).evaluate()
        assertEquals("${before.density}@${before.widthPixels}x${before.heightPixels}", seen)
        assertEquals(1f, before.density, 0f)
    }

    private fun defaultDisplay(): Display =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)

    // Deprecated on API 31+, but it is the call ThreadedRenderer makes.
    @Suppress("DEPRECATION")
    private fun realMetrics(display: Display): DisplayMetrics = DisplayMetrics().also { display.getRealMetrics(it) }
}
