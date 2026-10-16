package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthInboxReadMutationResult
import app.subshell.prototype.data.auth.AuthInboxReadMutationWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxSnapshot
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
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
 * Characterisation of the inbox reads and the optimistic read mutations on
 * AuthConnectViewModel, written before they move to a coordinator. Current
 * behaviour, not a specification. The browser authorization leg is not
 * driven here; the callback routing keeps
 * AuthConnectViewModelCallbackRoutingTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelInboxTest {
    private val account = "reddit:test"

    @Test
    fun loadPaintsTheCacheFirstThenSynchronisesAndRefusesRepeatsUnlessForced() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)

        viewModel.loadRedditInbox(account, forceRefresh = false)
        idle()
        var state = viewModel.redditInboxUiState.value
        assertTrue(state.loading)
        assertEquals(1, loader.cachedCalls)

        loader.cachedGate.complete(loaded(item("i1", unread = true), next = "cur-1"))
        idle()
        state = viewModel.redditInboxUiState.value
        assertTrue(state.loading)
        assertTrue(state.fromCache)
        assertTrue(state.loaded)
        assertEquals(listOf("i1"), state.items.map { it.id })
        assertEquals(1, loader.syncCalls)

        loader.syncGate.complete(loaded(item("i1", unread = true), item("i2", unread = false), next = "cur-2"))
        idle()
        state = viewModel.redditInboxUiState.value
        assertFalse(state.loading)
        assertFalse(state.fromCache)
        assertEquals(listOf("i1", "i2"), state.items.map { it.id })

        // Loaded from the server and not forced: refused.
        viewModel.loadRedditInbox(account, forceRefresh = false)
        idle()
        assertEquals(1, loader.syncCalls)
        loader.syncGate = CompletableDeferred()
        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()
        assertEquals(1, loader.cachedCalls)
        assertEquals(2, loader.syncCalls)
        assertEquals(listOf("i1", "i2"), viewModel.redditInboxUiState.value.items.map { it.id })
    }

    @Test
    fun appendNeedsACursorAndASettledInboxAndPassesIt() {
        val loader = GatedLoader()
        val viewModel = viewModel(loader)
        viewModel.appendRedditInbox(account)
        idle()
        assertEquals(0, loader.appendCalls)

        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("i1", unread = false), next = "cur-1"))
        idle()

        viewModel.appendRedditInbox(account)
        idle()
        assertTrue(viewModel.redditInboxUiState.value.appending)
        assertEquals("cur-1", loader.lastAfter)
        viewModel.appendRedditInbox(account)
        idle()
        assertEquals(1, loader.appendCalls)

        loader.appendGate.complete(loaded(item("i1", unread = false), item("i2", unread = false), next = null))
        idle()
        val state = viewModel.redditInboxUiState.value
        assertFalse(state.appending)
        assertEquals(listOf("i1", "i2"), state.items.map { it.id })
        assertNull(state.nextCursor)
    }

    @Test
    fun markReadIsOptimisticAndAFailureStaysIsolatedFromTheInboxUi() {
        val loader = GatedLoader()
        val writer = GatedWriter()
        val viewModel = viewModel(loader, writer)
        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("i1", unread = true), item("i2", unread = false), next = null))
        idle()

        // A read item is not marked again, and an unknown id is ignored.
        viewModel.markRedditInboxRead(account, "i2")
        viewModel.markRedditInboxRead(account, "nope")
        idle()
        assertEquals(0, writer.readCalls)

        viewModel.markRedditInboxRead(account, "i1")
        idle()
        var state = viewModel.redditInboxUiState.value
        assertTrue("i1" in state.locallySeenIds)
        assertTrue("i1" in state.readMutationItemIds)
        assertEquals(1, writer.readCalls)
        // While that mutation is pending, the same item is not sent again.
        viewModel.markRedditInboxRead(account, "i1")
        idle()
        assertEquals(1, writer.readCalls)

        writer.readGate.complete(
            AuthInboxReadMutationResult.Failed(
                kind = AuthenticatedRedditInboxFailureKind.HTTP,
                retryable = true,
            ),
        )
        idle()
        state = viewModel.redditInboxUiState.value
        // The pending id clears, but opening the conversation is authoritative
        // for local seen state, so the optimistic seen id is kept and the
        // failure never surfaces as an inbox failure.
        assertFalse("i1" in state.readMutationItemIds)
        assertTrue("i1" in state.locallySeenIds)
        assertNull(state.failureKind)
        assertTrue(state.items.first { it.id == "i1" }.unread)
    }

    @Test
    fun anAppliedMarkReadKeepsTheServerSnapshotAndClearsThePendingId() {
        val loader = GatedLoader()
        val writer = GatedWriter()
        val viewModel = viewModel(loader, writer)
        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("i1", unread = true), next = null))
        idle()

        viewModel.markRedditInboxRead(account, "i1")
        idle()
        writer.readGate.complete(
            AuthInboxReadMutationResult.Applied(
                snapshot = snapshot(listOf(item("i1", unread = false)), next = null, seen = setOf("i1")),
            ),
        )
        idle()
        val state = viewModel.redditInboxUiState.value
        assertFalse("i1" in state.readMutationItemIds)
        assertTrue("i1" in state.locallySeenIds)
        assertFalse(state.items.first { it.id == "i1" }.unread)
        assertNull(state.failureKind)
    }

    private fun viewModel(
        loader: AuthenticatedRedditInboxLoader,
        writer: AuthInboxReadMutationWriter = GatedWriter(),
    ): AuthConnectViewModel {
        // loadRedditInbox schedules the background sync worker.
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

    private fun loaded(vararg items: InboxItem, next: String?) =
        AuthenticatedRedditInboxResult.Loaded(
            snapshot = snapshot(items.toList(), next, items.filterNot { it.unread }.map { it.id }.toSet()),
            fromCache = false,
        )

    private fun snapshot(items: List<InboxItem>, next: String?, seen: Set<String>) =
        AuthenticatedRedditInboxSnapshot(items = items, nextCursor = next, locallySeenIds = seen)

    private fun item(id: String, unread: Boolean) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "title $id",
        sender = "someone",
        age = "1h",
        preview = "preview $id",
        unread = unread,
    )

    /** Loader whose calls suspend until a test completes their gate. */
    private class GatedLoader : AuthenticatedRedditInboxLoader {
        var cachedGate = CompletableDeferred<AuthenticatedRedditInboxResult>()
        var syncGate = CompletableDeferred<AuthenticatedRedditInboxResult>()
        var appendGate = CompletableDeferred<AuthenticatedRedditInboxResult>()
        var cachedCalls = 0
        var syncCalls = 0
        var appendCalls = 0
        var lastAfter: String? = null

        override suspend fun cached(accountStableId: String): AuthenticatedRedditInboxResult {
            cachedCalls++
            return cachedGate.await()
        }
        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditInboxResult {
            syncCalls++
            return syncGate.await()
        }
        override suspend fun append(accountStableId: String, after: String): AuthenticatedRedditInboxResult {
            appendCalls++; lastAfter = after
            return appendGate.await()
        }
        override suspend fun resolveTargetPost(accountStableId: String, postId: String): AuthenticatedRedditInboxTargetResult =
            AuthenticatedRedditInboxTargetResult.NotFound
    }

    private class GatedWriter : AuthInboxReadMutationWriter {
        var readGate = CompletableDeferred<AuthInboxReadMutationResult>()
        var readCalls = 0

        override suspend fun markRead(accountStableId: String, item: InboxItem): AuthInboxReadMutationResult {
            readCalls++
            return readGate.await()
        }
    }
}
