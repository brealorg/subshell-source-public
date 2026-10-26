package app.subshell.prototype.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.subshell.prototype.BuildConfig
import java.util.concurrent.TimeUnit

// UPDATE07_RETURN_CHECK_AFTER_AN_HOUR_V1 (owner): a reader who comes back to
// the app hears of a new release within the hour; the 12-hour background job
// stays as it is.
internal const val UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS =
    60L * 60L * 1000L

internal object UpdateCheckCoordinator {
    /**
     * Returns false only when the manifest could not be fetched: that is no
     * check (the stale window stays open) and the worker retries it.
     */
    suspend fun check(
        context: Context,
        force: Boolean,
        nowEpochMillis: Long = System.currentTimeMillis(),
        client: UpdateManifestSource = UpdateManifestClient(),
        enabled: Boolean =
            UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG),
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): Boolean {
        val appContext = context.applicationContext
        if (!enabled) return true

        val store = UpdateStateStore(appContext)
        if (
            !force &&
            !store.shouldCheck(
                nowEpochMillis,
                UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS,
            )
        ) {
            return true
        }

        val fetch = client.fetch()
        val usable = (fetch as? UpdateManifestFetch.Completed)?.release
        if (usable != null) {
            store.recordUsableManifest()
        } else {
            store.recordUnusableManifest(nowEpochMillis)
        }
        if (fetch !is UpdateManifestFetch.Completed) return false
        store.recordCheck(nowEpochMillis)
        val release = fetch.release ?: return true

        // UPDATE03: a stale copy never replaces a newer release we know; a
        // later correction does, and reopens the markers below it.
        val known = store.knownRelease()
        if (!updateManifestSupersedes(release, known, nowEpochMillis)) return true
        // UPDATE06_ONLY_A_CORRECTION_REOPENS_MARKERS_V1 (#63): a repeat check of
        // the same (or a newer) release keeps its notified/presented markers.
        if (updateManifestCorrects(release, known)) {
            store.lowerMarkersBelow(release.versionCode)
        }

        if (!release.isNewerThan(currentVersionCode)) {
            store.saveAvailableRelease(null)
            // LOW_UPDATE_WITHDRAWN_RELEASE_LEAVES_NO_OFFER_V1 (OCB-656): an
            // offer or notification for the withdrawn release goes too.
            UpdatePresentationCoordinator.withdrawAvailable()
            runCatching { UpdateNotificationCenter.cancel(appContext) }
            return true
        }

        store.saveAvailableRelease(release)
        UpdatePresentationCoordinator.offerAvailable(release)

        if (
            shouldNotifyUpdate(
                release = release,
                currentVersionCode = currentVersionCode,
                lastNotifiedVersionCode = store.lastNotifiedVersionCode(),
            ) &&
            UpdateNotificationCenter.postAvailable(appContext, release)
        ) {
            store.markNotified(release.versionCode)
        }
        return true
    }
}

internal object UpdateCheckScheduler {
    internal const val PERIODIC_WORK_NAME = "subshell-update01-beta-periodic-v1"
    private const val FOREGROUND_WORK_NAME = "subshell-update01-beta-foreground-v1"

    private val connectedConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // STEP3_UPDATE_RUNTIME_FENCE_IS_A_PARAMETER_V1: the release fence is the
    // default; a test passes true to see the work it schedules.
    fun schedule(
        context: Context,
        runtimeEnabled: Boolean = UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG),
    ) {
        val appContext = context.applicationContext
        if (!runtimeEnabled) return

        val request = PeriodicWorkRequest.Builder(
            UpdateCheckWorker::class.java,
            12,
            TimeUnit.HOURS,
        )
            .setConstraints(connectedConstraints)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueForegroundCheckIfStale(
        context: Context,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val appContext = context.applicationContext
        if (!UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) return

        val store = UpdateStateStore(appContext)
        if (
            !store.shouldCheck(
                nowEpochMillis,
                UPDATE01_FOREGROUND_STALE_INTERVAL_MILLIS,
            )
        ) {
            return
        }

        val request = OneTimeWorkRequest.Builder(UpdateCheckWorker::class.java)
            .setConstraints(connectedConstraints)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            FOREGROUND_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

internal class UpdateCheckWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val checked = UpdateCheckCoordinator.check(
            context = applicationContext,
            force = true,
        )
        return if (checked) Result.success() else Result.retry()
    }
}
