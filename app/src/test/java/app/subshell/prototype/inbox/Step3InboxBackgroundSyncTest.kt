package app.subshell.prototype.inbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (inbox01b21): the background inbox sync is one periodic work every
 * 15 minutes on a connected network, kept (not replaced) when scheduled again,
 * and its seen-event history stays within its limit, newest first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3InboxBackgroundSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun activeWork(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(INBOX_BACKGROUND_SYNC_UNIQUE_WORK)
            .get(5, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }

    @Test
    fun theSyncIsOnePeriodicWorkOnAConnectedNetworkKeptWhenScheduledAgain() {
        ensureWorkManagerInitialized(context)

        InboxBackgroundSyncScheduler.schedule(context, "reddit:a")
        val first = activeWork().single()
        InboxBackgroundSyncScheduler.schedule(context, "reddit:a")
        val second = activeWork().single()

        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertEquals(TimeUnit.MINUTES.toMillis(15), first.periodicityInfo?.repeatIntervalMillis)
        assertEquals(first.id, second.id)
    }

    @Test
    fun theSeenEventHistoryKeepsTheNewestWithinItsLimit() {
        val previous = (1..600).map { "old-$it" }

        val decision = reconcileInboxBackgroundEventHistory(
            initialized = true,
            previousEventKeys = previous,
            currentEventKeys = listOf("new-1", "new-2"),
            maxHistory = INBOX_BACKGROUND_EVENT_HISTORY_LIMIT,
        )

        assertEquals(512, INBOX_BACKGROUND_EVENT_HISTORY_LIMIT)
        assertEquals(INBOX_BACKGROUND_EVENT_HISTORY_LIMIT, decision.retainedEventKeys.size)
        assertEquals(listOf("new-1", "new-2", "old-1"), decision.retainedEventKeys.take(3))
        assertTrue("old-600" !in decision.retainedEventKeys)
    }
}
