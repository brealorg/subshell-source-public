package app.subshell.prototype.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #32: the one-line label also renders outside a Material component,
 * where the ambient text style has no size (auto-size needs a ceiling).
 * Robolectric does not wrap text like a device does, so the one-line
 * behaviour itself is verified on the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OneLineAutoSizeLabelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersWithoutAnAmbientFontSize() {
        composeRule.setContent {
            Box(Modifier.width(48.dp)) { OneLineAutoSizeLabel("Favorites") }
        }
        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
    }

    private fun fits(slot: androidx.compose.ui.unit.Dp): Boolean {
        var result = false
        composeRule.setContent {
            result = rememberLabelsFitSlots(
                labels = listOf("Favorites", "Joined", "Recent", "Feeds"),
                slotWidth = slot,
                style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            )
        }
        composeRule.waitForIdle()
        return result
    }

    // ISSUE220_TABS_SCROLL_INSTEAD_OF_SHRINKING_V1: tabs keep their size and
    // scroll when a label does not fit its slot.
    @Test
    fun aWideSlotFits() {
        assertTrue(fits(400.dp))
    }

    @Test
    fun aSlotNarrowerThanTheLongestLabelDoesNotFit() {
        assertFalse(fits(4.dp))
    }

    @Test
    fun anUnmeasuredRowCountsAsFitting() {
        assertTrue(fits(0.dp))
    }
}
