package app.subshell.prototype.data.diagnostics

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * DIAGISSUE01_THE_REPORT_GOES_WITH_THE_ISSUE_V1: "Open prefilled GitHub
 * issue" puts the whole report on the clipboard and opens the bug form with
 * its Diagnostic report field asking for it; the report never rides in the link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagIssue01ReportGoesWithIssueTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val repository = DiagnosticGitHubRepository(owner = "brealorg", name = "subshell")
    private val environment = DiagnosticReportEnvironment(
        appVersionName = "0.90-beta",
        appVersionCode = 254,
        buildType = "release",
        debugSlot = "",
        androidApi = 37,
        androidRelease = "17",
        deviceManufacturer = "Google",
        deviceModel = "Pixel 6",
        screenWidthDp = 411,
        screenHeightDp = 914,
        smallestScreenWidthDp = 411,
    )
    private val report = StagedDiagnosticReport(
        file = File(context.cacheDir, "report.txt"),
        text = "subshell diagnostic report\nschema=DIAG01A1_V1\n[reddit_refusals]\napi|403|network_security_block|3\n",
    )

    @Test
    fun theReportIsCopiedAndTheFormAsksForItWithoutCarryingIt() {
        // A browser and the GitHub app both take github.com links; only the
        // browser keeps the prefilled form.
        val browser = android.content.ComponentName("org.example.browser", "org.example.browser.Main")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(browser)
        packageManager.addIntentFilterForActivity(
            browser,
            android.content.IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
        // The Diagnostics screen calls it with its activity.
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val outcome = openPrefilledDiagnosticGitHubIssue(activity, repository, environment, report)

        assertEquals(PrefilledDiagnosticIssueOutcome.OPENED_REPORT_COPIED, outcome)
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertEquals(report.text, clip?.getItemAt(0)?.text?.toString())

        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("org.example.browser", opened.`package`)
        val url = opened.dataString.orEmpty()
        assertTrue(url.startsWith("https://github.com/brealorg/subshell/issues/new?template=bug_report.yml"))
        assertFalse(url.contains("diagnostics="))
        assertFalse(url.contains("network_security_block"))
        assertFalse(url.contains("schema%3DDIAG01A1_V1"))
    }
}
