package app.subshell.prototype.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #30 / CONTENTFILTER02: content filters apply on every feed surface,
 * and a custom feed honours hidden posts and the NSFW setting too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ContentFilter02EveryFeedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun post(id: String, title: String, community: String = "movies", nsfw: Boolean = false) = Post(
        id = "t3_$id",
        community = community,
        author = "someone",
        age = "1h",
        title = title,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        nsfw = nsfw,
    )

    @Test
    fun aCommunityFilterDoesNotEmptyThatCommunitysOwnPage() {
        val filters = ContentFilters(communities = setOf("movies", "tv"), words = setOf("spoiler"))

        assertTrue(filters.hides(post("a", "Great film")))
        assertFalse(filters.hidesOnCommunityPage(post("a", "Great film"), "Movies"))
        assertTrue(filters.hidesOnCommunityPage(post("b", "Big spoiler ahead"), "movies"))
        // A filter on another community still applies on this page.
        assertTrue(filters.hidesOnCommunityPage(post("c", "Other place", community = "tv"), "movies"))
    }

    @Test
    fun aCustomFeedHidesFilteredHiddenAndNsfwPosts() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.addContentFilter(ContentFilterType.WORD, "spoiler")
        state.updateShowNsfwPosts(false)
        val posts = listOf(
            post("keep", "Keep this one"),
            post("word", "A spoiler inside"),
            post("nsfw", "Adult thing", nsfw = true),
        )
        render(state, posts)

        assertEquals(1, composeRule.onAllNodesWithText("Keep this one").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("A spoiler inside").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Adult thing").fetchSemanticsNodes().size)
    }

    @Test
    fun aCustomFeedWhoseLoadedPostsAreAllFilteredSaysSo() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.addContentFilter(ContentFilterType.WORD, "spoiler")
        render(state, listOf(post("word", "A spoiler inside")))

        assertEquals(1, composeRule.onAllNodesWithText("All posts are filtered").fetchSemanticsNodes().size)
    }

    private fun render(state: SubshellState, posts: List<Post>) {
        val feed = CustomFeedSummary(
            path = "/user/me/m/films/",
            displayName = "films",
            description = "",
            visibility = "private",
            subredditNames = listOf("movies"),
        )
        composeRule.setContent {
            SubshellTheme {
                CustomFeedBrowser(
                    modifier = androidx.compose.ui.Modifier,
                    state = state,
                    accountStableId = "reddit:me",
                    feed = feed,
                    postsState = CustomFeedPostsUiState(
                        accountStableId = "reddit:me",
                        feedPath = feed.path,
                        posts = posts,
                    ),
                    contentPadding = PaddingValues(),
                    onBack = {},
                    onRefresh = {},
                    onLoadInitial = {},
                    onAppend = {},
                )
            }
        }
        composeRule.waitForIdle()
    }
}
