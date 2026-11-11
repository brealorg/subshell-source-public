package io.github.brealorg.subshell.ui.app

import android.content.Context
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.backup.processSettingsBackupRepository
import io.github.brealorg.subshell.data.privateprefs.SealedKeysPreferences
import io.github.brealorg.subshell.data.privateprefs.SealedStartup
import io.github.brealorg.subshell.inbox.NotificationLaunchKey
import io.github.brealorg.subshell.shortcuts.LAUNCHER_SHORTCUTS_PREFERENCES
import io.github.brealorg.subshell.ui.RedditExternalPollHandoffStore
import io.github.brealorg.subshell.ui.RedditLinkHandlingPromptStore
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
            val startedAt = System.nanoTime()
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
            // PRIVATEPREFS01 / PRIVATEPREFS05_START_DOES_NOT_WAIT_FOR_THE_KEYSTORE_V1: open the sealed
            // values (waiting at most two seconds) so the first reads on Main find them in memory; the
            // plain values an older build left are sealed afterwards, in the background.
            runCatching {
                SealedStartup.openThenSealInBackground(SealedKeysPreferences.allInstances(application), scope)
            }
            runCatching { processSettingsBackupRepository(application) }
            if (BuildConfig.DEBUG) {
                android.util.Log.i(
                    SealedStartup.LOG_TAG,
                    "start-up preferences ready after ${(System.nanoTime() - startedAt) / 1_000_000} ms",
                )
            }
            Unit
        }.also { loading = it }
    }

    suspend fun await(context: Context) {
        start(context).await()
    }
}
