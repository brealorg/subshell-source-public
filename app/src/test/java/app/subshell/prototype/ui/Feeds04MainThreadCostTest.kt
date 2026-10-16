package app.subshell.prototype.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ui review round 2 group D (FEEDS04): no Room reads or id-list copies per frame. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Feeds04MainThreadCostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingIdentityRepository : IdentityMetadataRepository {
        val readThreads = mutableListOf<Thread>()

        override fun cachedCommunityIdentity(name: String): Community? {
            readThreads += Thread.currentThread()
            return Community(
                id = "cached:$name",
                name = name,
                description = "Stored identity",
                members = "12k",
                accent = 0xFF00FF00,
            )
        }

        override suspend fun refreshCommunityIdentity(
            name: String,
            fallback: Community,
        ): RepositoryResult<Community> = RepositoryResult.Empty("not used")

        override fun cachedUserIdentity(username: String): UserFixture? = null

        override suspend fun refreshUserIdentity(
            username: String,
            fallback: UserFixture,
        ): RepositoryResult<UserFixture> = RepositoryResult.Empty("not used")
    }

    // FEEDS04_CACHED_COMMUNITY_IDENTITY_OFF_MAIN_V1
    @Test
    fun favoritesReadStoredIdentitiesOnceAndOffTheCallingThread() {
        val repository = RecordingIdentityRepository()
        val state = SubshellState(
            identityMetadataRepository = repository,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        state.toggleFavoriteCommunity("kotlin")

        state.favoriteCommunities()
        state.favoriteCommunities()
        assertEquals("composition never reads Room", 0, repository.readThreads.size)

        val caller = Thread.currentThread()
        runBlocking { state.loadCachedCommunityListIdentities() }
        runBlocking { state.loadCachedCommunityListIdentities() }

        assertEquals("each name is read once", 1, repository.readThreads.size)
        assertNotEquals(caller, repository.readThreads.single())
        assertEquals("Stored identity", state.favoriteCommunities().single().description)
    }

    // FEEDS04_DERIVED_ID_SET_READS_V1
    @Test
    fun hidingTheLastPostAfterTheFeedRenderedEmptiesTheList() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val feed = CustomFeedSummary(
            path = "/user/me/m/films/",
            displayName = "films",
            description = "",
            visibility = "private",
            subredditNames = listOf("movies"),
        )
        val posts = listOf("hide").map { id ->
            Post(
                // Local posts carry bare ids, the form hidePost stores.
                id = id,
                community = "movies",
                author = "someone",
                age = "1h",
                title = "Post $id",
                score = 1,
                comments = 0,
                kind = PostKind.TEXT,
            )
        }
        composeRule.setContent {
            SubshellTheme {
                CustomFeedBrowser(
                    modifier = Modifier,
                    state = state,
                    accountStableId = "reddit:me",
                    feed = feed,
                    postsState = CustomFeedPostsUiState(
                        accountStableId = "reddit:me",
                        feedPath = feed.path,
                        posts = posts,
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
        assertEquals(1, composeRule.onAllNodesWithText("Post hide").fetchSemanticsNodes().size)

        state.hidePost("hide")
        composeRule.waitForIdle()

        // Only a recomputed visible list reaches the empty state (PostCard
        // alone would just stop drawing the hidden row).
        assertEquals(1, composeRule.onAllNodesWithText("All posts are filtered").fetchSemanticsNodes().size)
    }
}
