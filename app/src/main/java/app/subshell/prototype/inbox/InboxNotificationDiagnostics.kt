package app.subshell.prototype.inbox

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.subshell.prototype.communityposts.COMMUNITY_COMMENTS_NOTIFICATION_CHANNEL_ID
import app.subshell.prototype.communityposts.COMMUNITY_POSTS_NOTIFICATION_CHANNEL_ID
import app.subshell.prototype.data.diagnostics.NotificationDiagnostics
import app.subshell.prototype.offline.OFFLINE_SAVE_CHANNEL_ID
import app.subshell.prototype.update.UpdateNotificationCenter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ISSUE272_NOTIFICATION_STATE_IN_THE_REPORT_V1 (#272): reads, when a report
 * is made, everything that decides whether a notification can reach the
 * reader. Blocks on WorkManager, so it runs off the main thread.
 */
internal fun readNotificationDiagnostics(context: Context): NotificationDiagnostics {
    val appContext = context.applicationContext
    val policy = runCatching { InboxNotificationCenter.notificationPolicy(appContext) }.getOrNull()
    return NotificationDiagnostics(
        appNotificationsEnabled = runCatching {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }.getOrNull(),
        postPermission = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "not_required"
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED -> "granted"
            else -> "denied"
        },
        inboxNotificationsSetting = inboxNotificationsEnabled(appContext),
        types = if (policy == null) {
            emptyMap()
        } else {
            linkedMapOf(
                "replies" to policy.replies,
                "mentions" to policy.mentions,
                "private_messages" to policy.privateMessages,
                "reddit_notices" to policy.redditNotices,
                "modmail" to policy.modmail,
            )
        },
        channels = notificationChannelImportance(appContext),
        inboxBackgroundWork = runCatching {
            WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWork(INBOX_BACKGROUND_SYNC_UNIQUE_WORK)
                .get(5, TimeUnit.SECONDS)
                .firstOrNull { !it.state.isFinished }
                ?.state
                ?.workToken()
                ?: "none"
        }.getOrDefault("unknown"),
        backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { appContext.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted }.getOrNull()
        } else {
            null
        },
        batteryOptimizationIgnored = runCatching {
            appContext.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(appContext.packageName)
        }.getOrNull(),
        standbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                standbyBucketToken(appContext.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket)
            }.getOrDefault("unknown")
        } else {
            "unknown"
        },
    )
}

/** Only Subshell's own fixed channel IDs: a conversation channel may carry a name. */
private val DIAGNOSED_CHANNEL_IDS = listOf(
    INBOX_NOTIFICATION_CHANNEL_ID,
    COMMUNITY_POSTS_NOTIFICATION_CHANNEL_ID,
    COMMUNITY_COMMENTS_NOTIFICATION_CHANNEL_ID,
    UpdateNotificationCenter.CHANNEL_ID,
    OFFLINE_SAVE_CHANNEL_ID,
)

private fun notificationChannelImportance(context: Context): Map<String, String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyMap()
    val manager = context.getSystemService(NotificationManager::class.java) ?: return emptyMap()
    return DIAGNOSED_CHANNEL_IDS.associateWith { id ->
        when (runCatching { manager.getNotificationChannel(id) }.getOrNull()?.importance) {
            null -> "missing"
            NotificationManager.IMPORTANCE_NONE -> "none"
            NotificationManager.IMPORTANCE_MIN -> "min"
            NotificationManager.IMPORTANCE_LOW -> "low"
            NotificationManager.IMPORTANCE_DEFAULT -> "default"
            NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> "high"
            else -> "unknown"
        }
    }
}

private fun WorkInfo.State.workToken(): String = name.lowercase(Locale.ROOT)

@androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
internal fun standbyBucketToken(bucket: Int?): String = when (bucket) {
    UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
    UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
    UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
    UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
    45 -> "restricted" // STANDBY_BUCKET_RESTRICTED (API 30)
    else -> "unknown"
}
