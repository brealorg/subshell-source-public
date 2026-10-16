package app.subshell.prototype.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSystemBarContrastPolicyTest {
    @Test
    fun lightThemeRequiresDarkSystemBarIcons() {
        assertTrue(systemBarIconsAreDark(darkTheme = false))
    }

    @Test
    fun darkThemeRequiresLightSystemBarIcons() {
        assertFalse(systemBarIconsAreDark(darkTheme = true))
    }
}
