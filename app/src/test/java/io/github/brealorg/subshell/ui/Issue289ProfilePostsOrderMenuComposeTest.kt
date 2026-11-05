package io.github.brealorg.subshell.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditProfilePostSort
import io.github.brealorg.subshell.data.RedditProfilePostsOrder
import io.github.brealorg.subshell.ui.components.ProfilePostsOrderMenuButton
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #289: the sort menu on a profile's posts. ISSUE289_PROFILE_POSTS_CAN_BE_SORTED_V1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue289ProfilePostsOrderMenuComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val order = mutableStateOf(RedditProfilePostsOrder.DEFAULT)

    private fun show() {
        composeRule.setContent {
            SubshellTheme {
                ProfilePostsOrderMenuButton(order = order.value, onOrderChange = { order.value = it })
            }
        }
    }

    @Test
    fun theMenuListsTheFourSortsAndTheButtonNamesTheCurrentOne() {
        show()
        composeRule.onNodeWithContentDescription("Sort posts: New").performClick()

        RedditProfilePostSort.entries.forEach { sort ->
            assertTrue(sort.label, composeRule.onAllNodesWithText(sort.label).fetchSemanticsNodes().isNotEmpty())
        }
        // New takes no range.
        assertTrue(composeRule.onAllNodesWithText("This week").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun topShowsTheRangeAndAChoiceOfItClosesTheMenu() {
        show()
        composeRule.onNodeWithContentDescription("Sort posts: New").performClick()
        composeRule.onNodeWithText("Top").performClick()

        assertEquals(RedditProfilePostSort.TOP, order.value.sort)
        composeRule.onNodeWithText("This week").performClick()
        assertEquals(RedditListingTimePeriod.WEEK, order.value.timePeriod)
    }
}
