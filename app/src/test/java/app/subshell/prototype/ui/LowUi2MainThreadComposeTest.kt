package app.subshell.prototype.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.createFixtureFreeLocalSubshellRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUI2_DEEP_LINK_CACHE_READ_OFF_MAIN_V1 (OCB-117): a Reddit post link read
 * the post cache (a blocking Room call) on the main thread before it opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi2MainThreadComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aPostLinkReadsTheCacheOffTheMainThreadAndOpensThePost() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        // Only the lookups that open the link count (route projection reads
        // the open post again later).
        val cacheReadsOnMain = CopyOnWriteArrayList<Boolean>()
        var opened: () -> Boolean = { false }
        val cached = Post(
            id = "x117",
            community = "test",
            author = "someone",
            age = "1h",
            title = "A cached post",
            score = 1,
            comments = 0,
            kind = PostKind.TEXT,
        )
        val repository: SubshellRepository =
            object : SubshellRepository by createFixtureFreeLocalSubshellRepository() {
                override fun postForAccount(postId: String, accountStableId: String?): RepositoryResult<Post> {
                    if (!opened()) cacheReadsOnMain += Looper.myLooper() == Looper.getMainLooper()
                    return if (postId == cached.id) RepositoryResult.Content(cached) else RepositoryResult.Empty()
                }
            }
        val account = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            repository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        opened = { state.selectedPost?.id == cached.id }
        cacheReadsOnMain.clear()

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditIntentLaunch = RedditIntentLaunch(
                    requestId = 1L,
                    rawUri = "https://www.reddit.com/r/test/comments/x117/a_cached_post/",
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { state.selectedPost?.id == cached.id }

        assertEquals(cached.id, state.selectedPost?.id)
        assertTrue(cacheReadsOnMain.isNotEmpty())
        assertFalse(cacheReadsOnMain.any { onMain -> onMain })
    }
}
