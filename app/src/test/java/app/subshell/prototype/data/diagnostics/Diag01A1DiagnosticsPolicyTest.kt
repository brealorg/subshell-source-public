package app.subshell.prototype.data.diagnostics

import app.subshell.prototype.data.error.FailureDetails
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Diag01A1DiagnosticsPolicyTest {
    @Test
    fun boundedBufferEvictsOldestAndCountsDrops() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 3,
            maxBytes = 4096,
        )
        val events = (1L..4L).map { index ->
            DiagnosticEvents.appSessionStarted(nowEpochMs = index)
        }
        events.forEach(store::record)

        val snapshot = store.snapshot()
        assertEquals(3, snapshot.events.size)
        assertEquals(events.drop(1).map { it.id }, snapshot.events.map { it.id })
        assertEquals(1L, snapshot.droppedEventCount)
        assertTrue(snapshot.bytesUsed in 1L..4096L)
    }

    @Test
    fun repeatedViewportFailureIsDeduplicatedInsideShortWindow() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 4096,
        )
        val details = FailureDetails(httpStatusCode = 503, retryable = true)
        store.recordViewportFailure(
            scopeKey = "feed-home",
            details = details,
            nowEpochMs = 1_000L,
        )
        store.recordViewportFailure(
            scopeKey = "feed-home",
            details = details,
            nowEpochMs = 2_000L,
        )

        assertEquals(1, store.snapshot().events.size)
    }

    @Test
    fun persistenceNeverStoresRawScopeProviderMessageOrCredentials() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 8192,
        )
        val secretBearer = "diag-super-secret-bearer"
        val secretAccessToken = "diag-super-secret-access"
        val privateProviderMessage = "private reddit body should never persist"
        val rawScope = "inbox-user-private-target-123"
        val details = FailureDetails(retryable = false).withProviderDetails(
            code = "Authorization: Bearer $secretBearer access_token=$secretAccessToken",
            message = privateProviderMessage,
        )

        store.recordViewportFailure(
            scopeKey = rawScope,
            details = details,
            nowEpochMs = 10_000L,
        )

        val persisted = File(directory, "events-v1.log").readText()
        listOf(
            secretBearer,
            secretAccessToken,
            privateProviderMessage,
            rawScope,
        ).forEach { forbidden ->
            assertFalse("Diagnostics persistence leaked $forbidden", persisted.contains(forbidden))
        }
        val event = store.snapshot().events.single()
        assertEquals(DiagnosticSubsystem.INBOX, event.subsystem)
        assertEquals(16, event.scopeFingerprint?.length)
        val report = buildDiagnosticReport(
            snapshot = store.snapshot(),
            environment = DiagnosticReportEnvironment(
                appVersionName = "test",
                appVersionCode = 1,
                buildType = "debug",
                debugSlot = "dev2",
                androidApi = 37,
                androidRelease = "17",
                deviceManufacturer = "test",
                deviceModel = "test",
                screenWidthDp = 1,
                screenHeightDp = 1,
                smallestScreenWidthDp = 1,
            ),
        )
        assertFalse(report.contains(secretBearer))
        assertFalse(report.contains(secretAccessToken))
        assertFalse(report.contains(privateProviderMessage))
        assertFalse(report.contains(rawScope))
    }

    @Test
    fun scopeFingerprintIsInstallLocalStableAndClearDoesNotRotateIt() = withTempDirectory { first ->
        withTempDirectory { second ->
            val firstStore = DiagnosticEventStore(first, maxEvents = 20, maxBytes = 8192)
            val secondStore = DiagnosticEventStore(second, maxEvents = 20, maxBytes = 8192)
            firstStore.recordViewportFailure("inbox-same-private-scope", null, 1_000L)
            val firstFingerprint = firstStore.snapshot().events.single().scopeFingerprint
            firstStore.clear()
            firstStore.recordViewportFailure("inbox-same-private-scope", null, 2_000L)
            val afterClearFingerprint = firstStore.snapshot().events.single().scopeFingerprint
            secondStore.recordViewportFailure("inbox-same-private-scope", null, 3_000L)
            val secondInstallFingerprint = secondStore.snapshot().events.single().scopeFingerprint

            assertEquals(firstFingerprint, afterClearFingerprint)
            assertTrue(firstFingerprint != secondInstallFingerprint)
        }
    }

    @Test
    fun reportContainsOnlyStructuredFailureFieldsAndPrivacyDeclaration() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 8192,
        )
        val privateMessage = "private provider response body"
        val details = FailureDetails(httpStatusCode = 429, retryable = true)
            .withProviderDetails(code = "RATELIMIT", message = privateMessage)
        store.recordViewportFailure(
            scopeKey = "comments-thread-private-id",
            details = details,
            nowEpochMs = 20_000L,
        )

        val report = buildDiagnosticReport(
            snapshot = store.snapshot(),
            environment = DiagnosticReportEnvironment(
                appVersionName = "1.2.3",
                appVersionCode = 123,
                buildType = "debug",
                debugSlot = "dev2",
                androidApi = 37,
                androidRelease = "17",
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                screenWidthDp = 800,
                screenHeightDp = 1280,
                smallestScreenWidthDp = 800,
            ),
            generatedAtEpochMs = 30_000L,
        )

        assertTrue(report.contains("http=429"))
        assertTrue(report.contains("provider_code=RATELIMIT"))
        assertTrue(report.contains("raw_content=not_collected"))
        assertTrue(report.contains("oauth_tokens=not_collected"))
        assertFalse(report.contains(privateMessage))
        assertFalse(report.contains("comments-thread-private-id"))
    }

    @Test
    fun retryableViewportFailuresAreWarningsAndTerminalFailuresAreErrors() {
        val retryable = DiagnosticEvents.viewportFailure(
            subsystem = DiagnosticSubsystem.FEED,
            scopeFingerprint = "0123456789abcdef",
            details = FailureDetails(httpStatusCode = 503, retryable = true),
            nowEpochMs = 1L,
        )
        val unknownRetryability = DiagnosticEvents.viewportFailure(
            subsystem = DiagnosticSubsystem.NETWORK,
            scopeFingerprint = "0123456789abcdef",
            details = null,
            nowEpochMs = 2L,
        )
        val terminal = DiagnosticEvents.viewportFailure(
            subsystem = DiagnosticSubsystem.AUTH,
            scopeFingerprint = "0123456789abcdef",
            details = FailureDetails(retryable = false),
            nowEpochMs = 3L,
        )

        assertEquals(DiagnosticLevel.WARNING, retryable.level)
        assertEquals(DiagnosticLevel.WARNING, unknownRetryability.level)
        assertEquals(DiagnosticLevel.ERROR, terminal.level)
        assertEquals(DiagnosticLevel.INFO, DiagnosticEvents.appSessionStarted(4L).level)
    }

    @Test
    fun supportDestinationsAreOptionalAndGitHubPrefillTargetsCanonicalBugForm() {
        val none = DiagnosticSupportConfiguration()
        assertFalse(none.hasCanonicalDestinations)

        val product = SubshellDiagnosticSupport.configuration
        assertTrue(product.hasCanonicalDestinations)
        assertEquals("brealorg", product.githubRepository?.owner)
        assertEquals("subshell", product.githubRepository?.name)

        val configured = DiagnosticSupportConfiguration(
            emailAddress = "support@example.test",
            githubRepository = DiagnosticGitHubRepository(
                owner = "brealorg",
                name = "subshell",
            ),
        )
        assertTrue(configured.hasCanonicalDestinations)

        val url = buildPrefilledDiagnosticGitHubIssueUrl(
            repository = requireNotNull(configured.githubRepository),
            environment = DiagnosticReportEnvironment(
                appVersionName = "1.2.3",
                appVersionCode = 123,
                buildType = "debug",
                debugSlot = "dev2",
                androidApi = 37,
                androidRelease = "17",
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                screenWidthDp = 411,
                screenHeightDp = 914,
                smallestScreenWidthDp = 411,
            ),
        )

        assertTrue(url.startsWith("https://github.com/brealorg/subshell/issues/new?"))
        assertTrue(url.contains("template=bug_report.yml"))
        assertTrue(url.contains("version=1.2.3%20%28123%29"))
        assertTrue(url.contains("android=Android%2017%20%28API%2037%29"))
        assertTrue(url.contains("device=Google%20Pixel"))
        assertTrue(url.contains("additional=A%20sanitized%20diagnostic%20report"))
        assertTrue(url.contains("Screen%3A%20411%20%C3%97%20914%20dp"))
        assertFalse(url.contains("events-v1.log"))
        assertFalse(url.contains("scope="))
        assertFalse(url.contains("provider_code="))
    }

    @Test
    fun clipboardPayloadUsesTheEntireStagedReportText() {
        val fullReport = buildString {
            appendLine("Subshell diagnostic report")
            repeat(6_000) { append('x') }
            appendLine()
            append("tail-marker")
        }
        val staged = StagedDiagnosticReport(
            file = File("unused-diagnostic-report.txt"),
            text = fullReport,
        )

        val clipboardText = diagnosticReportClipboardText(staged)

        assertEquals(fullReport, clipboardText)
        assertTrue(clipboardText.endsWith("tail-marker"))
    }

    @Test
    fun malformedPersistedRecordIsIgnoredRatherThanProjected() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 8192,
        )
        store.record(DiagnosticEvents.appSessionStarted(nowEpochMs = 40_000L))
        File(directory, "events-v1.log").appendText(
            "E|malformed|credential=should-never-project\n",
        )

        val snapshot = store.snapshot()
        assertEquals(1, snapshot.events.size)
        assertEquals(1, snapshot.invalidRecordCount)
        val report = buildDiagnosticReport(
            snapshot = snapshot,
            environment = DiagnosticReportEnvironment(
                appVersionName = "test",
                appVersionCode = 1,
                buildType = "debug",
                debugSlot = "dev2",
                androidApi = 37,
                androidRelease = "17",
                deviceManufacturer = "test",
                deviceModel = "test",
                screenWidthDp = 1,
                screenHeightDp = 1,
                smallestScreenWidthDp = 1,
            ),
        )
        assertFalse(report.contains("should-never-project"))
    }

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("subshell-diag01a1").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
