package app.subshell.prototype.data.error

import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.ui.error.ErrorAction
import app.subshell.prototype.ui.error.ErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (error01a1, error01a221): text a provider sends is cut to a bounded
 * length (a code shorter than a message, never more than 512 characters) and
 * credentials in it are redacted; an error presentation is plain data with no
 * callbacks in it; and a failure with nothing but its retryability has no
 * details worth opening.
 */
class Step3SafeFailureTextTest {
    private val long = "x".repeat(2_000)

    @Test
    fun providerTextIsCutToABoundedLength() {
        val message = requireNotNull(SafeFailureText.fromUntrusted(long)).value
        val widest = requireNotNull(SafeFailureText.fromUntrusted(long, maxLength = 512)).value
        val code = requireNotNull(FailureDetails().withProviderDetails(code = long, message = long).providerCode).value

        assertEquals(240, message.length)
        assertEquals(512, widest.length)
        assertEquals(80, code.length)
        assertTrue(runCatching { SafeFailureText.fromUntrusted(long, maxLength = 513) }.isFailure)
    }

    @Test
    fun clientSecretsCodesAndCookiesAreRedacted() {
        val text = requireNotNull(
            SafeFailureText.fromUntrusted("client_secret=s3cr3t authorization_code=abc123 cookie=reddit_session"),
        ).value

        assertFalse(text, text.contains("s3cr3t"))
        assertFalse(text, text.contains("abc123"))
        assertFalse(text, text.contains("reddit_session"))
        assertTrue(text, text.contains("client_secret=[redacted]"))
    }

    @Test
    fun anErrorPresentationHoldsNoCallbacks() {
        val functionFields = listOf(ErrorPresentation::class.java, ErrorAction::class.java)
            .flatMap { type -> type.declaredFields.toList() }
            .filter { field -> Function::class.java.isAssignableFrom(field.type) }
            .map { field -> "${field.declaringClass.simpleName}.${field.name}" }

        assertEquals(emptyList<String>(), functionFields)
    }

    @Test
    fun aFailureWithOnlyItsRetryabilityHasNoDetailsWorthOpening() {
        assertFalse(FailureDetails(retryable = true).hasMeaningfulExpandedDetails())
        assertFalse(FailureDetails(retryable = true, transportKind = NetworkTransportFailureKind.TIMEOUT).hasMeaningfulExpandedDetails())
        assertTrue(FailureDetails(retryable = true, httpStatusCode = 429).hasMeaningfulExpandedDetails())
    }
}
