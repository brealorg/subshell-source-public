package app.subshell.prototype.auth

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.subshell.prototype.data.auth.AuthInboxReadMutationResult
import app.subshell.prototype.data.auth.AuthInboxReadMutationWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxSnapshot
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.inbox.INBOX_BACKGROUND_SYNC_UNIQUE_WORK
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * INBOX01: an item opened while the inbox loads is marked read once the load
 * finishes (it used to be dropped); "Mark all read" includes items seen here
 * whose own read did not reach Reddit.
 * INBOX02: the background sync is scheduled only while inbox notifications
 * are on, and cancelled when they are off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Inbox01ReadsAndSyncTest {
    private val account = "reddit:test"
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun anItemOpenedDuringARefreshIsMarkedReadWhenTheRefreshEnds() {
        val loader = GatedLoader()
        val writer = GatedWriter()
        val viewModel = viewModel(loader, writer)
        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("i1", unread = true)))
        idle()
        loader.syncGate = CompletableDeferred()
        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()

        viewModel.markRedditInboxRead(account, "i1")
        idle()
        assertEquals(0, writer.readCalls)
        assertTrue("i1" in viewModel.redditInboxUiState.value.locallySeenIds)

        loader.syncGate.complete(loaded(item("i1", unread = true)))
        idle()
        assertEquals(1, writer.readCalls)
    }

    @Test
    fun aDeferredReadIsNotSentWhenTheRefreshShowsTheItemRead() {
        val loader = GatedLoader()
        val writer = GatedWriter()
        val viewModel = viewModel(loader, writer)
        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()
        loader.syncGate.complete(loaded(item("i1", unread = true)))
        idle()
        loader.syncGate = CompletableDeferred()
        viewModel.loadRedditInbox(account, forceRefresh = true)
        idle()
        viewModel.markRedditInboxRead(account, "i1")
        idle()

        loader.syncGate.complete(loaded(item("i1", unread = false)))
        idle()
        assertEquals(0, writer.readCalls)
    }

    @Test
    fun theBackgroundSyncRunsOnlyWhileInboxNotificationsAreOn() {
        setNotifications(false)
        viewModel(GatedLoader(), GatedWriter()).loadRedditInbox(account, forceRefresh = false)
        idle()
        assertEquals(0, activeSyncWork())

        setNotifications(true)
        viewModel(GatedLoader(), GatedWriter()).loadRedditInbox(account, forceRefresh = false)
        idle()
        assertEquals(1, activeSyncWork())

        setNotifications(false)
        viewModel(GatedLoader(), GatedWriter()).loadRedditInbox(account, forceRefresh = false)
        idle()
        assertEquals(0, activeSyncWork())
    }

    private fun setNotifications(enabled: Boolean) {
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE).edit()
            .putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, enabled)
            .commit()
    }

    private fun activeSyncWork(): Int =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(INBOX_BACKGROUND_SYNC_UNIQUE_WORK)
            .get(5, TimeUnit.SECONDS)
            .count { info -> !info.state.isFinished && info.state != WorkInfo.State.CANCELLED }

    private fun viewModel(
        loader: AuthenticatedRedditInboxLoader,
        writer: AuthInboxReadMutationWriter,
    ): AuthConnectViewModel {
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

    private fun loaded(vararg items: InboxItem) = AuthenticatedRedditInboxResult.Loaded(
        snapshot = AuthenticatedRedditInboxSnapshot(
            items = items.toList(),
            nextCursor = null,
            locallySeenIds = items.filterNot { it.unread }.map { it.id }.toSet(),
        ),
        fromCache = false,
    )

    private fun item(id: String, unread: Boolean) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "title $id",
        sender = "someone",
        age = "1h",
        preview = "preview $id",
        unread = unread,
    )

    private class GatedLoader : AuthenticatedRedditInboxLoader {
        var syncGate = CompletableDeferred<AuthenticatedRedditInboxResult>()

        override suspend fun cached(accountStableId: String): AuthenticatedRedditInboxResult =
            AuthenticatedRedditInboxResult.NotCached
        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditInboxResult = syncGate.await()
        override suspend fun append(accountStableId: String, after: String): AuthenticatedRedditInboxResult =
            CompletableDeferred<AuthenticatedRedditInboxResult>().await()
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
