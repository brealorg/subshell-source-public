package io.github.brealorg.subshell.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditSearchOrder
import io.github.brealorg.subshell.data.RedditSearchSort
import io.github.brealorg.subshell.ui.components.SearchOrderMenuButton
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #289: the sort menu beside the search results. ISSUE289_SEARCH_RESULTS_CAN_BE_SORTED_V1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue289SearchOrderMenuComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val order = mutableStateOf(RedditSearchOrder.DEFAULT)

    private fun show() {
        composeRule.setContent {
            SubshellTheme {
                SearchOrderMenuButton(order = order.value, onOrderChange = { order.value = it })
            }
        }
    }

    @Test
    fun theMenuListsTheFiveSortsAndTheButtonNamesTheCurrentOne() {
        show()
        composeRule.onNodeWithContentDescription("Sort posts: Relevance").performClick()

        RedditSearchSort.entries.forEach { sort ->
            assertTrue(sort.label, composeRule.onAllNodesWithText(sort.label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun aSortWithATimeRangeShowsTheRangeAndKeepsTheMenuOpen() {
        show()
        composeRule.onNodeWithContentDescription("Sort posts: Relevance").performClick()
        composeRule.onNodeWithText("Top").performClick()

        assertEquals(RedditSearchSort.TOP, order.value.sort)
        assertTrue(composeRule.onAllNodesWithText("This week").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithText("This week").performClick()
        assertEquals(RedditListingTimePeriod.WEEK, order.value.timePeriod)
    }

    @Test
    fun hotHasNoTimeRangeAndClosesTheMenu() {
        show()
        composeRule.onNodeWithContentDescription("Sort posts: Relevance").performClick()
        composeRule.onNodeWithText("Hot").performClick()

        assertEquals(RedditSearchSort.HOT, order.value.sort)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("This week").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Most comments").fetchSemanticsNodes().isEmpty())
    }
}
