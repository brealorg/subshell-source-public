package app.subshell.prototype.widget

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE267_POSTS_WIDGET_PICKS_ITS_PLACE_V1 (#267): the Posts widget's picker
 * offers Home and the joined communities, finds one by name and marks the
 * place shown now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue267PostsWidgetPickerComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeAndTheJoinedCommunitiesCanBePickedAndFound() {
        val picked = mutableListOf<PostsWidgetSource>()
        composeRule.setContent {
            SubshellTheme {
                PostsWidgetSourcePicker(
                    current = PostsWidgetSource("motogp"),
                    communities = listOf("motogp", "norge", "subshell"),
                    onPick = { source -> picked += source },
                )
            }
        }

        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithContentDescription("Shown now").assertExists()
        composeRule.onNodeWithText("r/norge").performClick()

        composeRule.onNodeWithText("Find a community").performTextInput("sub")
        composeRule.onNodeWithText("Home").assertDoesNotExist()
        composeRule.onNodeWithText("r/norge").assertDoesNotExist()
        composeRule.onNodeWithText("r/subshell").performClick()

        assertEquals(listOf(PostsWidgetSource("norge"), PostsWidgetSource("subshell")), picked)
    }

    @Test
    fun noJoinedCommunitiesStillOffersHome() {
        val picked = mutableListOf<PostsWidgetSource>()
        composeRule.setContent {
            SubshellTheme {
                PostsWidgetSourcePicker(PostsWidgetSource.Home, communities = emptyList(), onPick = { picked += it })
            }
        }

        composeRule.onNodeWithText("No joined communities stored yet").assertExists()
        composeRule.onNodeWithText("Home").performClick()

        assertEquals(listOf(PostsWidgetSource.Home), picked)
    }
}
