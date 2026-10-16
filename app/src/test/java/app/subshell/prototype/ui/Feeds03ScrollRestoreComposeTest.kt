package app.subshell.prototype.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FEEDS03_RESTORE_ONCE_THEN_FOLLOW_THE_READER_V1: the stored anchor is
 * restored when a feed opens, but a later append or hide never pulls the
 * reader back to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Feeds03ScrollRestoreComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scrollKey = youFeedScrollKey(0)
    private fun keys(range: IntRange) = range.map { index -> "item-$index" }

    private fun render(
        state: SubshellState,
        initialKeys: List<String>,
    ): Pair<androidx.compose.runtime.MutableState<List<String>>, () -> LazyListState> {
        val itemKeys = mutableStateOf(initialKeys)
        var listState: LazyListState? = null
        composeRule.setContent {
            val keysNow = itemKeys.value
            val list = rememberFeedScrollState(state = state, scrollKey = scrollKey, itemKeys = keysNow)
            listState = list
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("feed"), state = list) {
                items(keysNow, key = { key -> key }) { key ->
                    Text(key, modifier = Modifier.height(80.dp))
                }
            }
        }
        composeRule.waitForIdle()
        return itemKeys to { requireNotNull(listState) }
    }

    private fun newState() = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

    @Test
    fun theStoredAnchorIsRestoredWhenTheFeedOpens() {
        val state = newState()
        state.updateFeedScrollAnchor(scrollKey, itemKey = "item-20", fallbackIndex = 20, pixelOffset = 0)
        val (_, list) = render(state, keys(0 until 60))

        assertEquals(20, list().firstVisibleItemIndex)
    }

    @Test
    fun theRestoreWaitsForRowsThatArriveLater() {
        val state = newState()
        // Leaving a feed captures its anchor, which holds until restored.
        state.captureFeedScrollAnchor(scrollKey, itemKey = "item-20", fallbackIndex = 20, pixelOffset = 0)
        val (itemKeys, list) = render(state, emptyList())

        itemKeys.value = keys(0 until 60)
        composeRule.waitForIdle()

        assertEquals(20, list().firstVisibleItemIndex)
    }

    @Test
    fun anAppendAfterTheReaderScrolledDoesNotPullThemBackToACapturedAnchor() {
        val state = newState()
        val (itemKeys, list) = render(state, keys(0 until 200))
        composeRule.onNodeWithTag("feed").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        // Opening a post captures the anchor; in two-pane layouts the feed
        // stays on screen and the reader keeps scrolling it.
        state.captureFeedScrollAnchor(
            scrollKey,
            itemKey = "item-${list().firstVisibleItemIndex}",
            fallbackIndex = list().firstVisibleItemIndex,
            pixelOffset = list().firstVisibleItemScrollOffset,
        )
        val openedAt = list().firstVisibleItemIndex
        composeRule.onNodeWithTag("feed").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val readerIndex = list().firstVisibleItemIndex
        val readerOffset = list().firstVisibleItemScrollOffset
        assertTrue("the second swipe moved on", readerIndex > openedAt)

        itemKeys.value = keys(0 until 225)
        composeRule.waitForIdle()

        assertEquals(readerIndex, list().firstVisibleItemIndex)
        assertEquals(readerOffset, list().firstVisibleItemScrollOffset)
    }
}
