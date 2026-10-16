package app.subshell.prototype.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HIDDENIDS01_HIDDEN_POSTS_MATCH_BY_IDENTITY_V1: live posts carry the
 * fullname (t3_…) while hidden ids are stored normalized; feed filters
 * compare identities, so a hidden live post leaves the list, not just the
 * screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HiddenIds01IdentityMatchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun livePost(id: String) = Post(
        id = "t3_$id",
        community = "movies",
        author = "someone",
        age = "1h",
        title = "Post $id",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    @Test
    fun liveHomeSelectionDropsAPostHiddenByItsNormalizedId() {
        val posts = listOf(livePost("keep"), livePost("hide"))

        val selected = selectLiveHomePosts(posts = posts, hiddenPostIds = listOf("hide"))

        assertEquals(listOf("t3_keep"), selected.map(Post::id))
    }

    @Test
    fun hidingTheOnlyLivePostOfACustomFeedShowsTheFilteredEmptyState() {
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        val feed = CustomFeedSummary(
            path = "/user/alice/m/films/",
            displayName = "films",
            description = "",
            visibility = "private",
            subredditNames = listOf("movies"),
        )
        val post = livePost("only")
        composeRule.setContent {
            SubshellTheme {
                CustomFeedBrowser(
                    modifier = Modifier,
                    state = state,
                    accountStableId = alice.stableId,
                    feed = feed,
                    postsState = CustomFeedPostsUiState(
                        accountStableId = alice.stableId,
                        feedPath = feed.path,
                        posts = listOf(post),
                        contentRevision = 1L,
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

        state.togglePostHidden(post)
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("All posts are filtered").fetchSemanticsNodes().size)
    }
}
