package io.github.brealorg.subshell.update

import io.github.brealorg.subshell.harness.SUBSHELL_PACKAGE_DIR
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Update01A1SourcePolicyTest {
    private val root = File(requireNotNull(System.getProperty("user.dir"))).parentFile

    @Test
    fun manifestOwnsPackageReplacedReceiverAndOnlyItsOwnInstallerAnswer() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("UpdatePackageReplacedReceiver"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(manifest.contains("android:exported=\"false\""))
        // ISSUE286: the install permission comes with a receiver no other app can call.
        assertTrue(manifest.contains("REQUEST_INSTALL_PACKAGES"))
        assertTrue(
            manifest.contains(
                "android:name=\".update.UpdateInstallResultReceiver\"\n            android:exported=\"false\"",
            ),
        )
    }

    @Test
    fun updateStateIsDeviceLocalAndExistingBackupRulesExcludeItsPreferencesFile() {
        val store = source(
            "app/src/main/java/${SUBSHELL_PACKAGE_DIR}/update/UpdateStateStore.kt",
        )
        val backup = source("app/src/main/res/xml/backup_rules.xml")
        val extraction = source("app/src/main/res/xml/data_extraction_rules.xml")

        assertTrue(store.contains("\"subshell_device_preferences\""))
        assertTrue(backup.contains("subshell_device_preferences.xml"))
        assertTrue(extraction.contains("subshell_device_preferences.xml"))
    }

    @Test
    fun schedulerIsIndependentFromInboxAndUsesConnectedTwelveHourPeriodicWork() {
        val work = source(
            "app/src/main/java/${SUBSHELL_PACKAGE_DIR}/update/UpdateWork.kt",
        )
        assertTrue(work.contains("12,"))
        assertTrue(work.contains("TimeUnit.HOURS"))
        assertTrue(work.contains("NetworkType.CONNECTED"))
        assertTrue(work.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertFalse(work.contains("InboxBackgroundSync"))
        assertFalse(work.contains("INBOX_NOTIFICATIONS_ENABLED"))
    }

    @Test
    fun playCoreRemainsOutOfScope() {
        val updateDirectory = File(
            root,
            "app/src/main/java/${SUBSHELL_PACKAGE_DIR}/update",
        ).walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }
        val gradle = source("app/build.gradle.kts")

        assertFalse(updateDirectory.contains("AppUpdateManager"))
        assertFalse(gradle.contains("app-update"))
    }

    @Test
    fun mainActivitySchedulesOnlyThroughReleaseRuntimeFence() {
        val main = source(
            "app/src/main/java/${SUBSHELL_PACKAGE_DIR}/MainActivity.kt",
        )
        assertTrue(main.contains("UPDATE01A1A_GITHUB_BETA_UPDATE_AWARENESS_FOUNDATION_V1"))
        assertTrue(main.contains("UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)"))
        assertTrue(main.contains("UpdateCheckScheduler.schedule(applicationContext)"))
    }

    private fun source(relative: String): String =
        File(root, relative).also { file ->
            check(file.exists()) { "Missing fixture: ${file.path}" }
        }.readText()
}
