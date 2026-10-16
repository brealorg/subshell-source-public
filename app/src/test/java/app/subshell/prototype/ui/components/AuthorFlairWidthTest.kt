package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #32 follow-up: the author keeps its width next to a flair; a flair with no room disappears. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthorFlairWidthTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aFlairWithLessThanItsMinimumWidthTakesNoSpace() {
        composeRule.setContent {
            Box(Modifier.width(40.dp)) {
                Box(Modifier.hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth).size(100.dp).testTag("flair"))
            }
        }
        composeRule.onNodeWithTag("flair").assertIsNotDisplayed()
    }

    @Test
    fun aFlairWithRoomIsShownWithinTheSpaceLeft() {
        composeRule.setContent {
            Box(Modifier.width(60.dp)) {
                Box(Modifier.hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth).size(100.dp).testTag("flair"))
            }
        }
        assertEquals(60.dp, composeRule.onNodeWithTag("flair").getUnclippedBoundsInRoot().width)
    }

    @Test
    fun theAuthorKeepsItsWidthNextToAWeightedFlair() {
        composeRule.setContent {
            Row(Modifier.width(200.dp)) {
                Text("some-author-name", maxLines = 1, modifier = Modifier.testTag("author"))
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth)
                        .size(400.dp)
                        .testTag("flair"),
                )
            }
        }
        assertTrue(composeRule.onNodeWithTag("author").getUnclippedBoundsInRoot().width > 0.dp)
    }
}
