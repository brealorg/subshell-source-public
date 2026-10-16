package app.subshell.prototype.data.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.app.CRASH_REPORT_PROMPT_SCOPE
import app.subshell.prototype.ui.app.CrashReportPrompt
import app.subshell.prototype.ui.app.crashReportPromptPresentation
import app.subshell.prototype.ui.app.takeCrashesToOffer
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.theme.SubshellTheme
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

/**
 * CRASH01_CRASHES_ARE_RECORDED_LOCALLY_V1, CRASH01_NEXT_START_OFFERS_A_REPORT_V1
 * (owner 2026-10-16): crashes are kept on the device as class names and code
 * locations only, and the next start offers a report once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Crash01CrashReportsTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clean() {
        CrashRecordStore(context).clear()
        DiagnosticEventStore(context).clear()
        context.getSharedPreferences("subshell_crash_reports", 0).edit().clear().commit()
    }

    @Test
    fun aCrashKeepsItsClassesAndCodeLocationsButNeverItsText() {
        val secret = "token=abc123 /data/user/0/u/reader@example.com"
        val crash = IllegalStateException(secret, IllegalArgumentException("$secret inner"))
        val record = crashRecordOf(crash, nowEpochMs = 1_000L, appVersionCode = 246L)

        assertEquals(
            listOf("java.lang.IllegalStateException", "java.lang.IllegalArgumentException"),
            record.exceptionClasses,
        )
        assertTrue(record.topFrame!!.contains("Crash01CrashReportsTest"))
        assertTrue(
            record.appFrames.first(),
            record.appFrames.first().startsWith(
                "data.diagnostics.Crash01CrashReportsTest.aCrashKeepsItsClassesAndCodeLocationsButNeverItsText:",
            ),
        )
        val report = buildDiagnosticReport(
            snapshot = DiagnosticSnapshot(),
            environment = environment(),
            crashes = listOf(record),
        )
        listOf("token=", "abc123", "reader@example.com", "/data/user").forEach { fragment ->
            assertFalse(fragment, encodeCrashRecord(record).contains(fragment))
            assertFalse(fragment, report.contains(fragment))
        }
        assertTrue(report.contains("exception_messages=not_collected"))
        assertTrue(
            report.contains(
                "kind=crash app_version_code=246 " +
                    "exception=java.lang.IllegalStateException<-java.lang.IllegalArgumentException top=",
            ),
        )
    }

    @Test
    fun theHandlerRecordsTheCrashAndThenHandsItOnAsBefore() {
        val store = CrashRecordStore(context)
        var handedOn: Throwable? = null
        val crash = RuntimeException("gone")
        CrashRecorder.handler(store) { _, throwable -> handedOn = throwable }
            .uncaughtException(Thread.currentThread(), crash)

        assertSame(crash, handedOn)
        val records = store.records()
        assertEquals(1, records.size)
        assertEquals(CrashKind.CRASH, records.single().kind)
        assertEquals(listOf("java.lang.RuntimeException"), records.single().exceptionClasses)
    }

    @Test
    fun aBrokenStoreNeverStandsBetweenTheCrashAndAndroid() {
        val blocked = Files.createTempFile("crash", "file").toFile()
        var handedOn = false
        try {
            CrashRecorder.handler(CrashRecordStore(File(blocked, "below-a-file"))) { _, _ -> handedOn = true }
                .uncaughtException(Thread.currentThread(), RuntimeException())
        } finally {
            blocked.delete()
        }
        assertTrue(handedOn)
    }

    @Test
    fun theStoreKeepsTheLastTenAndOffersEachOnce() {
        val store = CrashRecordStore(context)
        repeat(12) { index ->
            store.record(CrashRecord(CrashKind.CRASH, timestampEpochMs = index.toLong(), appVersionCode = 1L))
        }
        assertEquals((2L..11L).toList(), store.records().map { it.timestampEpochMs })

        assertEquals(10, store.takeUnacknowledged().size)
        assertEquals(emptyList<CrashRecord>(), store.takeUnacknowledged())
        assertEquals(10, store.records().size)
    }

    @Test
    fun androidsAnrAndNativeCrashesAreAddedButOnlyAfterTheFirstLook() {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val store = CrashRecordStore(context)
        shadowOf(activityManager).addApplicationExitInfo(exit(ApplicationExitInfo.REASON_ANR, 100L))

        recordProcessExitReasons(context, store)
        assertEquals("the first look only sets the mark", emptyList<CrashRecord>(), store.records())

        shadowOf(activityManager).addApplicationExitInfo(exit(ApplicationExitInfo.REASON_ANR, 200L))
        shadowOf(activityManager).addApplicationExitInfo(exit(ApplicationExitInfo.REASON_CRASH_NATIVE, 300L))
        shadowOf(activityManager).addApplicationExitInfo(exit(ApplicationExitInfo.REASON_CRASH, 400L))
        shadowOf(activityManager).addApplicationExitInfo(exit(ApplicationExitInfo.REASON_USER_REQUESTED, 500L))
        recordProcessExitReasons(context, store)
        recordProcessExitReasons(context, store)

        assertEquals(
            listOf(CrashKind.NOT_RESPONDING to 200L, CrashKind.NATIVE_CRASH to 300L),
            store.records().map { it.kind to it.timestampEpochMs },
        )
    }

    @Test
    fun theNextStartWritesTheCrashIntoTheHistoryOnce() {
        CrashRecordStore(context).record(CrashRecord(CrashKind.CRASH, 5_000L, appVersionCode = 246L))

        assertEquals(1, takeCrashesToOffer(context).size)
        assertEquals(0, takeCrashesToOffer(context).size)
        val crashed = DiagnosticEventStore(context).snapshot().events
            .filter { it.operation == DiagnosticOperation.APP_CRASHED }
        assertEquals(listOf(5_000L), crashed.map { it.timestampEpochMs })
        assertEquals(DiagnosticLevel.ERROR, crashed.single().level)
    }

    @Test
    fun theTrayOffersTheReportOnceAndOpensDiagnostics() {
        CrashRecordStore(context).record(CrashRecord(CrashKind.CRASH, 5_000L, appVersionCode = 246L))
        val host = ViewportErrorHostState()
        var diagnosticsOpened = 0
        composeRule.setContent {
            SubshellTheme { CrashReportPrompt(noticeHost = host, onOpenDiagnostics = { diagnosticsOpened++ }) }
        }
        composeRule.waitUntil(5_000) { host.currentRequest != null }

        val request = requireNotNull(host.currentRequest)
        assertEquals(CRASH_REPORT_PROMPT_SCOPE, request.scopeKey)
        assertEquals(TrayTone.INFO, request.tone)
        assertEquals("subshell closed unexpectedly last time", request.presentation.title)
        assertEquals("Send report", request.presentation.primaryAction?.label)
        composeRule.runOnIdle { requireNotNull(request.onRetry).invoke() }
        composeRule.waitForIdle()

        assertEquals(1, diagnosticsOpened)
        assertNull(host.currentRequest)
        assertTrue(CrashRecordStore(context).records().all { it.acknowledged })
    }

    @Test
    fun withoutACrashThereIsNothingToSayAndAnAnrSaysSo() {
        assertNull(crashReportPromptPresentation(emptyList()))
        assertEquals(
            "subshell stopped responding last time",
            crashReportPromptPresentation(listOf(CrashRecord(CrashKind.NOT_RESPONDING, 1L, null)))?.title,
        )
    }

    private fun exit(reason: Int, timestamp: Long): ApplicationExitInfo =
        ApplicationExitInfoBuilder.newBuilder()
            .setReason(reason)
            .setTimestamp(timestamp)
            .setProcessName(context.packageName)
            .build()

    private fun environment() = DiagnosticReportEnvironment(
        appVersionName = "0.82-beta",
        appVersionCode = 246L,
        buildType = "release",
        debugSlot = "",
        androidApi = 36,
        androidRelease = "16",
        deviceManufacturer = "Google",
        deviceModel = "Pixel",
        screenWidthDp = 411,
        screenHeightDp = 891,
        smallestScreenWidthDp = 411,
    )
}
