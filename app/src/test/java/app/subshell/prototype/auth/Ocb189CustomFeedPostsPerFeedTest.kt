package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.CustomFeedPostReadResult
import app.subshell.prototype.data.auth.CustomFeedReadResult
import app.subshell.prototype.data.auth.CustomFeedReader
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OCB189_CUSTOM_FEED_POSTS_KEPT_PER_FEED_V1: Home and the Feeds tab share the
 * visible posts slot; returning to a feed already loaded puts its posts back
 * instead of reloading it from the top.
 */
class Ocb189CustomFeedPostsPerFeedTest {
    private class CountingReader : CustomFeedReader {
        val postReads = mutableListOf<String>()

        override suspend fun load(accountStableId: String, preferredSession: StoredOAuthSession?): CustomFeedReadResult =
            error("not used")

        override suspend fun loadPosts(
            accountStableId: String,
            feedPath: String,
            after: String?,
            preferredSession: StoredOAuthSession?,
        ): CustomFeedPostReadResult {
            postReads += feedPath
            return CustomFeedPostReadResult.Loaded(
                posts = listOf(post("t3_${feedPath.trim('/').substringAfterLast('/')}")),
                nextCursor = "next",
            )
        }

        override suspend fun loadFeed(accountStableId: String, feedPath: String): CustomFeedSummary? = null
    }

    @Test
    fun returningToALoadedFeedRestoresItsPostsWithoutAReload() {
        val reader = CountingReader()
        val coordinator = CustomFeedCoordinator(
            feedReader = reader,
            feedCreator = null,
            writeCapability = null,
            writeClientMode = null,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        val home = "/user/me/m/home_feed/"
        val tab = "/user/me/m/tab_feed/"

        coordinator.loadCustomFeedPosts("acct", home, forceRefresh = false)
        coordinator.loadCustomFeedPosts("acct", tab, forceRefresh = false)
        coordinator.loadCustomFeedPosts("acct", home, forceRefresh = false)

        assertEquals(listOf(home.trim(), tab.trim()), reader.postReads)
        val state = coordinator.customFeedPostsUiState.value
        assertEquals(home.trim(), state.feedPath)
        assertEquals(listOf("t3_home_feed"), state.posts.map(Post::id))
        assertEquals("next", state.nextCursor)
    }

    private companion object {
        fun post(id: String) = Post(
            id = id,
            community = "r/test",
            author = "me",
            age = "1h",
            title = id,
            score = 1,
            comments = 0,
            kind = PostKind.TEXT,
        )
    }
}
