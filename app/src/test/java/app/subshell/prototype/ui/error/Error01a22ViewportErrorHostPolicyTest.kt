package app.subshell.prototype.ui.error

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.hasMeaningfulExpandedDetails
import app.subshell.prototype.data.error.toSafeDetailItems
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Error01a22ViewportErrorHostPolicyTest {
    @Test
    fun degradedErrorPersistsUntilDismissedOrCleared() {
        val host = ViewportErrorHostState()
        var retries = 0
        val presentation = degradedPresentation()
        val details = FailureDetails(
            transportKind = NetworkTransportFailureKind.TIMEOUT,
            retryable = true,
        )

        host.present(
            scopeKey = "home",
            presentation = presentation,
            failureDetails = details,
            onRetry = { retries += 1 },
        )

        assertEquals("home", host.currentRequest?.scopeKey)
        assertFalse(host.detailsVisible)

        host.openDetails()
        assertFalse(host.detailsVisible)

        host.retryCurrent()
        assertEquals(1, retries)

        host.dismissCurrent()
        assertNull(host.currentRequest)
        assertFalse(host.detailsVisible)

        host.present(
            scopeKey = "home",
            presentation = presentation,
            failureDetails = details,
            onRetry = { retries += 1 },
        )
        assertNull(host.currentRequest)

        host.clear("home")
        host.present(
            scopeKey = "home",
            presentation = presentation,
            failureDetails = details,
            onRetry = { retries += 1 },
        )
        assertEquals("home", host.currentRequest?.scopeKey)
    }

    @Test
    fun clearingDisposedScopeCannotRemoveNewerVisibleError() {
        val host = ViewportErrorHostState()
        host.present(
            scopeKey = "home",
            presentation = degradedPresentation(title = "Home failed"),
            failureDetails = null,
            onRetry = {},
        )
        val inbox = degradedPresentation(title = "Inbox failed")
        host.present(
            scopeKey = "inbox",
            presentation = inbox,
            failureDetails = null,
            onRetry = {},
        )

        host.clear("home")

        assertEquals("inbox", host.currentRequest?.scopeKey)
        assertSame(inbox, host.currentRequest?.presentation)
    }


    @Test
    fun safeDetailItemsPreserveStructuredNetworkMetadata() {
        val details = FailureDetails(
            httpStatusCode = 429,
            transportKind = NetworkTransportFailureKind.TIMEOUT,
            retryAfterMs = 18_000,
            retryable = true,
        )

        val items = details.toSafeDetailItems()

        assertTrue(items.any { it.label == "HTTP status" && it.value == "429" })
        assertTrue(items.any { it.label == "Network" && it.value == "Timeout" })
        assertTrue(items.any { it.label == "Retry after" && it.value == "18 seconds" })
        assertTrue(items.any { it.label == "Retryable" && it.value == "Yes" })
    }

    @Test
    fun simpleTransportFailuresDoNotExposeRedundantDetails() {
        val timeout = FailureDetails(
            transportKind = NetworkTransportFailureKind.TIMEOUT,
            retryable = true,
        )
        val dns = FailureDetails(
            transportKind = NetworkTransportFailureKind.DNS,
            retryable = true,
        )

        assertFalse(timeout.hasMeaningfulExpandedDetails())
        assertFalse(dns.hasMeaningfulExpandedDetails())

        val host = ViewportErrorHostState()
        host.present(
            scopeKey = "comments",
            presentation = degradedPresentation(),
            failureDetails = timeout,
            onRetry = {},
        )
        host.openDetails()

        assertFalse(host.detailsVisible)
    }

    @Test
    fun richerHttpProviderRetryAndProtocolFailuresExposeDetails() {
        val http = FailureDetails(httpStatusCode = 429, retryable = true)
        val retryAfter = FailureDetails(retryAfterMs = 18_000, retryable = true)
        val provider = FailureDetails(retryable = false).withProviderDetails(
            code = "BAD_SR_NAME",
            message = "That community name is invalid",
        )
        val protocol = FailureDetails(
            protocolKind = NetworkProtocolFailureKind.MALFORMED_JSON,
            retryable = false,
        )

        assertTrue(http.hasMeaningfulExpandedDetails())
        assertTrue(retryAfter.hasMeaningfulExpandedDetails())
        assertTrue(provider.hasMeaningfulExpandedDetails())
        assertTrue(protocol.hasMeaningfulExpandedDetails())

        val host = ViewportErrorHostState()
        host.present(
            scopeKey = "home",
            presentation = degradedPresentation(),
            failureDetails = http,
            onRetry = {},
        )
        host.openDetails()

        assertTrue(host.detailsVisible)
    }

    @Test
    fun retryCallbackIsDroppedWhenPresentationHasNoRetryAction() {
        val host = ViewportErrorHostState()
        var retries = 0
        val presentation = degradedPresentation().copy(primaryAction = null)

        host.present(
            scopeKey = "home",
            presentation = presentation,
            failureDetails = null,
            onRetry = { retries += 1 },
        )
        host.retryCurrent()

        assertEquals(0, retries)
        assertNull(host.currentRequest?.onRetry)
    }

    private fun degradedPresentation(
        title: String = "Couldn't refresh feed",
    ) = ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = title,
        message = "Saved content remains visible.",
        technicalDetail = "Network: Timeout",
        primaryAction = ErrorAction(
            kind = ErrorActionKind.RETRY,
            label = "Retry",
        ),
    )
}
