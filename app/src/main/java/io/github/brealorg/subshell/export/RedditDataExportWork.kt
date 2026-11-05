package io.github.brealorg.subshell.export

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.auth.AuthApplicationRuntimeFactory
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.auth.reddit.RedditDataExportAccess
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What Settings › Accounts shows under "Export your Reddit data". */
internal sealed interface RedditDataExportStatus {
    data object Waiting : RedditDataExportStatus
    data class Running(val done: Int, val total: Int) : RedditDataExportStatus
    data class Saved(val location: String, val items: Int, val incompleteLists: Int) : RedditDataExportStatus
    data class Failed(val message: String) : RedditDataExportStatus
}

internal object RedditDataExportScheduler {
    fun enqueue(context: Context, accountStableId: String) {
        val request = OneTimeWorkRequest.Builder(RedditDataExportWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putString(KEY_ACCOUNT, accountStableId).build())
            .build()
        // A second tap while an export runs leaves that one running.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(REDDIT_EXPORT_UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** The newest export of [accountStableId]; nothing while WorkManager is not there (tests). */
    fun status(context: Context, accountStableId: String): Flow<RedditDataExportStatus?> {
        val workManager = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (_: IllegalStateException) {
            return flowOf(null)
        }
        return workManager.getWorkInfosForUniqueWorkFlow(REDDIT_EXPORT_UNIQUE_WORK).map { infos ->
            infos.lastOrNull()?.toExportStatus(accountStableId)
        }
    }
}

internal fun WorkInfo.toExportStatus(accountStableId: String): RedditDataExportStatus? {
    val data = if (state.isFinished) outputData else progress
    val account = data.getString(KEY_ACCOUNT)
    if (account != null && account != accountStableId) return null
    return when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> RedditDataExportStatus.Waiting
        WorkInfo.State.RUNNING -> RedditDataExportStatus.Running(
            done = data.getInt(KEY_DONE, 0),
            total = data.getInt(KEY_TOTAL, RedditExportList.entries.size),
        )
        WorkInfo.State.SUCCEEDED -> {
            if (account == null) return null
            val location = data.getString(KEY_LOCATION)
            if (location != null) {
                RedditDataExportStatus.Saved(
                    location = location,
                    items = data.getInt(KEY_ITEMS, 0),
                    incompleteLists = data.getInt(KEY_INCOMPLETE, 0),
                )
            } else {
                RedditDataExportStatus.Failed(data.getString(KEY_MESSAGE) ?: EXPORT_NOT_SAVED)
            }
        }
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> null
    }
}

internal class RedditDataExportWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(KEY_ACCOUNT)?.trim()?.takeIf(String::isNotEmpty)
            ?: return Result.success()
        val notifications = RedditDataExportNotifications(applicationContext)
        fun failed(message: String): Result {
            notifications.failed(message)
            return Result.success(Data.Builder().putString(KEY_ACCOUNT, account).putString(KEY_MESSAGE, message).build())
        }
        val runtime = runCatching { AuthApplicationRuntimeFactory.create(applicationContext) }.getOrNull()
        val reader = runtime?.redditDataExportReader ?: return failed(EXPORT_NOT_AVAILABLE)
        val username = try {
            runtime.accountSessionDirectory?.hydratedSnapshot()?.accounts?.firstOrNull { candidate ->
                candidate.stableId == account && candidate.source == AccountSessionSource.AUTHENTICATED
            }?.username
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return failed(EXPORT_SIGN_IN)
        val access = when (val opened = reader.open(account)) {
            is RedditDataExportAccess.Granted -> opened
            RedditDataExportAccess.SignInRequired -> return failed(EXPORT_SIGN_IN)
            RedditDataExportAccess.Unavailable -> return failed(EXPORT_NO_TOKEN)
        }
        val today = LocalDate.now(ZoneOffset.UTC)
        val displayName = "subshell-reddit-export-u_$username-$today.zip"
        val staged = File(File(applicationContext.cacheDir, "export").apply { mkdirs() }, "export.zip")
        try {
            val run = staged.outputStream().buffered().use { output ->
                runRedditDataExport(
                    username = username,
                    grantedScopes = access.grantedScopes,
                    fetch = access.fetch,
                    pause = { delay(REDDIT_EXPORT_REQUEST_SPACING_MS) },
                    output = output,
                    exportedAtEpochSeconds = System.currentTimeMillis() / 1000L,
                    appVersion = BuildConfig.VERSION_NAME,
                    progress = { done, total ->
                        notifications.progress(done, total)
                        setProgressAsync(
                            Data.Builder()
                                .putString(KEY_ACCOUNT, account)
                                .putInt(KEY_DONE, done)
                                .putInt(KEY_TOTAL, total)
                                .build(),
                        )
                    },
                )
            }
            if (run.items == 0 && run.incomplete.isNotEmpty()) return failed(EXPORT_NOTHING_READ)
            val location = saveToDownloads(applicationContext, staged, displayName)
                ?: return failed(EXPORT_NOT_SAVED)
            notifications.saved(location, run)
            return Result.success(
                Data.Builder()
                    .putString(KEY_ACCOUNT, account)
                    .putString(KEY_LOCATION, location)
                    .putInt(KEY_ITEMS, run.items)
                    .putInt(KEY_INCOMPLETE, run.incomplete.size)
                    .build(),
            )
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { staged.delete() }
        }
    }
}

/**
 * The ZIP goes to Downloads/subshell like a saved video; before Android 10 a
 * worker cannot ask for the storage grant, so it goes to Subshell's own
 * Downloads folder and the location says so.
 */
private suspend fun saveToDownloads(context: Context, staged: File, displayName: String): String? =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/subshell")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val destination = try {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@withContext null
            try {
                val written = resolver.openOutputStream(destination, "w")?.use { output ->
                    staged.inputStream().use { input -> input.copyTo(output) }
                    true
                } == true
                if (!written) {
                    resolver.delete(destination, null, null)
                    return@withContext null
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(destination, values, null, null)
                "Downloads/subshell/$displayName"
            } catch (cancelled: CancellationException) {
                resolver.delete(destination, null, null)
                throw cancelled
            } catch (_: Exception) {
                resolver.delete(destination, null, null)
                null
            }
        } else {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@withContext null
            val target = File(directory, displayName)
            try {
                staged.copyTo(target, overwrite = true)
                target.absolutePath
            } catch (cancelled: CancellationException) {
                target.delete()
                throw cancelled
            } catch (_: Exception) {
                target.delete()
                null
            }
        }
    }

/** Progress while the lists are read, then where the file is; silent without permission. */
internal class RedditDataExportNotifications(context: Context) {
    private val appContext = context.applicationContext

    fun progress(done: Int, total: Int) {
        post(
            NotificationCompat.Builder(appContext, REDDIT_EXPORT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle("Exporting your Reddit data")
                .setContentText("$done of $total lists")
                .setProgress(total, done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true),
        )
    }

    fun saved(location: String, run: RedditExportRun) {
        val text = exportSavedText(location, run.items, run.incomplete.size)
        post(
            NotificationCompat.Builder(appContext, REDDIT_EXPORT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle("Your Reddit data is exported")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        appContext,
                        0,
                        Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .setAutoCancel(true),
        )
    }

    fun failed(message: String) {
        post(
            NotificationCompat.Builder(appContext, REDDIT_EXPORT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle("Your Reddit data wasn't exported")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true),
        )
    }

    @SuppressLint("MissingPermission") // canPost checks it.
    private fun post(builder: NotificationCompat.Builder) {
        if (!canPost()) return
        ensureChannel()
        NotificationManagerCompat.from(appContext).notify(REDDIT_EXPORT_NOTIFICATION_ID, builder.build())
    }

    private fun canPost(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(REDDIT_EXPORT_CHANNEL_ID, "Data export", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while your Reddit data is exported"
            },
        )
    }
}

internal fun exportSavedText(location: String, items: Int, incompleteLists: Int): String = buildString {
    append("$items items saved to $location.")
    if (incompleteLists > 0) {
        append(" $incompleteLists ${if (incompleteLists == 1) "list is" else "lists are"} incomplete; summary.json says why.")
    }
}

internal const val EXPORT_SIGN_IN = "Sign in to Reddit with this account again, then export."
internal const val EXPORT_NO_TOKEN = "Couldn't reach Reddit to start the export. Try again later."
internal const val EXPORT_NOTHING_READ = "Reddit didn't answer for any list. Try again later."
internal const val EXPORT_NOT_SAVED = "Couldn't save the file to Downloads."
internal const val EXPORT_NOT_AVAILABLE = "Export isn't available with this sign-in."
internal const val REDDIT_EXPORT_CHANNEL_ID = "subshell_data_export"
internal const val REDDIT_EXPORT_UNIQUE_WORK = "subshell-reddit-data-export"
private const val REDDIT_EXPORT_NOTIFICATION_ID = 26_502

// About 85 requests a minute, under Reddit's 100.
private const val REDDIT_EXPORT_REQUEST_SPACING_MS = 700L
private const val KEY_ACCOUNT = "account"
private const val KEY_DONE = "done"
private const val KEY_TOTAL = "total"
private const val KEY_LOCATION = "location"
private const val KEY_ITEMS = "items"
private const val KEY_INCOMPLETE = "incomplete"
private const val KEY_MESSAGE = "message"
