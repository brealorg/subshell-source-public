package app.subshell.prototype.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.subshell.prototype.ui.screens.settingsSearchResult
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1 (#205): a passive position
 * bar for feeds and comment threads, behind a setting that is off by default.
 * Owner's phone check: it changed shape on Home (length followed the number
 * of visible posts) and in a thread it sat at the top and flashed (the pinned
 * sort row counted as the first row; the app's own scrolls showed it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue205ScrollPositionIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theThumbFollowsGooglesScrollIndicatorState() {
        // 1 000 px track, 10 000 px of content through a 1 000 px viewport.
        val top = scrollPositionThumb(0, 10_000, 1_000, 1_000f, 24f, moreCanLoad = false)!!
        val middle = scrollPositionThumb(4_500, 10_000, 1_000, 1_000f, 24f, moreCanLoad = false)!!
        val end = scrollPositionThumb(9_000, 10_000, 1_000, 1_000f, 24f, moreCanLoad = false)!!
        assertEquals(0f, top.start, 0.01f)
        assertEquals(100f, top.length, 0.01f)
        assertEquals(450f, middle.start, 0.01f)
        assertEquals(1_000f, end.start + end.length, 0.01f)
        // Never shorter than the minimum, never longer than 90 % of the track.
        assertEquals(24f, scrollPositionThumb(0, 1_000_000, 1_000, 1_000f, 24f, false)!!.length, 0.01f)
        assertEquals(900f, scrollPositionThumb(0, 1_001, 1_000, 1_000f, 24f, false)!!.length, 0.01f)
    }

    @Test
    fun aFeedThatCanLoadMoreNeverLooksFinished() {
        val atLoadedEnd = scrollPositionThumb(9_000, 10_000, 1_000, 1_000f, 24f, moreCanLoad = true)!!
        assertTrue(atLoadedEnd.start + atLoadedEnd.length <= 1_000f * SCROLL_POSITION_MORE_CAN_LOAD_LIMIT + 0.01f)
    }

    @Test
    fun aListThatFitsHasNoThumb() {
        assertNull(scrollPositionThumb(0, 800, 1_000, 1_000f, 24f, moreCanLoad = false))
        assertNull(scrollPositionThumb(0, 0, 0, 1_000f, 24f, moreCanLoad = true))
        assertNotNull(scrollPositionThumb(0, 1_200, 1_000, 1_000f, 24f, moreCanLoad = true))
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun aPinnedHeaderDoesNotHoldThePositionAtTheTop() {
        val state = LazyListState()
        composeRule.setContent {
            LazyColumn(state = state, modifier = Modifier.testTag("thread")) {
                item("post") { Text("Post") }
                stickyHeader("comment-controls") { Text("Sort: Best") }
                items((1..200).toList()) { index -> Text("Comment $index") }
            }
        }
        composeRule.runOnIdle { kotlinx.coroutines.runBlocking { state.scrollToItem(100) } }
        composeRule.waitForIdle()

        val indicator = composeRule.runOnIdle { state.scrollIndicatorState }!!
        val progress = indicator.scrollOffset.toFloat() / (indicator.contentSize - indicator.viewportSize)
        assertTrue("progress $progress", progress > 0.4f)
    }

    private lateinit var scope: CoroutineScope

    private fun list(indicator: Boolean, state: LazyListState = LazyListState()) {
        composeRule.setContent {
            scope = rememberCoroutineScope()
            SubshellTheme {
                RefreshableLazyColumn(
                    modifier = Modifier.testTag("feed"),
                    state = state,
                    scrollPositionIndicator = indicator,
                ) {
                    items((1..200).toList()) { index -> Text("Post $index") }
                }
            }
        }
    }

    private fun indicatorCount(): Int =
        composeRule.onAllNodes(hasTestTag(SCROLL_POSITION_INDICATOR_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    @Test
    fun theBarShowsWhileTheReaderScrollsOnlyWithTheSettingOn() {
        list(indicator = true)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("feed").performTouchInput { swipeUp() }
        composeRule.mainClock.advanceTimeBy(200)
        assertTrue(indicatorCount() > 0)
        // Faded out a while after the scroll ends.
        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals(0, indicatorCount())
    }

    @Test
    fun theAppsOwnScrollsDoNotShowTheBar() {
        val state = LazyListState()
        list(indicator = true, state = state)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { scope.launch { state.animateScrollToItem(120) } }
        repeat(10) {
            composeRule.mainClock.advanceTimeBy(50)
            assertEquals(0, indicatorCount())
        }
    }

    @Test
    fun nothingIsDrawnWithTheSettingOff() {
        list(indicator = false)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("feed").performTouchInput { swipeUp() }
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(0, indicatorCount())
    }

    @Test
    fun theSettingCanBeFound() {
        assertTrue("scroll-position-indicator" in settingsSearchResult("scroll position").itemKeys)
    }
}
