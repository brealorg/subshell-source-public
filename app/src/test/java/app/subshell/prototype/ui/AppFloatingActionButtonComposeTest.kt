package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AppFloatingActionButton
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Scaffold's floating action button in its own host: "Create feed"
 * requests the custom feed creator and wins over the create button, Create
 * opens the post composer, Message opens the new-message route, and nothing
 * shows while the create button is not visible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppFloatingActionButtonComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createFeedRequestsTheCustomFeedCreatorAndWinsOverCreate() {
        val requested = mutableStateOf(false)
        host(
            customFeedFabVisible = true,
            createButtonVisible = true,
            createPostButtonVisible = true,
            customFeedCreateRequested = requested,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Create", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithText("Create feed", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(requested.value)
    }

    @Test
    fun createOpensThePostComposer() {
        val state = host(createButtonVisible = true, createPostButtonVisible = true)

        composeRule.onNodeWithText("Create", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(SubshellRoute.PostComposer, state.currentNavigationRoute)
    }

    @Test
    fun messageOpensTheNewMessageRoute() {
        val state = host(createButtonVisible = true, createPostButtonVisible = false)

        composeRule.onNodeWithText("Message", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        val route = state.currentNavigationRoute
        assertTrue(route is SubshellRoute.Secondary)
        assertEquals(
            SecondaryDestination.NEW_MESSAGE,
            (route as SubshellRoute.Secondary).secondaryDestination,
        )
    }

    @Test
    fun nothingShowsWhileTheCreateButtonIsHidden() {
        val visible = mutableStateOf(false)
        host(createButtonVisible = visible.value, createButtonVisibleState = visible)
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Create", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Message", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )

        visible.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun host(
        customFeedFabVisible: Boolean = false,
        createButtonVisible: Boolean = false,
        createPostButtonVisible: Boolean = true,
        customFeedCreateRequested: MutableState<Boolean> = mutableStateOf(false),
        createButtonVisibleState: MutableState<Boolean> = mutableStateOf(createButtonVisible),
    ): SubshellState {
        val state = SubshellState()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                AppFloatingActionButton(
                    state = state,
                    customFeedFabVisible = customFeedFabVisible,
                    createButtonVisible = createButtonVisibleState.value,
                    createPostButtonVisible = createPostButtonVisible,
                    fabTravelPx = 315f,
                    bottomBarHiddenFractionState = mutableFloatStateOf(0f),
                    customFeedCreateRequestedState = customFeedCreateRequested,
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }
}
