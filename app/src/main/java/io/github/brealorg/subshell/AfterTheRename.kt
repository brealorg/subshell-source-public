package io.github.brealorg.subshell

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.brealorg.subshell.communityposts.CommunityPostCheckWorker
import io.github.brealorg.subshell.export.RedditDataExportWorker
import io.github.brealorg.subshell.inbox.InboxBackgroundSyncWorker
import io.github.brealorg.subshell.inbox.InboxReplyWorker
import io.github.brealorg.subshell.offline.OfflineSaveWorker
import io.github.brealorg.subshell.share.ShareIntoSubshellComponent
import io.github.brealorg.subshell.update.UpdateCheckWorker
import java.io.File

/** The package versions up to 0.91 used for every class (#42). */
internal const val RETIRED_CLASS_PREFIX = "app.subshell.prototype."
private const val CURRENT_CLASS_PREFIX = "io.github.brealorg.subshell."

/**
 * ISSUE55_WORK_FROM_BEFORE_THE_RENAME_RUNS_V1: WorkManager stores each job by its worker's class
 * name. A periodic Inbox or new-posts check, a queued Direct Reply, export or offline save stored
 * by 0.91 names a class that no longer exists, so it failed until the app re-enqueued it, and a
 * queued reply, export or offline save was lost. The old names map to this version's workers;
 * the job runs with the input it was stored with. Any other name goes to WorkManager's default.
 */
internal object RenamedWorkerFactory : WorkerFactory() {
    internal val workersByRetiredName: Map<String, Class<out ListenableWorker>> = listOf(
        InboxBackgroundSyncWorker::class.java,
        InboxReplyWorker::class.java,
        CommunityPostCheckWorker::class.java,
        RedditDataExportWorker::class.java,
        OfflineSaveWorker::class.java,
        UpdateCheckWorker::class.java,
    ).associateBy { worker -> RETIRED_CLASS_PREFIX + worker.name.removePrefix(CURRENT_CLASS_PREFIX) }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val worker = workersByRetiredName[workerClassName] ?: return null
        return worker
            .getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            .newInstance(appContext, workerParameters)
    }
}

/**
 * The one-time step after an update from a version before the package rename (#42), run by
 * UpdatePackageReplacedReceiver and at every process start (SubshellApplication). Each part is
 * independent; a no-backup marker keeps it to once.
 */
internal object AfterTheRename {
    @Volatile
    internal var backgroundRun: Thread? = null
        private set

    fun runOnceInTheBackground(context: Context) {
        val appContext = context.applicationContext
        backgroundRun = Thread({ runOnce(appContext) }, "subshell-after-the-rename").apply {
            isDaemon = true
            start()
        }
    }

    private const val MARKER_DIRECTORY = "migrations"
    internal const val MARKER_FILE = "after-the-rename-v1"
    internal const val RETIRED_SHARE_ALIAS = RETIRED_CLASS_PREFIX + "ShareIntoSubshell"

    @Synchronized
    fun runOnce(context: Context) {
        val appContext = context.applicationContext
        val marker = File(File(appContext.noBackupFilesDir, MARKER_DIRECTORY), MARKER_FILE)
        if (runCatching { marker.isFile }.getOrDefault(false)) return
        runCatching { carryShareIntoSubshell(appContext) }
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText("done\n")
        }
    }

    /**
     * ISSUE52_SHARE_INTO_SUBSHELL_SURVIVES_THE_RENAME_V1: the setting is the share alias's component
     * state. Android forgets the state of a component the new version no longer has (seen on the
     * Pixel 6), so the manifest keeps the retired alias, disabled and without intent filters, only
     * to hold it. A reader who had it on keeps it on; the retired alias goes back to its default.
     */
    internal fun carryShareIntoSubshell(context: Context): Boolean {
        val retired = ComponentName(context.packageName, RETIRED_SHARE_ALIAS)
        val wasOn = try {
            context.packageManager.getComponentEnabledSetting(retired) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (_: IllegalArgumentException) {
            false
        }
        if (wasOn && !ShareIntoSubshellComponent.isEnabled(context)) {
            ShareIntoSubshellComponent.setEnabled(context, true)
        }
        if (wasOn) {
            context.packageManager.setComponentEnabledSetting(
                retired,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
        return wasOn
    }
}
