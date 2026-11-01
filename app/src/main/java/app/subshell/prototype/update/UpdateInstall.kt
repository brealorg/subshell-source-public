package app.subshell.prototype.update

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
 * without the reader and never with someone else's build. The download is
 * deleted once the installer has its copy, or when anything fails.
 */

internal sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    data class Downloading(val versionCode: Int, val downloaded: Long, val total: Long) : UpdateInstallState

    /** Handed to Android; its own install dialog is on screen or coming. */
    data class Installing(val versionCode: Int) : UpdateInstallState

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

internal object UpdateInstallCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    private var job: Job? = null

    val state: StateFlow<UpdateInstallState> = mutableState.asStateFlow()

    /** Starts the download and install of [release]; nothing while one runs. */
    @Synchronized
    fun start(
        context: Context,
        release: UpdateManifestRelease,
        downloader: UpdateApkDownloader = UpdateApkDownloader(),
        install: (Context, File) -> Unit = ::commitUpdateInstall,
    ): Boolean {
        val current = mutableState.value
        if (current is UpdateInstallState.Downloading || current is UpdateInstallState.Installing) return false
        val appContext = context.applicationContext
        mutableState.value = UpdateInstallState.Downloading(release.versionCode, 0L, -1L)
        job = scope.launch {
            val target = File(File(appContext.cacheDir, "update"), "update-${release.versionName}.apk")
            try {
                val result = downloader.download(release, target) { downloaded, total ->
                    mutableState.value = UpdateInstallState.Downloading(release.versionCode, downloaded, total)
                }
                when (result) {
                    is UpdateApkDownload.Failed ->
                        mutableState.value = UpdateInstallState.Failed(release.versionCode, result.failure.message)
                    is UpdateApkDownload.Verified -> {
                        mutableState.value = UpdateInstallState.Installing(release.versionCode)
                        install(appContext, result.file)
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = UpdateInstallState.Idle
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = UpdateInstallState.Failed(release.versionCode, UPDATE_INSTALL_NOT_STARTED)
            } finally {
                withContext(NonCancellable) { target.delete() }
            }
        }
        return true
    }

    /** What Android's installer answered (UpdateInstallResultReceiver). */
    fun installerAnswered(status: Int) {
        val current = mutableState.value as? UpdateInstallState.Installing ?: return
        mutableState.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> current
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallState.Idle
            else -> UpdateInstallState.Failed(current.versionCode, updateInstallFailureMessage(status))
        }
    }

    internal fun resetForTest() {
        job?.cancel()
        job = null
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

/** Copies [apk] into an install session and commits it; Android takes it from there. */
internal fun commitUpdateInstall(context: Context, apk: File) {
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
    } catch (failure: Exception) {
        runCatching { installer.abandonSession(sessionId) }
        throw failure
    }
}

internal class UpdateInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            confirm?.let { prompt ->
                try {
                    context.startActivity(prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_FAILURE_ABORTED)
                    return
                }
            }
        }
        UpdateInstallCoordinator.installerAnswered(status)
    }
}
