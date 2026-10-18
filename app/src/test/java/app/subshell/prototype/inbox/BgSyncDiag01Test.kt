package app.subshell.prototype.inbox

import android.app.Application
import android.database.sqlite.SQLiteDiskIOException
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import java.net.UnknownHostException
import java.security.KeyStoreException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BGSYNCDIAG01 (owner 2026-10-18, from a diagnostic report with ~25
 * "local=unknown" inbox/modmail pairs): background sync exceptions are
 * recorded by kind, a transient network failure is retried with backoff, and
 * the work waits for a validated network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BgSyncDiag01Test {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun anExceptionIsRecordedByWhatItIs() {
        assertEquals(
            NetworkTransportFailureKind.DNS,
            backgroundSyncFailureDetails(UnknownHostException("oauth.reddit.com")).transportKind,
        )
        assertEquals(
            LocalFailureKind.STORAGE,
            backgroundSyncFailureDetails(ExecutionException(SQLiteDiskIOException("disk"))).localKind,
        )
        assertEquals(LocalFailureKind.SECURITY, backgroundSyncFailureDetails(KeyStoreException("locked")).localKind)
        assertEquals(
            FailureDetails(retryable = false, localKind = LocalFailureKind.DECODING),
            backgroundSyncFailureDetails(SerializationException("shape")),
        )
        assertEquals(LocalFailureKind.UNKNOWN, backgroundSyncFailureDetails(IllegalStateException()).localKind)
    }

    @Test
    fun onlyATransientNetworkFailureIsRetriedAndOnlyAFewTimes() {
        assertTrue(backgroundSyncFailureDetails(UnknownHostException()).isTransientNetworkFailure())
        assertFalse(backgroundSyncFailureDetails(SQLiteDiskIOException()).isTransientNetworkFailure())
        assertTrue(backgroundSyncRetries(transientNetworkFailure = true, runAttemptCount = 0))
        assertFalse(backgroundSyncRetries(transientNetworkFailure = true, runAttemptCount = INBOX_BACKGROUND_TRANSIENT_RETRIES))
        assertFalse(backgroundSyncRetries(transientNetworkFailure = false, runAttemptCount = 0))
    }

    @Test
    fun theSyncWaitsForAValidatedNetwork() {
        ensureWorkManagerInitialized(context)
        InboxBackgroundSyncScheduler.schedule(context, "reddit:a")

        val work: WorkInfo = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(INBOX_BACKGROUND_SYNC_UNIQUE_WORK)
            .get(5, TimeUnit.SECONDS)
            .single { !it.state.isFinished }
        val request = requireNotNull(work.constraints.requiredNetworkRequest)
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    }
}
