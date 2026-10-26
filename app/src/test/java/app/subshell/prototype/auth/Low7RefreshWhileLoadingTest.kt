package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.auth.AuthInboxReadMutationResult
import app.subshell.prototype.data.auth.AuthInboxReadMutationWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxSnapshot
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsResult
import app.subshell.prototype.data.auth.authenticatedRedditFeedQueryKey
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * LOW7: a refresh asked for while the same list was busy was dropped without a
 * trace: pull to refresh during a feed append (OCB-440; Saved/history share the
 * rule, OCB-441), a forced subscriptions refresh during an ordinary load
 * (OCB-442), a forced inbox reload during a mark-read (OCB-421).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low7RefreshWhileLoadingTest {
    private val account = "reddit:test"

    @Test
    fun aFeedRefreshDuringAnAppendReplacesTheAppend() = runBlocking<Unit> {
        val calls = mutableListOf<RepositoryLoadOperation>()
        val gates = mutableListOf<CompletableDeferred<AuthenticatedRedditFeedUpdateResult>>()
        val target = AuthenticatedRedditFeedTarget.main(feed = RedditMainFeed.ALL, sort = RedditFeedSort.NEW)
        val key = authenticatedRedditFeedQueryKey(account, target)
        val coordinator = RedditFeedRequestCoordinator(
            scope = this,
            updater = AuthenticatedRedditFeedUpdater { _, operation, _ ->
                calls += operation
                CompletableDeferred<AuthenticatedRedditFeedUpdateResult>().also(gates::add).await()
            },
        )

        coordinator.update(account, RepositoryLoadOperation.APPEND, target)
        yield()
        coordinator.update(account, RepositoryLoadOperation.REFRESH, target)
        yield()
        coordinator.update(account, RepositoryLoadOperation.REFRESH, target)
        yield()

        assertEquals(listOf(RepositoryLoadOperation.APPEND, RepositoryLoadOperation.REFRESH), calls)
        gates.last().complete(AuthenticatedRedditFeedUpdateResult.Applied(key, RepositoryLoadOperation.REFRESH, 1, 1, false))
        yield()
        assertEquals(RepositoryLoadOperation.REFRESH, coordinator.uiState.value.lastAppliedOperation)
    }

    @Test
    fun aForcedSubscriptionsRefreshReplacesAnOrdinaryLoad() {
        val loader = SubscriptionsLoader()
        val viewModel = AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(redditSubscriptionsLoader = loader),
        )

        viewModel.communities().loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        viewModel.communities().loadRedditSubscriptions(account, forceRefresh = true)
        idle()
        viewModel.communities().loadRedditSubscriptions(account, forceRefresh = true)
        idle()

        assertEquals(2, loader.synchronizeCalls)
    }

    @Test
    fun aForcedInboxReloadDuringAMarkReadRunsWhenTheReadEnds() {
        val loader = InboxLoader()
        val writer = InboxWriter()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val viewModel = AuthConnectViewModel(
            context,
            unavailableAuthApplicationRuntime().copy(redditInboxLoader = loader, inboxReadMutationWriter = writer),
        )
        viewModel.inbox().loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGates.single().complete(loaded(item("i1", unread = true)))
        idle()
        assertEquals(1, loader.syncCalls)

        viewModel.inbox().markRedditInboxRead(account, "i1")
        idle()
        viewModel.inbox().loadRedditInbox(account, forceRefresh = true)
        idle()
        assertEquals("waits for the read", 1, loader.syncCalls)

        writer.readGate.complete(
            AuthInboxReadMutationResult.Applied(snapshot(listOf(item("i1", unread = false)))),
        )
        idle()
        assertEquals(2, loader.syncCalls)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun loaded(vararg items: InboxItem) =
        AuthenticatedRedditInboxResult.Loaded(snapshot = snapshot(items.toList()), fromCache = false)

    private fun snapshot(items: List<InboxItem>) =
        AuthenticatedRedditInboxSnapshot(items = items, nextCursor = null, locallySeenIds = emptySet())

    private fun item(id: String, unread: Boolean) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "title $id",
        sender = "someone",
        age = "1h",
        preview = "preview $id",
        unread = unread,
    )

    private class SubscriptionsLoader : AuthenticatedRedditSubscriptionsLoader {
        var synchronizeCalls = 0
        override suspend fun cached(accountStableId: String) = AuthenticatedRedditSubscriptionsResult.NotCached
        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditSubscriptionsResult {
            synchronizeCalls++
            return CompletableDeferred<AuthenticatedRedditSubscriptionsResult>().await()
        }
    }

    private class InboxLoader : AuthenticatedRedditInboxLoader {
        val syncGates = mutableListOf<CompletableDeferred<AuthenticatedRedditInboxResult>>()
        val syncCalls: Int get() = syncGates.size
        override suspend fun cached(accountStableId: String): AuthenticatedRedditInboxResult =
            AuthenticatedRedditInboxResult.NotCached
        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditInboxResult {
            return CompletableDeferred<AuthenticatedRedditInboxResult>().also(syncGates::add).await()
        }
        override suspend fun append(accountStableId: String, after: String): AuthenticatedRedditInboxResult =
            CompletableDeferred<AuthenticatedRedditInboxResult>().await()
        override suspend fun resolveTargetPost(accountStableId: String, postId: String): AuthenticatedRedditInboxTargetResult =
            AuthenticatedRedditInboxTargetResult.NotFound
    }

    private class InboxWriter : AuthInboxReadMutationWriter {
        val readGate = CompletableDeferred<AuthInboxReadMutationResult>()
        override suspend fun markRead(accountStableId: String, item: InboxItem): AuthInboxReadMutationResult =
            readGate.await()
    }
}
