package app.subshell.prototype.data.diagnostics

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.InboxCoordinator
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxSnapshot
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.inbox.InboxNotificationCenter
import app.subshell.prototype.inbox.readNotificationDiagnostics
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE272_INBOX_STATUS_IN_THE_REPORT_V1 / ISSUE272_NOTIFICATION_STATE_IN_THE_REPORT_V1
 * (#272): the report says what the last inbox load found (counts only) and
 * what decides whether a notification can reach the reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue272NotificationDiagnosticsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        ensureWorkManagerInitialized(application)
        InboxDiagnosticStatusStore(application).clear()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun item(id: String, kind: ActivityKind, unread: Boolean) = InboxItem(
        id = id,
        kind = kind,
        title = "secret title $id",
        sender = "secret_sender",
        age = "1h",
        preview = "secret preview",
        unread = unread,
    )

    @Test
    fun theStatusLinesRoundTripAndReachTheReportAsCounts() {
        val store = InboxDiagnosticStatusStore(Files.createTempDirectory("inbox-status").toFile())
        store.record(
            INBOX_STATUS_BACKGROUND,
            inboxCheckSummary(
                atEpochMs = 1_000L,
                outcome = "checked",
                items = listOf(
                    item("a", ActivityKind.REPLY, unread = true),
                    item("b", ActivityKind.MESSAGE, unread = false),
                    item("c", ActivityKind.MENTION, unread = false),
                ),
                fresh = 3,
                newEvents = 1,
                posted = 1,
            ),
        )
        store.record(INBOX_STATUS_APP, InboxCheckSummary(2_000L, "authorization_required"))

        val read = store.read()
        assertEquals(2, read[INBOX_STATUS_BACKGROUND]?.activity)
        assertEquals(1, read[INBOX_STATUS_BACKGROUND]?.messages)
        assertEquals("authorization_required", read[INBOX_STATUS_APP]?.outcome)

        val report = buildString { appendNotificationDiagnostics(null, read) }
        assertTrue(report, report.contains("last_background_inbox_check=1970-01-01T00:00:01.000Z outcome=checked activity=2 messages=1 unread=1 fresh=3 new=1 posted=1"))
        assertTrue(report, report.contains("last_app_inbox_load=1970-01-01T00:00:02.000Z outcome=authorization_required"))
        assertTrue("no content", !report.contains("secret"))
    }

    @Test
    fun anAppLoadRecordsWhatRedditSent() {
        val items = listOf(
            item("r1", ActivityKind.REPLY, unread = true),
            item("m1", ActivityKind.MESSAGE, unread = true),
            item("m2", ActivityKind.MOD, unread = false),
        )
        val coordinator = InboxCoordinator(
            application = application,
            redditInboxLoader = FakeLoader(AuthenticatedRedditInboxResult.Loaded(snapshot(items), fromCache = false)),
            inboxReadMutationWriter = null,
            inboxReadCapability = null,
            inboxReadClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            accountSessionDirectory = null,
            scope = scope,
        )

        coordinator.loadRedditInbox("reddit:a", forceRefresh = true)

        val summary = waitForAppLoad()
        assertEquals("loaded", summary.outcome)
        assertEquals(1, summary.activity)
        assertEquals(2, summary.messages)
        assertEquals(2, summary.unread)
        assertEquals(false, summary.fromCache)
    }

    @Test
    fun anEmptyInboxIsRecordedAsLoadedWithNothing() {
        val coordinator = InboxCoordinator(
            application = application,
            redditInboxLoader = FakeLoader(AuthenticatedRedditInboxResult.Loaded(snapshot(emptyList()), fromCache = false)),
            inboxReadMutationWriter = null,
            inboxReadCapability = null,
            inboxReadClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            accountSessionDirectory = null,
            scope = scope,
        )

        coordinator.loadRedditInbox("reddit:a", forceRefresh = true)

        val summary = waitForAppLoad()
        assertEquals("loaded", summary.outcome)
        assertEquals(0, summary.activity)
        assertEquals(0, summary.messages)
    }

    @Test
    fun theNotificationStateNamesPermissionChannelsAndTypes() {
        InboxNotificationCenter.ensureChannel(application)
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        var state = readNotificationDiagnostics(application)
        assertEquals("denied", state.postPermission)
        assertEquals("default", state.channels["subshell_inbox"])
        assertEquals("missing", state.channels["subshell_offline"])
        assertEquals(true, state.types["replies"])

        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        state = readNotificationDiagnostics(application)
        assertEquals("granted", state.postPermission)

        val report = buildString { appendNotificationDiagnostics(state, emptyMap()) }
        assertTrue(report, report.contains("[notifications]"))
        assertTrue(report, report.contains("post_notifications_permission=granted"))
        assertTrue(report, report.contains("channel_subshell_inbox=default"))
        assertTrue(report, report.contains("notify_replies=on"))
        assertTrue(report, report.contains("last_app_inbox_load=none"))
    }

    private fun waitForAppLoad(): InboxCheckSummary {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            InboxDiagnosticStatusStore(application).read()[INBOX_STATUS_APP]?.let { return it }
            Thread.sleep(20)
        }
        error("no app inbox load recorded")
    }

    private fun snapshot(items: List<InboxItem>) = AuthenticatedRedditInboxSnapshot(
        items = items,
        nextCursor = null,
        locallySeenIds = emptySet(),
    )

    private class FakeLoader(private val result: AuthenticatedRedditInboxResult) : AuthenticatedRedditInboxLoader {
        override suspend fun cached(accountStableId: String) = AuthenticatedRedditInboxResult.NotCached
        override suspend fun synchronize(accountStableId: String) = result
        override suspend fun append(accountStableId: String, after: String) = result
        override suspend fun resolveTargetPost(accountStableId: String, postId: String): AuthenticatedRedditInboxTargetResult =
            error("not used")
    }
}
