package app.subshell.prototype.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class A11y01A1LargeTextAndTouchTargetPolicyTest {
    @Test
    fun navigationBarExpandsForLargeText() {
        assertEquals(64.dp, navigationBarBaseHeightForFontScale(1.0f))
        assertEquals(64.dp, navigationBarBaseHeightForFontScale(1.29f))
        assertEquals(88.dp, navigationBarBaseHeightForFontScale(1.30f))
        assertEquals(88.dp, navigationBarBaseHeightForFontScale(2.0f))
    }
}
