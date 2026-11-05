package io.github.brealorg.subshell

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.brealorg.subshell.share.ShareIntoSubshellComponent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #42 follow-ups: what a 0.91 install left under the old class names keeps working after the
 * update. ISSUE55_WORK_FROM_BEFORE_THE_RENAME_RUNS_V1, ISSUE52_SHARE_INTO_SUBSHELL_SURVIVES_THE_RENAME_V1.
 * (#54 needs nothing: Android removes an app's notifications when it is updated.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue42AfterTheRenameTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val marker get() = File(File(context.noBackupFilesDir, "migrations"), AfterTheRename.MARKER_FILE)

    @Before
    fun freshUpdate() {
        // The application's own start ran the step already; begin each test from before it.
        AfterTheRename.backgroundRun?.join(5_000)
        marker.delete()
    }

    @Test
    fun theUpdateBroadcastRunsTheStepOffTheMainThread() {
        context.sendBroadcast(
            Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(context.packageName),
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val deadline = System.currentTimeMillis() + 5_000
        while (!marker.isFile && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(marker.isFile)
    }

    @Test
    fun theStepAlsoRunsWhenTheAppStartsSinceTheUpdateBroadcastCanBeLost() {
        val retired = ComponentName(context.packageName, AfterTheRename.RETIRED_SHARE_ALIAS)
        context.packageManager.setComponentEnabledSetting(
            retired,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        (context as SubshellApplication).onCreate()
        AfterTheRename.backgroundRun?.join(5_000)

        assertTrue(ShareIntoSubshellComponent.isEnabled(context))
        assertTrue(marker.isFile)
    }

    @Test
    fun everyWorkerA091InstallStoredRunsUnderItsOldName() {
        assertSame(RenamedWorkerFactory, (context as Configuration.Provider).workManagerConfiguration.workerFactory)
        val expected = mapOf(
            "app.subshell.prototype.inbox.InboxBackgroundSyncWorker" to "io.github.brealorg.subshell.inbox.InboxBackgroundSyncWorker",
            "app.subshell.prototype.inbox.InboxReplyWorker" to "io.github.brealorg.subshell.inbox.InboxReplyWorker",
            "app.subshell.prototype.communityposts.CommunityPostCheckWorker" to
                "io.github.brealorg.subshell.communityposts.CommunityPostCheckWorker",
            "app.subshell.prototype.export.RedditDataExportWorker" to "io.github.brealorg.subshell.export.RedditDataExportWorker",
            "app.subshell.prototype.offline.OfflineSaveWorker" to "io.github.brealorg.subshell.offline.OfflineSaveWorker",
            "app.subshell.prototype.update.UpdateCheckWorker" to "io.github.brealorg.subshell.update.UpdateCheckWorker",
        )
        assertEquals(expected, RenamedWorkerFactory.workersByRetiredName.mapValues { (_, worker) -> worker.name })
        RenamedWorkerFactory.workersByRetiredName.values.forEach { worker ->
            assertTrue(worker.name, CoroutineWorker::class.java.isAssignableFrom(worker))
            worker.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
        }
    }

    @Test
    fun shareIntoSubshellThatWasOnStaysOnAndTheStepRunsOnce() {
        val retired = ComponentName(context.packageName, AfterTheRename.RETIRED_SHARE_ALIAS)
        context.packageManager.setComponentEnabledSetting(
            retired,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        assertFalse(ShareIntoSubshellComponent.isEnabled(context))

        AfterTheRename.runOnce(context)
        assertTrue(ShareIntoSubshellComponent.isEnabled(context))
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            context.packageManager.getComponentEnabledSetting(retired),
        )

        ShareIntoSubshellComponent.setEnabled(context, false)
        context.packageManager.setComponentEnabledSetting(
            retired,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AfterTheRename.runOnce(context)
        assertFalse(ShareIntoSubshellComponent.isEnabled(context))
    }

    @Test
    fun shareIntoSubshellThatWasOffStaysOff() {
        AfterTheRename.runOnce(context)

        assertFalse(ShareIntoSubshellComponent.isEnabled(context))
    }

    /**
     * Android forgets the on/off state of a component the update no longer has, so the retired
     * alias stays in the manifest: disabled, not exported and never in the share sheet.
     */
    @Test
    fun theRetiredAliasIsKeptOnlyToHoldTheSetting() {
        val retired = ComponentName(context.packageName, AfterTheRename.RETIRED_SHARE_ALIAS)
        val info = context.packageManager.getActivityInfo(retired, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertFalse(info.enabled)
        assertFalse(info.exported)
        context.packageManager.setComponentEnabledSetting(
            retired,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
        val targets = context.packageManager.queryIntentActivities(share, 0).map { it.activityInfo.name }
        assertFalse(targets.toString(), AfterTheRename.RETIRED_SHARE_ALIAS in targets)
    }
}
