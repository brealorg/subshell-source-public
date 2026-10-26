package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthInboxReadMutationResult
import app.subshell.prototype.data.auth.AuthInboxReadMutationWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxSnapshot
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * INBOXSSOT01_THE_INBOX_FOLLOWS_ITS_CACHE_V1: the inbox the screen shows
 * follows the stored inbox, so what the background sync stores (the item a
 * notification is about) is shown without another load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InboxSsot01FollowsTheStoreTest {
    private val account = "reddit:test"

    @Test
    fun whatTheBackgroundSyncStoresIsShownWithoutAnotherLoad() {
        val loader = StoreLoader()
        val viewModel = viewModel(loader)
        viewModel.inbox().loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("old")))
        idle()
        assertEquals(1, loader.syncCalls)

        // The 15-minute worker stored a new message.
        loader.store.tryEmit(snapshot(item("new"), item("old")))
        idle()

        val state = viewModel.inbox().redditInboxUiState.value
        assertEquals(listOf("new", "old"), state.items.map(InboxItem::id))
        // The unread badge counts it too.
        assertEquals(2, state.unreadCount)
        assertFalse(state.loading)
        assertEquals(1, loader.syncCalls)
    }

    @Test
    fun aReadMarkNotStoredYetStaysShownWhenTheStoreChanges() {
        val loader = StoreLoader()
        val writer = GatedWriter()
        val viewModel = viewModel(loader, writer)
        viewModel.inbox().loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("a")))
        idle()
        viewModel.inbox().markRedditInboxRead(account, "a")
        idle()
        assertTrue("a" in viewModel.inbox().redditInboxUiState.value.readMutationItemIds)

        loader.store.tryEmit(snapshot(item("b"), item("a")))
        idle()

        val state = viewModel.inbox().redditInboxUiState.value
        assertEquals(listOf("b", "a"), state.items.map(InboxItem::id))
        assertTrue("a" in state.locallySeenIds)
    }

    @Test
    fun anotherAccountsStoreIsNotFollowedAfterASwitch() {
        val loader = StoreLoader()
        val viewModel = viewModel(loader)
        viewModel.inbox().loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("mine")))
        idle()

        loader.syncGate = CompletableDeferred()
        viewModel.inbox().loadRedditInbox("reddit:other", forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("theirs")))
        idle()
        loader.stores.getValue(account).tryEmit(snapshot(item("late"), item("mine")))
        idle()

        val state = viewModel.inbox().redditInboxUiState.value
        assertEquals("reddit:other", state.accountStableId)
        assertEquals(listOf("theirs"), state.items.map(InboxItem::id))
        assertEquals(listOf(account, "reddit:other"), loader.observed)
    }

    private fun viewModel(
        loader: AuthenticatedRedditInboxLoader,
        writer: AuthInboxReadMutationWriter = GatedWriter(),
    ): AuthConnectViewModel {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        return AuthConnectViewModel(
            context,
            unavailableAuthApplicationRuntime().copy(
                redditInboxLoader = loader,
                inboxReadMutationWriter = writer,
            ),
        )
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun snapshot(vararg items: InboxItem) =
        AuthenticatedRedditInboxSnapshot(items = items.toList(), nextCursor = null, locallySeenIds = emptySet())

    private fun loaded(vararg items: InboxItem) =
        AuthenticatedRedditInboxResult.Loaded(snapshot = snapshot(*items), fromCache = false)

    private fun item(id: String) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "title $id",
        sender = "someone",
        age = "1h",
        preview = "preview $id",
        unread = true,
    )

    /** A stored inbox per account that a test writes to, as the worker would. */
    private class StoreLoader : AuthenticatedRedditInboxLoader {
        val stores = mutableMapOf<String, MutableSharedFlow<AuthenticatedRedditInboxSnapshot>>()
        val observed = mutableListOf<String>()
        val store get() = stores.getValue("reddit:test")
        var syncGate = CompletableDeferred<AuthenticatedRedditInboxResult>()
        var syncCalls = 0

        override fun observe(accountStableId: String): Flow<AuthenticatedRedditInboxSnapshot> {
            observed += accountStableId
            return stores.getOrPut(accountStableId) { MutableSharedFlow(extraBufferCapacity = 8) }
        }
        override suspend fun cached(accountStableId: String) = AuthenticatedRedditInboxResult.NotCached
        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditInboxResult {
            syncCalls++
            return syncGate.await()
        }
        override suspend fun append(accountStableId: String, after: String) =
            AuthenticatedRedditInboxResult.NotCached
        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditInboxTargetResult.NotFound
    }

    private class GatedWriter : AuthInboxReadMutationWriter {
        val readGate = CompletableDeferred<AuthInboxReadMutationResult>()

        override suspend fun markRead(accountStableId: String, item: InboxItem): AuthInboxReadMutationResult =
            readGate.await()
    }
}
