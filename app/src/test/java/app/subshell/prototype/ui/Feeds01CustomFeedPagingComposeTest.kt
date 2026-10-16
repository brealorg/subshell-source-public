package app.subshell.prototype.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.CustomFeedPostsFailureKind
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FEEDS01: a custom feed fills its viewport by itself like every other feed,
 * and a failed read while posts are shown reaches the viewport error tray.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Feeds01CustomFeedPagingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val feed = CustomFeedSummary(
        path = "/user/me/m/films/",
        displayName = "films",
        description = "",
        visibility = "private",
        subredditNames = listOf("movies"),
    )

    private fun post(id: String, title: String) = Post(
        id = "t3_$id",
        community = "movies",
        author = "someone",
        age = "1h",
        title = title,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun postsState(
        posts: List<Post>,
        nextCursor: String?,
        failureKind: CustomFeedPostsFailureKind? = null,
        failedAppend: Boolean = false,
    ) = CustomFeedPostsUiState(
        accountStableId = "reddit:me",
        feedPath = feed.path,
        posts = posts,
        nextCursor = nextCursor,
        failureKind = failureKind,
        failureRetryable = failureKind != null,
        failedAppend = failedAppend,
        contentRevision = 1L,
    )

    private class Calls {
        var appends = 0
        var refreshes = 0
    }

    private fun render(
        initial: CustomFeedPostsUiState,
        state: SubshellState = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT),
        host: ViewportErrorHostState = ViewportErrorHostState(),
        onAppend: (androidx.compose.runtime.MutableState<CustomFeedPostsUiState>) -> Unit = {},
    ): Calls {
        val calls = Calls()
        val posts = mutableStateOf(initial)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalViewportErrorHostState provides host) {
                    CustomFeedBrowser(
                        modifier = androidx.compose.ui.Modifier,
                        state = state,
                        accountStableId = "reddit:me",
                        feed = feed,
                        postsState = posts.value,
                        contentPadding = PaddingValues(),
                        onBack = {},
                        onRefresh = { calls.refreshes += 1 },
                        onLoadInitial = {},
                        onAppend = {
                            calls.appends += 1
                            onAppend(posts)
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return calls
    }

    @Test
    fun aFullyFilteredCustomFeedKeepsLoadingUntilTheListingEnds() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.addContentFilter(ContentFilterType.WORD, "spoiler")
        var page = 1
        val calls = render(
            initial = postsState(listOf(post("p1", "spoiler 1")), nextCursor = "c1"),
            state = state,
            onAppend = { posts ->
                page += 1
                val loaded = (1..page).map { index -> post("p$index", "spoiler $index") }
                posts.value = postsState(loaded, nextCursor = if (page < 5) "c$page" else null)
            },
        )
        assertEquals("two pages go out back to back", 2, calls.appends)
        composeRule.mainClock.advanceTimeBy(1_100L)
        composeRule.waitForIdle()
        assertEquals("the third waits the first backoff step", 3, calls.appends)
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        assertEquals("the fourth waits longer", 3, calls.appends)
        composeRule.mainClock.advanceTimeBy(1_100L)
        composeRule.waitForIdle()
        assertEquals(4, calls.appends)
        composeRule.mainClock.advanceTimeBy(20_000L)
        composeRule.waitForIdle()
        assertEquals("the listing's end stops it", 4, calls.appends)
    }

    @Test
    fun aShortCustomFeedPageFillsTheViewport() {
        val calls = render(
            initial = postsState(listOf(post("a", "Only one post")), nextCursor = "c1"),
        )

        assertEquals(1, calls.appends)
    }

    @Test
    fun aFailedAppendIsReportedAndRetriesFromTheSameCursor() {
        val host = ViewportErrorHostState()
        val calls = render(
            initial = postsState(
                listOf(post("a", "Only one post")),
                nextCursor = "c1",
                failureKind = CustomFeedPostsFailureKind.TRANSPORT,
                failedAppend = true,
            ),
            host = host,
        )

        assertEquals("no automatic retry of a failing request", 0, calls.appends)
        assertEquals("Couldn't load more posts", host.currentRequest?.presentation?.title)
        host.retryCurrent()
        assertEquals(1, calls.appends)
        assertEquals(0, calls.refreshes)
    }

    @Test
    fun aFailedRefreshWithPostsShownRetriesTheRefresh() {
        val host = ViewportErrorHostState()
        val calls = render(
            initial = postsState(
                listOf(post("a", "Only one post")),
                nextCursor = null,
                failureKind = CustomFeedPostsFailureKind.HTTP,
            ),
            host = host,
        )

        assertEquals("Couldn't refresh the feed", host.currentRequest?.presentation?.title)
        host.retryCurrent()
        assertEquals(1, calls.refreshes)
        assertEquals(0, calls.appends)
    }

    @Test
    fun aFailureWithNoPostsStaysInTheList() {
        val host = ViewportErrorHostState()
        render(
            initial = postsState(emptyList(), nextCursor = null, failureKind = CustomFeedPostsFailureKind.HTTP),
            host = host,
        )

        assertNull(host.currentRequest)
    }
}
