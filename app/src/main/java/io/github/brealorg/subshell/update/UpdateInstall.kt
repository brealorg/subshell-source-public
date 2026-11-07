package io.github.brealorg.subshell.update

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * ISSUE286_UPDATE_INSTALLS_THROUGH_ANDROIDS_INSTALLER_V1 (#286): the verified
 * APK goes to Android's PackageInstaller. Android shows its own confirmation
 * and refuses an APK signed by another key, so subshell never replaces itself
 * without the reader and never with someone else's build. The checked APK is
 * kept in no-backup storage until Android has decided (#58), so an update
 * after a killed process needs no second download; it is deleted when the
 * install succeeds, fails or is refused, and anything that does not match the
 * manifest's hash is deleted at once.
 */

/*
 * ISSUE45_UPDATE_CONFIRMATION_IS_NEVER_LOST_V1 (#45): Android may refuse to
 * open its install confirmation from a broadcast while subshell is in the
 * background (no exception), and Home on that dialog leaves the session
 * pending with no answer. The confirmation is kept: Settings › Updates shows
 * it again when the reader comes back and offers Continue, and a new Update
 * abandons a session still waiting for the reader instead of being refused.
 */

internal sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    data class Downloading(val versionCode: Int, val downloaded: Long, val total: Long) : UpdateInstallState

    /**
     * Handed to Android; its own install dialog is on screen or coming.
     * [waitingForYou] once Android asked the reader to confirm.
     */
    data class Installing(val versionCode: Int, val waitingForYou: Boolean = false) : UpdateInstallState

    data class Failed(val versionCode: Int, val message: String) : UpdateInstallState
}

/** Android must let subshell install apps before Update can work. */
internal fun updateInstallAllowed(context: Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

internal fun updateInstallPermissionIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

/**
 * ISSUE291_UPDATE_DIALOG_UPDATES_IN_THE_APP_V1: "Update" in the update dialog
 * asks the Updates page to start the update as it opens, through the same
 * path as its own Update button (permission question included).
 */
internal object UpdateStartRequest {
    private var versionCode: Int = 0

    @Synchronized
    fun request(versionCode: Int) {
        this.versionCode = versionCode
    }

    /** The requested version once; 0 when nothing is waiting. */
    @Synchronized
    fun take(): Int = versionCode.also { versionCode = 0 }
}

internal object UpdateInstallCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    private var job: Job? = null
    private var sessionId: Int? = null
    private var confirmation: Intent? = null
    private var confirmationShown = false
    private val abandonedSessions = mutableSetOf<Int>()
    private var keptApk: File? = null

    val state: StateFlow<UpdateInstallState> = mutableState.asStateFlow()

    /** Starts the download and install of [release]; nothing while one runs. */
    @Synchronized
    fun start(
        context: Context,
        release: UpdateManifestRelease,
        downloader: UpdateApkDownloader = UpdateApkDownloader(),
        install: (Context, File) -> Int = ::commitUpdateInstall,
    ): Boolean {
        val current = mutableState.value
        if (current is UpdateInstallState.Downloading) return false
        if (current is UpdateInstallState.Installing && !current.waitingForYou) return false
        val appContext = context.applicationContext
        if (current is UpdateInstallState.Installing) abandonWaitingSession(appContext)
        // ISSUE58_A_SESSION_LEFT_BY_A_KILLED_PROCESS_IS_ABANDONED_V1: a session this
        // process knows nothing about (Android killed the app while it waited)
        // would stay pending next to the new one.
        abandonLeftoverSessions(appContext)
        mutableState.value = UpdateInstallState.Downloading(release.versionCode, 0L, -1L)
        job = scope.launch {
            val dir = File(appContext.noBackupFilesDir, "update")
            val target = File(dir, "update-${release.versionName}.apk")
            var handedOver = false
            try {
                // Copies of other releases are of no use any more.
                dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
                val result = downloader.reuseKept(release, target)
                    ?: downloader.download(release, target) { downloaded, total ->
                        mutableState.value = UpdateInstallState.Downloading(release.versionCode, downloaded, total)
                    }
                when (result) {
                    is UpdateApkDownload.Failed ->
                        mutableState.value = UpdateInstallState.Failed(release.versionCode, result.failure.message)
                    is UpdateApkDownload.Verified -> {
                        mutableState.value = UpdateInstallState.Installing(release.versionCode)
                        val committed = install(appContext, result.file)
                        // Kept until Android has decided: a process kill while the
                        // reader is deciding must not cost a second download.
                        synchronized(this@UpdateInstallCoordinator) {
                            sessionId = committed
                            keptApk = target
                        }
                        handedOver = true
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = UpdateInstallState.Idle
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = UpdateInstallState.Failed(release.versionCode, UPDATE_INSTALL_NOT_STARTED)
            } finally {
                if (!handedOver) withContext(NonCancellable) { target.delete() }
            }
        }
        return true
    }

    /** What Android's installer answered (UpdateInstallResultReceiver). */
    @Synchronized
    fun installerAnswered(status: Int, prompt: Intent? = null, answeredSession: Int? = null) {
        val current = mutableState.value as? UpdateInstallState.Installing ?: return
        // ISSUE57_A_LATE_ANSWER_FOR_ANOTHER_SESSION_IS_DROPPED_V1: an answer for a
        // session we abandoned, or for one that is not the current one, is not
        // about this update.
        if (answeredSession != null) {
            if (answeredSession in abandonedSessions) return
            val own = sessionId
            if (own != null && own != answeredSession) return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (prompt != null) {
                confirmation = prompt
                confirmationShown = false
            }
            mutableState.value = current.copy(waitingForYou = confirmation != null)
            return
        }
        clearSession()
        discardKeptApk()
        mutableState.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallState.Idle
            else -> UpdateInstallState.Failed(current.versionCode, updateInstallFailureMessage(status))
        }
    }

    /**
     * Opens Android's confirmation from [context] (an activity on screen).
     * [onlyIfNotShown] is the automatic case on return: it opens once, so
     * leaving the dialog with Home does not bring it back on every resume.
     */
    @Synchronized
    fun showConfirmation(
        context: Context,
        onlyIfNotShown: Boolean = false,
        launch: (Context, Intent) -> Unit = { from, intent -> from.startActivity(intent) },
    ): Boolean {
        val current = mutableState.value as? UpdateInstallState.Installing ?: return false
        val prompt = confirmation ?: return false
        if (onlyIfNotShown && confirmationShown) return false
        return try {
            launch(context, Intent(prompt))
            confirmationShown = true
            true
        } catch (_: Exception) {
            clearSession()
            discardKeptApk()
            mutableState.value = UpdateInstallState.Failed(current.versionCode, UPDATE_INSTALL_NOT_STARTED)
            false
        }
    }

    /** The broadcast tried to open the confirmation itself; Android may have refused silently. */
    @Synchronized
    fun confirmationOpenedFromBackground(opened: Boolean) {
        if (!opened) confirmationShown = false
    }

    private fun abandonWaitingSession(context: Context) {
        sessionId?.let { id ->
            abandonedSessions.add(id)
            try {
                context.packageManager.packageInstaller.abandonSession(id)
            } catch (_: Exception) {
                // Already gone with Android; nothing to abandon.
            }
        }
        clearSession()
    }

    private fun abandonLeftoverSessions(context: Context) {
        try {
            val installer = context.packageManager.packageInstaller
            installer.mySessions.forEach { info ->
                abandonedSessions.add(info.sessionId)
                runCatching { installer.abandonSession(info.sessionId) }
            }
        } catch (_: Exception) {
            // Android would not list them; the new session goes ahead as before.
        }
    }

    /** The install is decided (installed, refused or given up): the kept copy goes. */
    private fun discardKeptApk() {
        keptApk?.delete()
        keptApk = null
    }

    private fun clearSession() {
        sessionId = null
        confirmation = null
        confirmationShown = false
    }

    internal fun resetForTest() {
        job?.cancel()
        job = null
        clearSession()
        abandonedSessions.clear()
        keptApk = null
        mutableState.value = UpdateInstallState.Idle
    }
}

internal const val UPDATE_INSTALL_NOT_STARTED = "Android's installer couldn't be started. Try again."

internal fun updateInstallFailureMessage(status: Int): String = when (status) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> "The update wasn't installed."
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install."
    PackageInstaller.STATUS_FAILURE_CONFLICT,
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
    -> "Android refused the update because it doesn't match the installed app."
    PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough storage to install the update."
    PackageInstaller.STATUS_FAILURE_INVALID -> "Android couldn't read the downloaded update."
    else -> "Android didn't install the update."
}

/** Copies [apk] into an install session and commits it; Android takes it from there. Returns the session id. */
internal fun commitUpdateInstall(context: Context, apk: File): Int {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
        setSize(apk.length())
    }
    val sessionId = installer.createSession(params)
    try {
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("subshell.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(context, UpdateInstallResultReceiver::class.java)
                .setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            session.commit(PendingIntent.getBroadcast(context, sessionId, callback, flags).intentSender)
        }
        return sessionId
    } catch (failure: Exception) {
        runCatching { installer.abandonSession(sessionId) }
        throw failure
    }
}

internal class UpdateInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val answered = if (intent.hasExtra(PackageInstaller.EXTRA_SESSION_ID)) {
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        } else {
            null
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            UpdateInstallCoordinator.installerAnswered(status, confirm, answered)
            // Works while subshell is in front; from the background Android may
            // drop it without a word, and Settings › Updates shows it on return.
            val opened = UpdateInstallCoordinator.showConfirmation(context)
            UpdateInstallCoordinator.confirmationOpenedFromBackground(opened && appIsInFront())
            return
        }
        UpdateInstallCoordinator.installerAnswered(status, answeredSession = answered)
    }
}

private fun appIsInFront(): Boolean {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
