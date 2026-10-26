package app.subshell.prototype.ui.app

import android.content.Context
import app.subshell.prototype.backup.processSettingsBackupRepository
import app.subshell.prototype.inbox.NotificationLaunchKey
import app.subshell.prototype.shortcuts.LAUNCHER_SHORTCUTS_PREFERENCES
import app.subshell.prototype.ui.RedditExternalPollHandoffStore
import app.subshell.prototype.ui.RedditLinkHandlingPromptStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

internal const val SUBSHELL_PREFERENCES = "subshell_preferences"

/**
 * STRICTMODE02_PREFERENCES_LOAD_BEFORE_THE_FIRST_FRAME_V1 (#243): the files the
 * first screen reads (settings, the device file, the poll handoff, launcher
 * shortcuts, the review nudge, the notification launch key, the backup
 * repository with its folder) are opened and read into memory on IO once per
 * process, and the launch intent's handling and AccountDirectoryGate wait for
 * that. Reads on Main then hit memory: a first SharedPreferences read waits for
 * its file, and every app folder lookup checks the disk.
 */
internal object StartupPreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loading: Deferred<Unit>? = null

    val loaded: Boolean
        get() = synchronized(this) { loading?.isCompleted == true }

    fun start(context: Context): Deferred<Unit> = synchronized(this) {
        loading ?: scope.async {
            val application = context.applicationContext
            listOf(
                SUBSHELL_PREFERENCES,
                RedditLinkHandlingPromptStore.DEVICE_PREFERENCES_NAME,
                RedditExternalPollHandoffStore.PREFERENCES_NAME,
                LAUNCHER_SHORTCUTS_PREFERENCES,
                NUDGE_PREFERENCES,
                NotificationLaunchKey.PREFERENCES,
            ).forEach { name ->
                runCatching { application.getSharedPreferences(name, Context.MODE_PRIVATE).all }
            }
            runCatching { processSettingsBackupRepository(application) }
            Unit
        }.also { loading = it }
    }

    suspend fun await(context: Context) {
        start(context).await()
    }
}
