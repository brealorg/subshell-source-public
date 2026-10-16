package app.subshell.prototype.ui.theme

import android.app.Application
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (theme01): before Compose draws anything, the window theme already
 * gives dark bar icons on a light system and light ones on a dark system, so
 * a cold start does not flash unreadable status and navigation icons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3StartupSystemBarsTest {
    private fun lightBars(): Pair<Boolean, Boolean> {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val themed = ContextThemeWrapper(context, R.style.Theme_Subshell)
        val values = themed.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowLightStatusBar, android.R.attr.windowLightNavigationBar),
        )
        try {
            return values.getBoolean(0, false) to values.getBoolean(1, false)
        } finally {
            values.recycle()
        }
    }

    @Test
    fun aLightSystemStartsWithDarkBarIcons() {
        val (status, navigation) = lightBars()
        assertTrue(status)
        assertTrue(navigation)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun aDarkSystemStartsWithLightBarIcons() {
        val (status, navigation) = lightBars()
        assertFalse(status)
        assertFalse(navigation)
    }
}
