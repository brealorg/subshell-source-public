package app.subshell.prototype.update

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkManager
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellSettings
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (update01a1a): updates have their own notification channel whatever
 * the inbox setting, check every 12 hours on a connected network in their own
 * work, and an installed update leaves "What's new" pending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3UpdateRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun updatesHaveTheirOwnChannelWhateverTheInboxSetting() {
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE).edit()
            .putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, false)
            .commit()

        UpdateNotificationCenter.ensureChannel(context)

        val channel = requireNotNull(
            context.getSystemService(NotificationManager::class.java).getNotificationChannel("subshell_app_updates_v1"),
        )
        assertEquals("App updates", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun updatesAreCheckedEveryTwelveHoursOnAConnectedNetworkOnlyInTheReleaseRuntime() {
        ensureWorkManagerInitialized(context)
        val manager = WorkManager.getInstance(context)
        fun scheduled() = manager.getWorkInfosForUniqueWork(UpdateCheckScheduler.PERIODIC_WORK_NAME)
            .get(5, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }

        UpdateCheckScheduler.schedule(context, runtimeEnabled = false)
        assertTrue(scheduled().isEmpty())

        UpdateCheckScheduler.schedule(context, runtimeEnabled = true)
        val work = scheduled().single()
        assertEquals(TimeUnit.HOURS.toMillis(12), work.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun anInstalledUpdateLeavesWhatsNewPendingOnlyForThePackageReplacedAction() {
        ensureWorkManagerInitialized(context)
        val receiver = UpdatePackageReplacedReceiver()
        val store = UpdateStateStore(context)

        receiver.handle(context, Intent(Intent.ACTION_BOOT_COMPLETED), runtimeEnabled = true, versionCode = 300)
        assertEquals(0, store.pendingWhatsNewVersionCode())
        receiver.handle(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED), runtimeEnabled = false, versionCode = 300)
        assertEquals(0, store.pendingWhatsNewVersionCode())

        receiver.handle(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED), runtimeEnabled = true, versionCode = 300)

        assertEquals(300, store.pendingWhatsNewVersionCode())
    }
}
