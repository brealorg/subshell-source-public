package app.subshell.prototype.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.MainActivity
import app.subshell.prototype.R

internal const val UPDATE01_ACTION_OPEN_UPDATE =
    "app.subshell.prototype.action.OPEN_UPDATE"
internal const val UPDATE01_EXTRA_VERSION_CODE =
    "app.subshell.prototype.extra.UPDATE_VERSION_CODE"
internal const val UPDATE01_EXTRA_VERSION_NAME =
    "app.subshell.prototype.extra.UPDATE_VERSION_NAME"
internal const val UPDATE01_EXTRA_RELEASE_URL =
    "app.subshell.prototype.extra.UPDATE_RELEASE_URL"

internal object UpdateNotificationCenter {
    private const val CHANNEL_ID = "subshell_app_updates_v1"
    private const val NOTIFICATION_ID = 0x55A1
    private const val REQUEST_CODE = 0x55A1

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
            ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New subshell beta releases"
            },
        )
    }

    fun canPost(context: Context): Boolean {
        val appContext = context.applicationContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    fun postAvailable(
        context: Context,
        release: UpdateManifestRelease,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): Boolean {
        val appContext = context.applicationContext
        if (!canPost(appContext)) return false

        ensureChannel(appContext)

        val intent = Intent(appContext, MainActivity::class.java)
            .setAction(UPDATE01_ACTION_OPEN_UPDATE)
            .putExtra(UPDATE01_EXTRA_VERSION_CODE, release.versionCode)
            .putExtra(UPDATE01_EXTRA_VERSION_NAME, release.versionName)
            .putExtra(UPDATE01_EXTRA_RELEASE_URL, release.releaseUrl)

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_subshell)
            .setContentTitle(updateNotificationTitle(release, currentVersionCode))
            .setContentText(updateNotificationText(release, currentVersionCode))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    // UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1
                    release.advisoryFor(currentVersionCode)?.message ?: release.headline,
                ),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        return runCatching {
            NotificationManagerCompat.from(appContext)
                .notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}

/**
 * UPDATE08_UPDATE_DIALOG_ASKS_FOR_NOTIFICATIONS_V1 (owner): a reader who never
 * allowed notifications (Subshell otherwise asks only for a signed-in
 * account's inbox) hears of the next release only inside the app. The update
 * dialog offers "Notify me next time": Android's question while it can still
 * be asked, Subshell's notification settings once it cannot.
 */
internal enum class UpdateNotificationOptIn { NONE, ASK, OPEN_SETTINGS }

internal fun updateNotificationOptIn(
    sdkInt: Int,
    permissionGranted: Boolean,
    notificationsEnabled: Boolean,
    askedBefore: Boolean,
    showRationale: Boolean,
): UpdateNotificationOptIn {
    val permissionNeeded = sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted
    return when {
        !permissionNeeded && notificationsEnabled -> UpdateNotificationOptIn.NONE
        // After a refusal Android still asks while it shows a rationale; once
        // it stops (a second refusal), only the system settings can allow it.
        permissionNeeded && (!askedBefore || showRationale) -> UpdateNotificationOptIn.ASK
        else -> UpdateNotificationOptIn.OPEN_SETTINGS
    }
}

internal fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

internal fun updateNotificationTitle(release: UpdateManifestRelease, currentVersionCode: Int): String =
    if (release.advisoryFor(currentVersionCode) != null) {
        "Known problem in your subshell version"
    } else {
        "subshell update available"
    }

internal fun updateNotificationText(release: UpdateManifestRelease, currentVersionCode: Int): String =
    if (release.advisoryFor(currentVersionCode) != null) {
        "${release.versionName} fixes it"
    } else {
        "${release.versionName} is ready"
    }
