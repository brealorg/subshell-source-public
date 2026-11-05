package io.github.brealorg.subshell.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.RepositoryLoadOperation
import io.github.brealorg.subshell.data.account.RedditFeedUiState
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE290_FEEDS_RESUME_WHERE_YOU_LEFT_OFF_V1 and
 * ISSUE290_LOAD_NEW_FINDS_THE_PLACE_WITHIN_FOUR_PAGES_V1: after a cold start a
 * feed opens at the stored place, or (Load new posts) stays at the top while
 * new pages are read and goes to the place once it shows up, at most four pages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue290ResumeFeedPositionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = 1_800_000_000_000L
    private val home = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)
    private val dir: File = Files.createTempDirectory("issue290-compose").toFile()
    private val file = File(dir, FEED_POSITION_FILE_NAME)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Lets the search's wait for steady rows pass on the test clock. */
    private fun settle() {
        repeat(4) {
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
        }
    }

    private fun keys(prefix: String, range: IntRange) = range.map { index -> "$prefix-$index" }

    private fun coldStartState(start: FeedPositionStart): SubshellState {
        val before = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialResumeFeedPosition = true,
            feedPositionFile = FeedPositionFile { file },
            clock = { now },
        )
        before.updateFeedScrollAnchor(home, itemKey = "old-40", fallbackIndex = 40, pixelOffset = 0)
        before.saveFeedPositions()
        return SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialResumeFeedPosition = true,
            initialResumeFeedPositionStart = start,
            feedPositionFile = FeedPositionFile { file },
            clock = { now + 60_000L },
        )
    }

    private class Feed(initialKeys: List<String>) {
        val itemKeys = mutableStateOf(initialKeys)
        val feedState = mutableStateOf(RedditFeedUiState(inProgressOperation = RepositoryLoadOperation.REFRESH))
        var appends = 0
        var nextPages: List<List<String>> = emptyList()
    }

    private fun render(state: SubshellState, feed: Feed, withSearch: Boolean): () -> LazyListState {
        var listState: LazyListState? = null
        composeRule.setContent {
            val keysNow = feed.itemKeys.value
            val list = rememberFeedScrollState(state = state, scrollKey = home, itemKeys = keysNow)
            listState = list
            if (withSearch) {
                FindTheResumedFeedPlace(
                    state = state,
                    scrollKey = home,
                    feedState = feed.feedState.value,
                    itemKeys = keysNow,
                    canAppend = true,
                    onAppend = {
                        feed.appends += 1
                        val page = feed.nextPages.getOrNull(feed.appends - 1).orEmpty()
                        feed.itemKeys.value = feed.itemKeys.value + page
                        feed.feedState.value = RedditFeedUiState(lastAppliedOperation = RepositoryLoadOperation.APPEND)
                    },
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), state = list) {
                items(keysNow, key = { key -> key }) { key ->
                    Text(key, modifier = Modifier.height(80.dp))
                }
            }
        }
        composeRule.waitForIdle()
        return { requireNotNull(listState) }
    }

    @Test
    fun showWhereILeftOffOpensTheStoredListAtThePlace() {
        val state = coldStartState(FeedPositionStart.SHOW_STORED)
        val list = render(state, Feed(keys("old", 0 until 80)), withSearch = false)

        assertEquals(40, list().firstVisibleItemIndex)
    }

    @Test
    fun loadNewStaysAtTheTopThenGoesToThePlaceFoundOnTheThirdPage() {
        val state = coldStartState(FeedPositionStart.LOAD_NEW)
        // The stored list still holds the place, but it is about to be replaced.
        val feed = Feed(keys("old", 0 until 80))
        val list = render(state, feed, withSearch = true)
        assertEquals(0, list().firstVisibleItemIndex)

        feed.nextPages = listOf(keys("new", 25 until 50), keys("new", 50 until 60) + "old-40" + keys("new", 61 until 75))
        feed.itemKeys.value = keys("new", 0 until 25)
        feed.feedState.value = RedditFeedUiState(lastAppliedOperation = RepositoryLoadOperation.REFRESH)
        settle()

        assertEquals(2, feed.appends)
        assertEquals(60, list().firstVisibleItemIndex)
        assertNull(state.feedPositionAwaitingFind(home))
    }

    @Test
    fun theSearchWaitsForTheNewListInsteadOfFindingThePlaceInTheOldOne() {
        val state = coldStartState(FeedPositionStart.LOAD_NEW)
        val feed = Feed(keys("old", 0 until 80))
        val list = render(state, feed, withSearch = true)
        feed.nextPages = (1..5).map { page -> keys("page$page", 0 until 25) }

        // Reddit's answer is applied while the feed still shows the old list.
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            feed.feedState.value = RedditFeedUiState(lastAppliedOperation = RepositoryLoadOperation.REFRESH)
        }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(12) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.runOnUiThread { feed.itemKeys.value = keys("new", 0 until 25) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true
        settle()

        assertEquals(RESUMED_PLACE_SEARCH_PAGES - 1, feed.appends)
        assertEquals(0, list().firstVisibleItemIndex)
    }

    @Test
    fun loadNewGivesUpAfterFourPagesAndStartsAtTheTop() {
        val state = coldStartState(FeedPositionStart.LOAD_NEW)
        val feed = Feed(keys("old", 0 until 80))
        val list = render(state, feed, withSearch = true)

        feed.nextPages = (1..5).map { page -> keys("page$page", 0 until 25) }
        feed.itemKeys.value = keys("new", 0 until 25)
        feed.feedState.value = RedditFeedUiState(lastAppliedOperation = RepositoryLoadOperation.REFRESH)
        settle()

        assertEquals(RESUMED_PLACE_SEARCH_PAGES - 1, feed.appends)
        assertEquals(0, list().firstVisibleItemIndex)
        // The top is now the reader's place (the first new row), not the old one.
        assertEquals(0, state.feedScrollAnchor(home).fallbackIndex)
        assertNull(state.feedPositionAwaitingFind(home))
    }

    @Test
    fun aFailedRefreshKeepsTheStoredListAndGoesToThePlace() {
        val state = coldStartState(FeedPositionStart.LOAD_NEW)
        val feed = Feed(keys("old", 0 until 80))
        val list = render(state, feed, withSearch = true)

        feed.feedState.value = RedditFeedUiState(failedOperation = RepositoryLoadOperation.REFRESH)
        settle()

        assertEquals(0, feed.appends)
        assertEquals(40, list().firstVisibleItemIndex)
    }
}
