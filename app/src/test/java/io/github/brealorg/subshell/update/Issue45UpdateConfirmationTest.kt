package io.github.brealorg.subshell.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.theme.SubshellTheme
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
 * ISSUE45_UPDATE_CONFIRMATION_IS_NEVER_LOST_V1 (#45): when Android's install
 * confirmation could not open (subshell was in the background) or was left
 * with Home, the update does not hang in "installing": the confirmation opens
 * again on return, Continue opens it on request, and a new Update starts over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue45UpdateConfirmationTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val apk = ByteArray(1024) { it.toByte() }
    private val release = UpdateManifestRelease(
        schemaVersion = UPDATE01_MANIFEST_SCHEMA_VERSION,
        channel = UPDATE01_BETA_CHANNEL,
        versionCode = 255,
        versionName = "0.91-beta",
        publishedAt = "2026-11-02T10:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.91-beta",
        apkSha256 = java.security.MessageDigest.getInstance("SHA-256").digest(apk)
            .joinToString("") { "%02x".format(it) },
        headline = "Subshell 0.91-beta: something new",
        highlights = listOf("One thing is better."),
    )
    private val github = OkHttpClient.Builder().addInterceptor { chain ->
        okhttp3.Response.Builder()
            .request(chain.request())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(apk.toResponseBody(null))
            .build()
    }.build()
    private val prompt = Intent("android.content.pm.action.CONFIRM_INSTALL")

    @After
    fun reset() = UpdateInstallCoordinator.resetForTest()

    private fun handToInstaller() {
        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, _ -> 7 })
        waitFor { UpdateInstallCoordinator.state.value is UpdateInstallState.Installing }
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }
    }

    @Test
    fun aConfirmationThatCouldNotOpenOpensOnReturnAndOnContinue() {
        handToInstaller()
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_PENDING_USER_ACTION, prompt)
        assertEquals(UpdateInstallState.Installing(255, waitingForYou = true), UpdateInstallCoordinator.state.value)

        val opened = mutableListOf<String?>()
        val launch: (android.content.Context, Intent) -> Unit = { _, intent -> opened += intent.action }
        // The broadcast tried while subshell was in the background; Android dropped it.
        UpdateInstallCoordinator.showConfirmation(context, launch = launch)
        UpdateInstallCoordinator.confirmationOpenedFromBackground(false)

        // Back on Settings › Updates: it opens once by itself.
        assertTrue(UpdateInstallCoordinator.showConfirmation(context, onlyIfNotShown = true, launch = launch))
        // Home on Android's dialog, back again: not forced on every return.
        assertFalse(UpdateInstallCoordinator.showConfirmation(context, onlyIfNotShown = true, launch = launch))
        // Continue opens it on request.
        assertTrue(UpdateInstallCoordinator.showConfirmation(context, launch = launch))
        assertEquals(3, opened.size)

        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(UpdateInstallState.Failed(255, "The update wasn't installed."), UpdateInstallCoordinator.state.value)
        assertFalse(UpdateInstallCoordinator.showConfirmation(context, launch = launch))
    }

    @Test
    fun aNewUpdateStartsOverInsteadOfBeingRefused() {
        handToInstaller()
        // Still with Android, not yet asking the reader: one at a time.
        assertFalse(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, _ -> 8 })
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_PENDING_USER_ACTION, prompt)
        // Waiting for a reader who never answered: Update starts again.
        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, _ -> 8 })
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }
    }

    @Test
    fun aLateAnswerForAnAbandonedSessionIsNotAboutTheNewUpdate() {
        // Session 7 waits for the reader and is abandoned by a new Update (session 8).
        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, _ -> 7 })
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_PENDING_USER_ACTION, prompt, 7)
        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, _ -> 8 })
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }
        Thread.sleep(100)

        // Android's late abort for 7 arrives: 8 is untouched.
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_FAILURE_ABORTED, answeredSession = 7)
        assertEquals(UpdateInstallState.Installing(255), UpdateInstallCoordinator.state.value)
        // 8's own confirmation still works and 8 can still be abandoned and replaced.
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_PENDING_USER_ACTION, prompt, 8)
        assertEquals(UpdateInstallState.Installing(255, waitingForYou = true), UpdateInstallCoordinator.state.value)
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_FAILURE_ABORTED, answeredSession = 8)
        assertEquals(UpdateInstallState.Failed(255, "The update wasn't installed."), UpdateInstallCoordinator.state.value)
    }

    @Test
    fun aSessionLeftByAKilledProcessIsAbandonedBeforeANewOne() {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val left = installer.createSession(params)
        assertTrue(installer.mySessions.any { it.sessionId == left })

        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(github)) { _, _ -> 9 })
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }

        assertFalse(installer.mySessions.any { it.sessionId == left })
    }

    @Test
    fun theCheckedApkIsKeptUntilTheInstallIsDecided() {
        var downloads = 0
        val counting = OkHttpClient.Builder().addInterceptor { chain -> downloads++; chain.proceed(chain.request()) }.addInterceptor(github.interceptors.first()).build()
        val kept = java.io.File(java.io.File(context.noBackupFilesDir, "update"), "update-0.91-beta.apk")

        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(counting)) { _, _ -> 7 })
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }
        Thread.sleep(100)
        assertEquals(1, downloads)
        assertTrue("kept while Android waits", kept.isFile)

        // The process is killed: only the file is left. Update takes it from disk.
        UpdateInstallCoordinator.resetForTest()
        assertTrue(kept.isFile)
        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(counting)) { _, _ -> 8 })
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }
        Thread.sleep(100)
        assertEquals("no second download", 1, downloads)

        // Android decides: the copy goes.
        UpdateInstallCoordinator.installerAnswered(PackageInstaller.STATUS_FAILURE_ABORTED, answeredSession = 8)
        assertFalse(kept.exists())
    }

    @Test
    fun aKeptApkThatNoLongerMatchesIsDownloadedAgain() {
        var downloads = 0
        val counting = OkHttpClient.Builder().addInterceptor { chain -> downloads++; chain.proceed(chain.request()) }.addInterceptor(github.interceptors.first()).build()
        val dir = java.io.File(context.noBackupFilesDir, "update").apply { mkdirs() }
        java.io.File(dir, "update-0.91-beta.apk").writeBytes(ByteArray(1024) { 1 })

        assertTrue(UpdateInstallCoordinator.start(context, release, UpdateApkDownloader(counting)) { _, _ -> 7 })
        waitFor { UpdateInstallCoordinator.state.value == UpdateInstallState.Installing(255) }
        assertEquals(1, downloads)
    }

    @Test
    fun theScreenSaysAndroidIsWaitingAndOffersContinue() {
        val install = mutableStateOf<UpdateInstallState>(UpdateInstallState.Installing(255, waitingForYou = true))
        var continued = 0
        var updates = 0
        composeRule.setContent {
            SubshellTheme {
                UpdatesContent(
                    state = UpdatesScreenState(
                        installedVersionName = "0.90-beta",
                        runtimeEnabled = true,
                        latest = release,
                        available = release,
                        lastCheckedEpochMillis = 0L,
                        nowEpochMillis = 1_800_000_000_000L,
                        install = install.value,
                    ),
                    onBack = {},
                    onCheckNow = {},
                    onUpdate = { updates++ },
                    onViewRelease = {},
                    onContinueInstall = { continued++ },
                )
            }
        }
        assertTrue(composeRule.onAllNodesWithText(UPDATE_WAITING_FOR_YOU).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNode(hasText("Continue") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, continued)
        assertEquals(0, updates)
    }

    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000L
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out" }
            Thread.sleep(10)
        }
    }
}
