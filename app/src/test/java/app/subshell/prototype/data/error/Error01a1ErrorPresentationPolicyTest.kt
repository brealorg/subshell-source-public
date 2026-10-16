package app.subshell.prototype.data.error

import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Error01a1ErrorPresentationPolicyTest {
    @Test
    fun networkHttpFailurePreservesSafeStatusRetryAndRetryability() {
        val metadata = NetworkResponseMetadata(
            statusCode = 429,
            receivedAtEpochMs = 1_000L,
            rateLimit = NetworkRateLimit(
                remaining = 0.0,
                used = 100.0,
                resetAfterMs = 18_000L,
            ),
            retryAfterMs = 18_000L,
        )
        val details = NetworkResult.HttpFailure(
            statusCode = 429,
            retryable = true,
            metadata = metadata,
        ).failureDetailsOrNull()

        requireNotNull(details)
        assertEquals(429, details.httpStatusCode)
        assertEquals(18_000L, details.retryAfterMs)
        assertEquals(true, details.retryable)
        assertEquals("HTTP 429 · Retry after 18 s", details.toSafeTechnicalDetail())
    }

    @Test
    fun transportAndProtocolFailuresRemainTyped() {
        val transport = NetworkResult.TransportFailure(
            kind = NetworkTransportFailureKind.TIMEOUT,
            retryable = true,
        ).failureDetailsOrNull()
        val protocol = NetworkResult.ProtocolFailure(
            kind = NetworkProtocolFailureKind.MALFORMED_JSON,
        ).failureDetailsOrNull()

        assertEquals(NetworkTransportFailureKind.TIMEOUT, transport?.transportKind)
        assertEquals("Network: Timeout", transport?.toSafeTechnicalDetail())
        assertEquals(NetworkProtocolFailureKind.MALFORMED_JSON, protocol?.protocolKind)
        assertEquals("Response: Malformed response", protocol?.toSafeTechnicalDetail())
        assertNull(NetworkResult.Success("ok", successMetadata()).failureDetailsOrNull())
    }

    @Test
    fun untrustedProviderTextRedactsCredentialsAndFlattensControls() {
        val raw = "Authorization: Bearer top-secret\naccess_token=abc123 state=oauth-state Cookie: session-cookie"
        val safe = SafeFailureText.fromUntrusted(raw)?.value.orEmpty()

        assertFalse(safe.contains("top-secret"))
        assertFalse(safe.contains("abc123"))
        assertFalse(safe.contains("oauth-state"))
        assertFalse(safe.contains("session-cookie"))
        assertFalse(safe.contains('\n'))
        assertTrue(safe.contains("[redacted]"))
    }

    @Test
    fun providerDetailsAreBoundedAndOnlyAcceptSanitizedText() {
        val details = FailureDetails(httpStatusCode = 403).withProviderDetails(
            code = "NOT_ALLOWED",
            message = "refresh_token=secret-value Provider rejected the request",
        )

        assertEquals("NOT_ALLOWED", details.providerCode?.value)
        assertFalse(details.providerMessage?.value.orEmpty().contains("secret-value"))
        assertTrue(details.toSafeTechnicalDetail().orEmpty().contains("Provider code NOT_ALLOWED"))
    }

    @Test
    fun presentationCarriesSemanticsButNoCallbacks() {
        val action = ErrorAction(
            kind = ErrorActionKind.RETRY,
            label = "Retry",
        )
        val presentation = ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = "Couldn't refresh comments",
            message = "Saved comments are still visible.",
            primaryAction = action,
        ).withFailureDetails(
            FailureDetails(
                httpStatusCode = 429,
                retryAfterMs = 18_000L,
                retryable = true,
            ),
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
        assertEquals("HTTP 429 · Retry after 18 s", presentation.technicalDetail)
    }

    private fun successMetadata() = NetworkResponseMetadata(
        statusCode = 200,
        receivedAtEpochMs = 1_000L,
        rateLimit = NetworkRateLimit(
            remaining = null,
            used = null,
            resetAfterMs = null,
        ),
        retryAfterMs = null,
    )
}
