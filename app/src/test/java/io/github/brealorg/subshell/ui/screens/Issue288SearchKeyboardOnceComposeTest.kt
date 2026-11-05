package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE288_SEARCH_OPENS_THE_KEYBOARD_ONCE_V1 (#288, reader on 0.91, Xiaomi 11i):
 * opening search puts the cursor in the field (Open keyboard on search), but
 * coming back to the results from a post does not put it there again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue288SearchKeyboardOnceComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private fun fieldFocused(): Boolean =
        composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .any { node -> node.config.getOrNull(SemanticsProperties.Focused) == true }

    @Test
    fun backFromAPostDoesNotPutTheCursorInTheSearchFieldAgain() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        val state = SubshellState(initialInteractionSideConfigured = true).apply { openSearch() }
        val searchShown = mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                // The post replaces search on screen, and Back brings search back.
                if (searchShown.value) SearchScreen(state = state, onBack = {})
            }
        }
        composeRule.waitForIdle()
        assertEquals("opening search focuses the field", true, fieldFocused())

        searchShown.value = false
        composeRule.waitForIdle()
        searchShown.value = true
        composeRule.waitForIdle()
        assertEquals("back from a post leaves the field alone", false, fieldFocused())

        // Tapping search again is a new request.
        state.openSearch()
        composeRule.waitForIdle()
        assertEquals(true, fieldFocused())
    }
}
