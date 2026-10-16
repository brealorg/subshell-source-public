package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.ContentFilterType
import app.subshell.prototype.ui.ContentFilters
import app.subshell.prototype.ui.ScopedContentFilter
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #30: the content filter editor, driven through its UI — adding a
 * filter everywhere and one only in a community, seeing both listed, and
 * removing them; the Communities editor offers no community field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ContentFiltersScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(state: SubshellState) {
        composeRule.setContent {
            SubshellTheme {
                ContentFiltersScreen(
                    filters = state.contentFilters,
                    onAdd = state::addContentFilter,
                    onRemove = state::removeContentFilter,
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aWordFilterCanBeAddedEverywhereAndOnlyInACommunityThenRemoved() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        render(state)

        composeRule.onNodeWithText("Words").performClick()
        composeRule.onNodeWithText("Add word or phrase").performTextInput("giveaway")
        composeRule.onNodeWithContentDescription("Add filter").performClick()
        composeRule.onNodeWithText("Add word or phrase").performTextInput("Spoiler")
        composeRule.onNodeWithText("Only in community (optional)").performTextInput("r/Movies")
        composeRule.onNodeWithContentDescription("Add filter").performClick()
        composeRule.waitForIdle()

        assertEquals(
            ContentFilters(
                words = setOf("giveaway"),
                scoped = setOf(ScopedContentFilter(ContentFilterType.WORD, "spoiler", "movies")),
            ),
            state.contentFilters,
        )
        composeRule.onNodeWithText("giveaway").assertExists()
        composeRule.onNodeWithText("Only in r/movies").assertExists()

        composeRule.onNodeWithContentDescription("Remove spoiler in r/movies").performClick()
        composeRule.onNodeWithContentDescription("Remove giveaway").performClick()
        composeRule.waitForIdle()

        assertEquals(ContentFilters(), state.contentFilters)
        assertEquals(0, composeRule.onAllNodesWithText("Only in r/movies").fetchSemanticsNodes().size)
    }

    @Test
    fun theCommunitiesEditorHasNoCommunityField() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        render(state)

        composeRule.onNodeWithText("Communities").performClick()
        composeRule.waitForIdle()

        assertEquals(
            0,
            composeRule.onAllNodesWithText("Only in community (optional)").fetchSemanticsNodes().size,
        )
    }
}
