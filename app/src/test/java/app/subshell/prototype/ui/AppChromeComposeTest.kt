package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.rememberChromeHiddenFractions
import app.subshell.prototype.ui.app.rememberChromeScrollConnection
import app.subshell.prototype.ui.screens.InboxContextualSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Directional chrome in its own file: the scroll connection hides the
 * chrome after 56 dp down and shows it after 40 dp up or on a top
 * overscroll; the hidden fractions follow the chrome, except that the top
 * bar stays while inbox activity is selected and a bar set to "always
 * show" never hides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    // 2 px per dp: hide after 112 px down, show after 80 px up.
    private val density = Density(density = 2f)

    @Test
    fun theScrollConnectionHidesAfter56DpDownAndShowsAfter40DpUp() {
        val state = SubshellState()
        val connection = scrollConnection(state)

        connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput)
        assertTrue(state.chromeVisible)
        connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.UserInput)
        assertFalse(state.chromeVisible)

        connection.onPreScroll(Offset(0f, 70f), NestedScrollSource.UserInput)
        assertFalse(state.chromeVisible)
        connection.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)
        assertTrue(state.chromeVisible)
    }

    @Test
    fun anOverscrollAtTheTopShowsTheChrome() {
        val state = SubshellState()
        val connection = scrollConnection(state)
        state.onMainContentScroll(deltaPx = 500f, atTop = false, hideThresholdPx = 1f, showThresholdPx = 1f)
        assertFalse(state.chromeVisible)

        connection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput)

        assertTrue(state.chromeVisible)
    }

    @Test
    fun bothBarsHideWithTheChromeButTheTopBarStaysWhileActivityIsSelected() {
        val state = SubshellState(initialReduceMotion = true)
        val selection = mutableStateOf<InboxContextualSelection?>(null)
        val (top, bottom) = fractions(state, selection)
        assertEquals(0f, top.value)
        assertEquals(0f, bottom.value)

        state.onMainContentScroll(deltaPx = 500f, atTop = false, hideThresholdPx = 1f, showThresholdPx = 1f)
        composeRule.waitForIdle()
        assertEquals(1f, top.value)
        assertEquals(1f, bottom.value)

        selection.value = InboxContextualSelection(
            selectedCount = 1,
            canMarkRead = true,
            allVisibleSelected = false,
            onClose = {},
            onMarkRead = {},
            onDismiss = {},
            onSelectAll = {},
        )
        composeRule.waitForIdle()
        assertEquals(0f, top.value)
        assertEquals(1f, bottom.value)
    }

    @Test
    fun aBarSetToAlwaysShowNeverHides() {
        val state = SubshellState(
            initialTopBarBehavior = ChromeBehavior.ALWAYS_SHOW,
            initialReduceMotion = true,
        )
        val (top, _) = fractions(state, mutableStateOf(null))

        state.onMainContentScroll(deltaPx = 500f, atTop = false, hideThresholdPx = 1f, showThresholdPx = 1f)
        composeRule.waitForIdle()

        assertEquals(0f, top.value)
    }

    // MODMAILCHROME01_A_COVERING_SURFACE_LEAVES_THE_CHROME_ALONE_V1
    @Test
    fun aSurfaceCoveringTheBarsLeavesThemAsTheyWere() {
        val state = SubshellState()
        val shown = mutableStateOf(false)
        val connection = scrollConnection(state, shown)

        connection.onPreScroll(Offset(0f, -500f), NestedScrollSource.UserInput)
        assertTrue("an open modmail thread does not hide the bars", state.chromeVisible)

        state.onMainContentScroll(deltaPx = 500f, atTop = false, hideThresholdPx = 1f, showThresholdPx = 1f)
        connection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput)
        assertFalse("nor shows them", state.chromeVisible)

        shown.value = true
        composeRule.waitForIdle()
        connection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput)
        assertTrue("back on the inbox the list moves them again", state.chromeVisible)
    }

    private fun scrollConnection(
        state: SubshellState,
        chromeShown: androidx.compose.runtime.State<Boolean> = mutableStateOf(true),
    ): NestedScrollConnection {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        var connection: NestedScrollConnection? = null
        composeRule.setContent {
            connection = rememberChromeScrollConnection(
                state = state,
                density = density,
                chromeShown = chromeShown.value,
            )
        }
        composeRule.waitForIdle()
        return checkNotNull(connection)
    }

    private fun fractions(
        state: SubshellState,
        selection: MutableState<InboxContextualSelection?>,
    ): Pair<State<Float>, State<Float>> {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        var result: Pair<State<Float>, State<Float>>? = null
        composeRule.setContent {
            result = rememberChromeHiddenFractions(
                state = state,
                activeInboxContextualSelection = selection.value,
            )
        }
        composeRule.waitForIdle()
        return checkNotNull(result)
    }
}
