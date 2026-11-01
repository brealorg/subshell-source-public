package app.subshell.prototype.update

import android.app.Application
import android.content.pm.PackageInstaller
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.screens.settingsSearchResult
import app.subshell.prototype.ui.theme.SubshellTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE286_SETTINGS_UPDATES_V1 and ISSUE286_UPDATE_INSTALLS_THROUGH_ANDROIDS_INSTALLER_V1:
 * Settings › Updates says which version is installed and which is the newest,
 * checks on request, and Update downloads, verifies and hands the APK to
 * Android's installer, saying how it went.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue286UpdatesScreenTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val older = release(254, "0.90-beta")
    private val newer = release(255, "0.91-beta")

    private fun release(versionCode: Int, name: String) = UpdateManifestRelease(
        schemaVersion = UPDATE01_MANIFEST_SCHEMA_VERSION,
        channel = UPDATE01_BETA_CHANNEL,
        versionCode = versionCode,
        versionName = name,
        publishedAt = "2026-11-02T10:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v$name",
        apkSha256 = "b".repeat(64),
        headline = "Subshell $name: something new",
        highlights = listOf("One thing is better."),
    )

    @After
    fun reset() = UpdateInstallCoordinator.resetForTest()

    private fun shows(text: String) =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun tap(text: String) =
        composeRule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

    private fun state(
        available: UpdateManifestRelease? = null,
        install: UpdateInstallState = UpdateInstallState.Idle,
        checkMessage: String? = null,
        runtimeEnabled: Boolean = true,
    ) = UpdatesScreenState(
        installedVersionName = "0.90-beta",
        runtimeEnabled = runtimeEnabled,
        latest = available ?: older,
        available = available,
        lastCheckedEpochMillis = 1_800_000_000_000L - 5 * 60_000L,
        nowEpochMillis = 1_800_000_000_000L,
        checkMessage = checkMessage,
        install = install,
    )

    @Test
    fun theScreenSaysWhatIsInstalledWhatIsNewestAndHowTheUpdateGoes() {
        val screen = mutableStateOf(state(checkMessage = UPDATES_UP_TO_DATE))
        var checks = 0
        val updates = mutableListOf<UpdateManifestRelease>()
        composeRule.setContent {
            SubshellTheme {
                UpdatesContent(
                    state = screen.value,
                    onBack = {},
                    onCheckNow = { checks++ },
                    onUpdate = { updates += it },
                    onViewRelease = {},
                )
            }
        }
        assertTrue(shows("Installed"))
        assertTrue(shows("Latest"))
        assertTrue(shows("5 minutes ago"))
        assertTrue(shows(UPDATES_UP_TO_DATE))
        assertTrue(composeRule.onAllNodes(hasText("Update") and hasClickAction()).fetchSemanticsNodes().isEmpty())
        tap("Check now")
        assertEquals(1, checks)

        screen.value = state(available = newer)
        composeRule.waitForIdle()
        assertTrue(shows("subshell 0.91-beta is available"))
        assertTrue(shows("One thing is better."))
        tap("Update")
        assertEquals(listOf(newer), updates)

        screen.value = state(available = newer, install = UpdateInstallState.Downloading(255, 1L shl 20, 2L shl 20))
        composeRule.waitForIdle()
        assertTrue(shows("Downloading… 1.0 of 2.0 MB"))
        composeRule.onNode(hasText("Update") and hasClickAction()).assertIsNotEnabled()

        screen.value = state(
            available = newer,
            install = UpdateInstallState.Failed(255, UpdateApkFailure.HASH_MISMATCH.message),
        )
        composeRule.waitForIdle()
        assertTrue(shows(UpdateApkFailure.HASH_MISMATCH.message))
        tap("Try again")
        assertEquals(2, updates.size)
    }

    @Test
    fun aDebugBuildSaysChecksAreOff() {
        // Settings › Updates in this (debug) build, as the reader would open it.
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = SubshellState(initialInteractionSideConfigured = true),
                    backupRepository = SettingsBackupRepository(context, "test"),
                    onBack = {},
                    initialDestinationName = SettingsDestination.UPDATES.name,
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(shows("Installed"))
        assertTrue(shows(UPDATES_OFF_IN_THIS_BUILD))
        assertFalse(shows("Check now"))
        assertTrue("updates" in settingsSearchResult("update").itemKeys)
        assertTrue("updates" in settingsSearchResult("check version").itemKeys)
    }

    @Test
    fun updateHandsTheVerifiedApkToAndroidsInstallerAndSaysWhatItAnswered() = runBlocking {
        val apk = ByteArray(2048) { it.toByte() }
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(apk)
            .joinToString("") { "%02x".format(it) }
        val release = newer.copy(apkSha256 = sha)
        val github = OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(apk.toResponseBody(null))
                .build()
        }.build()
        val installed = mutableListOf<ByteArray>()
        var handed: File? = null

        assertTrue(
            UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, file ->
                handed = file
                installed += file.readBytes()
            },
        )
        waitFor { UpdateInstallCoordinator.state.value is UpdateInstallState.Installing }
        waitFor { handed?.exists() == false }
        assertTrue(installed.single().contentEquals(apk))
        // One at a time.
        assertFalse(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, _ -> })

        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_PENDING_USER_ACTION)
        assertEquals(UpdateInstallState.Installing(255), UpdateInstallCoordinator.state.value)
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(
            UpdateInstallState.Failed(255, "The update wasn't installed."),
            UpdateInstallCoordinator.state.value,
        )
        assertEquals(
            "Android refused the update because it doesn't match the installed app.",
            updateInstallFailureMessage(PackageInstaller.STATUS_FAILURE_CONFLICT),
        )
    }

    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000L
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out" }
            Thread.sleep(10)
        }
    }
}
