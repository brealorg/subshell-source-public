package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModQueueResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationTargetResult
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Characterisation of the moderation-queue reads on AuthConnectViewModel,
 * written before they move to a coordinator. Current behaviour, not a
 * specification: a queue load is keyed on account + community and refused
 * while one is loading or already loaded (unless forced); a new community
 * cancels the previous load; append needs a cursor and a settled queue,
 * and de-duplicates by fullname; a stale result for another context is
 * dropped; the moderator directory is its own job.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelModerationQueueTest {
    private val account = "reddit:test"

    @Test
    fun queueLoadIsKeyedOnAccountAndCommunityAndRefusedWhileLoadingOrLoaded() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)

        viewModel.loadRedditModQueue(account, "r/Alpha")
        idle()
        var state = viewModel.redditModerationUiState.value
        assertTrue(state.queueLoading)
        assertEquals("alpha", state.queueCommunityName)
        assertEquals(1, loader.queueCalls)

        // Same context while loading: refused, no second call.
        viewModel.loadRedditModQueue(account, "alpha")
        idle()
        assertEquals(1, loader.queueCalls)

        loader.queueGate.complete(loaded("alpha", "t3_a1", "t3_a2", next = "cur-1"))
        idle()
        state = viewModel.redditModerationUiState.value
        assertFalse(state.queueLoading)
        assertTrue(state.queueLoaded)
        assertEquals(listOf("t3_a1", "t3_a2"), state.queueItems.map { it.fullname })
        assertEquals("cur-1", state.queueNextCursor)
        assertEquals(1L, state.contentRevision)

        // Loaded and not forced: refused. Forced: reloads, keeping the items
        // visible while loading.
        viewModel.loadRedditModQueue(account, "alpha")
        idle()
        assertEquals(1, loader.queueCalls)
        loader.queueGate = CompletableDeferred()
        viewModel.loadRedditModQueue(account, "alpha", forceRefresh = true)
        idle()
        state = viewModel.redditModerationUiState.value
        assertEquals(2, loader.queueCalls)
        assertTrue(state.queueLoading)
        assertTrue(state.queueLoaded)
        assertEquals(listOf("t3_a1", "t3_a2"), state.queueItems.map { it.fullname })
        assertNull(loader.lastAfter)
    }

    @Test
    fun aNewCommunityCancelsThePendingLoadAndDropsItsStaleResult() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)
        viewModel.loadRedditModQueue(account, "alpha")
        idle()
        val alphaGate = loader.queueGate
        loader.queueGate = CompletableDeferred()

        viewModel.loadRedditModQueue(account, "beta")
        idle()
        var state = viewModel.redditModerationUiState.value
        assertEquals("beta", state.queueCommunityName)
        assertTrue(state.queueItems.isEmpty())
        assertEquals(2, loader.queueCalls)

        // The cancelled alpha load completing late changes nothing.
        alphaGate.complete(loaded("alpha", "t3_a1"))
        idle()
        state = viewModel.redditModerationUiState.value
        assertEquals("beta", state.queueCommunityName)
        assertTrue(state.queueItems.isEmpty())
        assertTrue(state.queueLoading)

        loader.queueGate.complete(loaded("beta", "t3_b1"))
        idle()
        assertEquals(listOf("t3_b1"), viewModel.redditModerationUiState.value.queueItems.map { it.fullname })
    }

    @Test
    fun appendNeedsACursorAndASettledQueueAndDeduplicatesByFullname() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)

        // No queue yet: append is a no-op.
        viewModel.appendRedditModQueue(account, "alpha")
        idle()
        assertEquals(0, loader.queueCalls)

        viewModel.loadRedditModQueue(account, "alpha")
        idle()
        loader.queueGate.complete(loaded("alpha", "t3_a1", "t3_a2", next = "cur-1"))
        idle()

        loader.queueGate = CompletableDeferred()
        viewModel.appendRedditModQueue(account, "alpha")
        idle()
        assertEquals(2, loader.queueCalls)
        assertEquals("cur-1", loader.lastAfter)
        assertTrue(viewModel.redditModerationUiState.value.queueLoading)

        // Loading: a second append is refused.
        viewModel.appendRedditModQueue(account, "alpha")
        idle()
        assertEquals(2, loader.queueCalls)

        loader.queueGate.complete(loaded("alpha", "t3_a2", "t3_a3", next = null))
        idle()
        val state = viewModel.redditModerationUiState.value
        assertEquals(listOf("t3_a1", "t3_a2", "t3_a3"), state.queueItems.map { it.fullname })
        assertNull(state.queueNextCursor)
        assertEquals(2L, state.contentRevision)

        // Cursor exhausted: append is a no-op again.
        viewModel.appendRedditModQueue(account, "alpha")
        idle()
        assertEquals(2, loader.queueCalls)
    }

    @Test
    fun aFailedLoadKeepsTheContextAndReportsTheKind() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)
        viewModel.loadRedditModQueue(account, "alpha")
        idle()
        loader.queueGate.complete(
            AuthenticatedRedditModQueueResult.Failed(
                communityName = "alpha",
                kind = AuthenticatedRedditModerationFailureKind.HTTP,
                retryable = true,
                httpStatusCode = 503,
            ),
        )
        idle()
        val state = viewModel.redditModerationUiState.value
        assertFalse(state.queueLoading)
        assertFalse(state.queueLoaded)
        assertEquals("alpha", state.queueCommunityName)
        assertEquals(RedditModerationUiFailureKind.HTTP, state.queueFailureKind)
        assertTrue(state.queueFailureRetryable)
        assertEquals(503, state.queueFailureHttpStatusCode)

        // An invalid community name is a PROTOCOL failure without a call.
        viewModel.loadRedditModQueue(account, "not a community!")
        idle()
        assertEquals(RedditModerationUiFailureKind.PROTOCOL, viewModel.redditModerationUiState.value.queueFailureKind)
        assertEquals(1, loader.queueCalls)
    }

    @Test
    fun moderatorDirectoryLoadsOnItsOwnJob() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)
        viewModel.loadRedditModeratorCommunities(account)
        idle()
        assertTrue(viewModel.redditModerationUiState.value.directoryLoading)
        loader.directoryGate.complete(
            AuthenticatedRedditModeratedCommunitiesResult.Loaded(setOf("alpha", "beta")),
        )
        idle()
        val state = viewModel.redditModerationUiState.value
        assertFalse(state.directoryLoading)
        assertEquals(setOf("alpha", "beta"), state.moderatedCommunities)
    }

    private fun viewModel(loader: AuthenticatedRedditModerationLoader): AuthConnectViewModel =
        AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(redditModerationLoader = loader),
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun loaded(community: String, vararg fullnames: String, next: String? = null) =
        AuthenticatedRedditModQueueResult.Loaded(
            communityName = community,
            items = fullnames.map { item(it, community) },
            nextCursor = next,
        )

    private fun item(fullname: String, community: String) = RedditModeratorQueueItem(
        fullname = fullname,
        kind = RedditModeratorQueueKind.POST,
        communityName = community,
        author = "someone",
        summary = "summary $fullname",
    )

    /** Loader whose calls suspend until a test completes their gate. */
    private class GatedLoader : AuthenticatedRedditModerationLoader {
        var queueGate = CompletableDeferred<AuthenticatedRedditModQueueResult>()
        var directoryGate = CompletableDeferred<AuthenticatedRedditModeratedCommunitiesResult>()
        var queueCalls = 0
        var lastAfter: String? = null

        override suspend fun loadModeratorCommunities(accountStableId: String) = directoryGate.await()

        override suspend fun loadModQueue(
            accountStableId: String,
            rawCommunityName: String,
            after: String?,
        ): AuthenticatedRedditModQueueResult {
            queueCalls++
            lastAfter = after
            return queueGate.await()
        }

        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditModerationTargetResult.NotFound
    }
}
