package app.subshell.prototype.data.diagnostics

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Diag01A2CoveragePolicyTest {
    @Test
    fun errorPresentationRetainsTypedFailureForDiagnosticBridge() {
        val details = FailureDetails(
            httpStatusCode = 503,
            retryable = true,
            transportKind = NetworkTransportFailureKind.TIMEOUT,
        )
        val presentation = ErrorPresentation(
            kind = ErrorPresentationKind.BLOCKING,
            title = "Couldn't load",
            message = "A typed failure is available.",
        ).withFailureDetails(details)

        assertEquals(details, presentation.failureDetails)
        assertEquals("HTTP 503 · Network: Timeout", presentation.technicalDetail)
    }

    @Test
    fun presentedErrorsUseImpactAwareSeverityAndPreciseSubsystems() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 8192,
        )

        store.recordPresentedFailure(
            scopeKey = "search_posts",
            presentationKind = DiagnosticPresentationKind.BLOCKING,
            details = FailureDetails(retryable = true),
            nowEpochMs = 1_000L,
        )
        store.recordPresentedFailure(
            scopeKey = "post_create",
            presentationKind = DiagnosticPresentationKind.INLINE,
            details = FailureDetails(retryable = false),
            nowEpochMs = 2_000L,
        )
        store.recordPresentedFailure(
            scopeKey = "community_feed",
            presentationKind = DiagnosticPresentationKind.DEGRADED,
            details = FailureDetails(retryable = false),
            nowEpochMs = 3_000L,
        )

        val events = store.snapshot().events
        assertEquals(DiagnosticSubsystem.SEARCH, events[0].subsystem)
        assertEquals(DiagnosticOperation.BLOCKING_ERROR_PRESENTED, events[0].operation)
        assertEquals(DiagnosticLevel.ERROR, events[0].level)
        assertEquals(DiagnosticSubsystem.POSTS, events[1].subsystem)
        assertEquals(DiagnosticOperation.INLINE_ERROR_PRESENTED, events[1].operation)
        assertEquals(DiagnosticLevel.ERROR, events[1].level)
        assertEquals(DiagnosticSubsystem.COMMUNITY, events[2].subsystem)
        assertEquals(DiagnosticOperation.DEGRADED_ERROR_PRESENTED, events[2].operation)
        assertEquals(DiagnosticLevel.WARNING, events[2].level)
    }

    @Test
    fun featureSurfaceWinsOverEmbeddedAuthAccountContextInCompositeScope() {
        assertEquals(
            DiagnosticSubsystem.COMMENTS,
            diagnosticSubsystemForScope("post-comments:t3_runtime_probe:best"),
        )
        assertEquals(
            DiagnosticSubsystem.COMMENTS,
            diagnosticSubsystemForScope("post-comments:account:authenticated:new"),
        )
        assertEquals(
            DiagnosticSubsystem.FEED,
            diagnosticSubsystemForScope("home-feed:account:authenticated:frontpage"),
        )
        assertEquals(
            DiagnosticSubsystem.SEARCH,
            diagnosticSubsystemForScope("search:account:authenticated:query"),
        )
        assertEquals(
            DiagnosticSubsystem.BACKUP,
            diagnosticSubsystemForScope("backup:account:settings"),
        )
        assertEquals(
            DiagnosticSubsystem.SETTINGS,
            diagnosticSubsystemForScope("settings:account:notifications"),
        )
        assertEquals(
            DiagnosticSubsystem.BACKUP,
            diagnosticSubsystemForScope("account:backup:settings"),
        )
        assertEquals(
            DiagnosticSubsystem.FEED,
            diagnosticSubsystemForScope("account:home-feed:authenticated:frontpage"),
        )
        assertEquals(
            DiagnosticSubsystem.SEARCH,
            diagnosticSubsystemForScope("oauth:search:query"),
        )
        assertEquals(
            DiagnosticSubsystem.AUTH,
            diagnosticSubsystemForScope("oauth_account_refresh"),
        )
    }

    @Test
    fun repeatedPresentedFailureIsDeduplicatedInsideShortWindow() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 8192,
        )
        val details = FailureDetails(
            retryable = true,
            transportKind = NetworkTransportFailureKind.TIMEOUT,
        )

        store.recordPresentedFailure(
            scopeKey = "home_feed",
            presentationKind = DiagnosticPresentationKind.BLOCKING,
            details = details,
            nowEpochMs = 1_000L,
        )
        store.recordPresentedFailure(
            scopeKey = "home_feed",
            presentationKind = DiagnosticPresentationKind.BLOCKING,
            details = details,
            nowEpochMs = 2_000L,
        )

        assertEquals(1, store.snapshot().events.size)
    }

    @Test
    fun backgroundAndNotificationFailuresAreStructuredAndScoped() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 8192,
        )

        store.recordBackgroundFailure(
            scopeKey = "inbox_background_sync",
            details = FailureDetails(
                retryable = true,
                transportKind = NetworkTransportFailureKind.TIMEOUT,
            ),
            nowEpochMs = 1_000L,
        )
        store.recordBackgroundFailure(
            scopeKey = "modmail_background_state",
            details = FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.STORAGE,
            ),
            nowEpochMs = 2_000L,
        )
        store.recordBackgroundAuthorizationRequired(
            scopeKey = "inbox_background_authorization",
            nowEpochMs = 3_000L,
        )
        store.recordNotificationPostFailure(
            scopeKey = "modmail_notification_post",
            details = FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.PERMISSION,
            ),
            nowEpochMs = 4_000L,
        )

        val events = store.snapshot().events
        assertEquals(DiagnosticSubsystem.INBOX, events[0].subsystem)
        assertEquals(DiagnosticOperation.BACKGROUND_SYNC_FAILED, events[0].operation)
        assertEquals(DiagnosticLevel.WARNING, events[0].level)
        assertEquals(DiagnosticSubsystem.MODMAIL, events[1].subsystem)
        assertEquals(DiagnosticLevel.ERROR, events[1].level)
        assertEquals(DiagnosticSubsystem.INBOX, events[2].subsystem)
        assertEquals(DiagnosticOperation.BACKGROUND_AUTHORIZATION_REQUIRED, events[2].operation)
        assertEquals(DiagnosticLevel.WARNING, events[2].level)
        assertEquals(DiagnosticSubsystem.MODMAIL, events[3].subsystem)
        assertEquals(DiagnosticOperation.NOTIFICATION_POST_FAILED, events[3].operation)
        assertEquals(DiagnosticLevel.ERROR, events[3].level)
    }

    @Test
    fun newCoveragePathsStillExcludeRawScopeAndProviderMessage() = withTempDirectory { directory ->
        val store = DiagnosticEventStore(
            directory = directory,
            maxEvents = 20,
            maxBytes = 8192,
        )
        val rawScope = "search-private-query-user-content"
        val providerMessage = "private reddit provider body"
        val details = FailureDetails(retryable = true).withProviderDetails(
            code = "RATE_LIMIT",
            message = providerMessage,
        )

        store.recordPresentedFailure(
            scopeKey = rawScope,
            presentationKind = DiagnosticPresentationKind.INLINE,
            details = details,
            nowEpochMs = 1_000L,
        )

        val persisted = File(directory, "events-v1.log").readText()
        assertFalse(persisted.contains(rawScope))
        assertFalse(persisted.contains(providerMessage))
        assertEquals("RATE_LIMIT", store.snapshot().events.single().failure?.providerCode)
    }

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("subshell-diag01a2").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
