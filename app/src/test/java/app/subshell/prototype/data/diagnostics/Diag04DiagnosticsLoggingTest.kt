package app.subshell.prototype.data.diagnostics

import app.subshell.prototype.backup.toBackupFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.forbiddenReasonFromBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import app.subshell.prototype.data.network.reddit.RedditReadListingDto
import app.subshell.prototype.data.network.reddit.toNetworkPage
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DIAG04 (owner 2026-10-18, from the 0.82 diagnostic report review): events
 * say which action failed, which version the app came from, which post a
 * listing left out, the exception class of an unknown local failure and the
 * network a background job saw. Only fixed names, class names, field paths
 * and booleans are kept: no IDs, names, URLs or exception text.
 */
class Diag04DiagnosticsLoggingTest {
    private val directory: File = Files.createTempDirectory("diag04").toFile()
    private fun store() = DiagnosticEventStore(directory = directory, maxEvents = 50, maxBytes = 64 * 1024)

    private fun report(snapshot: DiagnosticSnapshot) = buildDiagnosticReport(
        snapshot = snapshot,
        environment = DiagnosticReportEnvironment(
            appVersionName = "0.83-beta",
            appVersionCode = 247,
            buildType = "debug",
            debugSlot = "",
            androidApi = 36,
            androidRelease = "16",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 6",
            screenWidthDp = 411,
            screenHeightDp = 914,
            smallestScreenWidthDp = 411,
        ),
        generatedAtEpochMs = 10_000L,
    )

    @After
    fun tearDown() {
        ListingSkipReports.installForTest(null)
        directory.deleteRecursively()
    }

    @Test
    fun theActionIsTheScopeKeysFixedNameWithoutItsIds() {
        assertEquals("post_vote", diagnosticActionForScope("reddit-post-vote:t3_abc123"))
        assertEquals("post_save", diagnosticActionForScope("reddit-post-save:t3_abc123"))
        assertEquals(
            "content_safety_set_post_hidden",
            diagnosticActionForScope("reddit-content-safety:t3_abc123:SET_POST_HIDDEN"),
        )
        assertEquals("home_feed", diagnosticActionForScope("home-feed:main|HOME|BEST|account-7"))
        assertEquals("post_comments", diagnosticActionForScope("post-comments:abc123:TOP"))
        assertEquals("inbox", diagnosticActionForScope("inbox:account-7"))
        assertEquals("post_edit", diagnosticActionForScope("post_edit"))
        assertEquals("inbox_background_sync", diagnosticActionForScope("inbox_background_sync"))
        assertEquals(
            "media_image_i_redd_it",
            diagnosticActionForScope(app.subshell.prototype.media.remoteMediaDiagnosticScope("media_image", "https://i.redd.it/abc.jpg")),
        )
        // A bare single word could be a community or user name; it is no action.
        assertNull(diagnosticActionForScope("pics"))
        assertNull(diagnosticActionForScope("t3_abc123:vote"))
    }

    @Test
    fun aFailedVoteIsToldApartFromAFailedSaveInTheReport() {
        val details = FailureDetails(httpStatusCode = 400, retryable = false)
        store().recordViewportFailure("reddit-post-vote:t3_secretid", details, nowEpochMs = 1_000L)
        store().recordViewportFailure("reddit-post-save:t3_secretid", details, nowEpochMs = 2_000L)

        val text = report(store().snapshot())
        assertTrue(text, text.contains("http=400") && text.contains(" action=post_vote"))
        assertTrue(text, text.contains(" action=post_save"))
        assertFalse(text, text.contains("secretid"))
    }

    @Test
    fun anUnknownLocalFailureNamesItsExceptionClassButNeverItsMessage() {
        val details = FailureDetails(localKind = LocalFailureKind.UNKNOWN)
            .withExceptionClass(IllegalStateException("token=zqsecret user u/someone"))
        store().recordBackgroundFailure("inbox_background_runtime", details, nowEpochMs = 1_000L)

        val event = store().snapshot().events.single()
        assertEquals("IllegalStateException", event.failure?.exceptionClass)
        val text = report(store().snapshot())
        assertTrue(text, text.contains(" local=unknown exception=IllegalStateException"))
        assertFalse(text, text.contains("someone"))
        assertFalse(text, text.contains("zqsecret"))
    }

    @Test
    fun aBackgroundFailureKeepsTheNetworkItSaw() {
        store().recordBackgroundFailure(
            scopeKey = "modmail_background_sync",
            details = FailureDetails(retryable = true),
            network = DiagnosticNetworkState(validated = false, metered = true, deviceIdle = true),
            nowEpochMs = 1_000L,
        )

        val failure = store().snapshot().events.single().failure!!
        assertEquals(false, failure.networkValidated)
        assertEquals(true, failure.networkMetered)
        assertEquals(true, failure.deviceIdle)
        val text = report(store().snapshot())
        assertTrue(text, text.contains("network_validated=false network_metered=true device_idle=true"))
    }

    @Test
    fun aBackgroundFailureReadsTheNetworkWhenTheCallerGaveNone() {
        val seeing = DiagnosticEventStore(
            directory = directory,
            maxEvents = 50,
            maxBytes = 64 * 1024,
            networkState = { DiagnosticNetworkState(validated = false, metered = false, deviceIdle = true) },
        )
        seeing.recordBackgroundFailure("inbox_background_sync", FailureDetails(retryable = true), nowEpochMs = 1_000L)

        val failure = store().snapshot().events.single().failure!!
        assertEquals(false, failure.networkValidated)
        assertEquals(true, failure.deviceIdle)
    }

    @Test
    fun theBackgroundAndBackupClassifiersNameAnUnexpectedExceptionsClass() {
        val background = app.subshell.prototype.inbox.backgroundSyncFailureDetails(UnsupportedOperationException("secret"))
        assertEquals(LocalFailureKind.UNKNOWN, background.localKind)
        assertEquals("UnsupportedOperationException", background.exceptionClass)
        // A classified failure needs no class name.
        assertNull(app.subshell.prototype.inbox.backgroundSyncFailureDetails(java.io.IOException("x")).exceptionClass)

        assertEquals("ArithmeticException", ArithmeticException("secret").toBackupFailureDetails().exceptionClass)
    }

    @Test
    fun theFirstStartOnANewVersionRecordsWhereTheAppCameFrom() {
        store().recordAppSessionStarted(versionCode = 246, nowEpochMs = 1_000L)
        store().recordAppSessionStarted(versionCode = 246, nowEpochMs = 2_000L)
        assertTrue(store().snapshot().events.none { it.operation == DiagnosticOperation.APP_UPDATED })

        store().recordAppSessionStarted(versionCode = 247, nowEpochMs = 3_000L)
        val updated = store().snapshot().events.single { it.operation == DiagnosticOperation.APP_UPDATED }
        assertEquals(246L, updated.appVersionFrom)
        assertEquals(247L, updated.appVersionTo)
        assertEquals(3, store().snapshot().events.count { it.operation == DiagnosticOperation.APP_SESSION_STARTED })
        val text = report(store().snapshot())
        assertTrue(text, text.contains("operation=app_updated outcome=observed from_version_code=246 to_version_code=247"))
    }

    @Test
    fun aListingThatLeavesOutAPostSaysWhichFieldOncePerProcess() {
        val recorded = mutableListOf<String?>()
        ListingSkipReports.installForTest { path -> recorded += path }
        val json = SubshellNetworkJson.providerJson
        val page = """
            {"kind": "Listing", "data": {"children": [
              {"kind": "t3", "data": {"id": "one", "title": "First"}},
              {"kind": "t3", "data": {"id": "bad", "title": "Broken", "poll_data": [1, 2]}}
            ]}}
        """.trimIndent()
        repeat(2) {
            json.decodeFromString(RedditReadListingDto.serializer(), page).toNetworkPage(json, 1_700_000_000L)
        }

        assertEquals(1, recorded.size)
        val path = recorded.single()!!
        assertTrue(path, path.startsWith("$.data.children[1]"))
        assertFalse(path, path.contains("bad"))

        store().recordListingPostSkipped(path, nowEpochMs = 1_000L)
        val event = store().snapshot().events.single()
        assertEquals(DiagnosticOperation.LISTING_POST_SKIPPED, event.operation)
        assertEquals(path, event.failure?.jsonPath)
    }

    @Test
    fun everyNewFieldReadsBackAndOlderLinesStillRead() {
        store().recordBackgroundFailure(
            scopeKey = "inbox_background_sync",
            details = FailureDetails(localKind = LocalFailureKind.UNKNOWN, exceptionClass = "IOException"),
            network = DiagnosticNetworkState(validated = true, metered = false, deviceIdle = false),
            nowEpochMs = 1_000L,
        )
        store().recordAppSessionStarted(versionCode = 5, nowEpochMs = 2_000L)
        store().recordAppSessionStarted(versionCode = 6, nowEpochMs = 3_000L)
        val written = store().snapshot().events
        val reread = DiagnosticEventStore(directory = directory, maxEvents = 50, maxBytes = 64 * 1024).snapshot()
        assertEquals(written, reread.events)
        assertEquals(0, reread.invalidRecordCount)

        // A 17-field line (DIAG03) and a 16-field line (before it) still read, without the new fields.
        val log = File(directory, "events-v1.log")
        val lines = log.readLines()
        val failureLine = lines.first { it.startsWith("E|") }
        val fields = failureLine.split('|')
        log.writeText(
            listOf(
                lines.first(),
                fields.take(17).joinToString("|"),
                fields.take(16).joinToString("|"),
            ).joinToString("\n", postfix = "\n"),
        )
        val legacy = store().snapshot()
        assertEquals(0, legacy.invalidRecordCount)
        assertEquals(2, legacy.events.size)
        legacy.events.forEach { event ->
            assertEquals("unknown", event.failure?.localKind)
            assertNull(event.action)
            assertNull(event.failure?.exceptionClass)
            assertNull(event.failure?.networkValidated)
        }
    }

    @Test
    fun aRefusedRequestKeepsRedditsCodeButNotItsMessage() {
        assertEquals(
            "too_old",
            forbiddenReasonFromBody("""{"json": {"errors": [["TOO_OLD", "that's a piece of history now", "parent"]]}}"""),
        )
        assertEquals("thread_locked", forbiddenReasonFromBody("""{"error": "THREAD_LOCKED"}"""))
        assertEquals("quarantined", forbiddenReasonFromBody("""{"reason": "quarantined"}"""))
        assertNull(forbiddenReasonFromBody("""{"message": "Bad Request", "error": 400}"""))
        assertNull(forbiddenReasonFromBody("""{"error": "a sentence with spaces"}"""))
    }

    @Test
    fun aRefusedWriteCarriesRedditsCode() = kotlinx.coroutines.runBlocking {
        val client = SubshellHttpClient(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(400).message("Bad Request")
                    .body("""{"json": {"errors": [["TOO_OLD", "old", "parent"]]}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test/1.0",
        )
        val result = client.postFormStatus(FormPostRequest(listOf("api", "vote"), listOf("dir" to "1", "id" to "t3_x")))

        assertEquals("too_old", (result as NetworkResult.HttpFailure).providerReason)
        assertEquals("too_old", result.failureDetailsOrNull()?.providerCode?.value)
    }

    @Test
    fun everyScopeKeyTheAppWritesHasAnAction() {
        val sourceRoot = File("src/main/java")
        val literal = Regex("""(?:scopeKey|diagnosticScopeKey)\s*=\s*"([^"]+)"""")
        val keys = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> literal.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue("found ${keys.size}", keys.size >= 20)
        val missing = keys.filter { key -> diagnosticActionForScope(key.substringBefore('$')) == null }
        assertTrue("scope keys without an action: $missing", missing.isEmpty())
    }
}
