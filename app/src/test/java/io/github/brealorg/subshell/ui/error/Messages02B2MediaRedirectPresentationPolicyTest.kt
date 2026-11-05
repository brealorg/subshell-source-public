package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02B2MediaRedirectPresentationPolicyTest {
    @Test
    fun unresolved308IsPresentedAsRedirectNotRejection() {
        val presentation = remoteMediaErrorPresentation(
            kind = RemoteMediaKind.IMAGE,
            details = FailureDetails(
                httpStatusCode = 308,
                retryable = false,
            ),
        )

        assertTrue(presentation.message.contains("redirected", ignoreCase = true))
        assertTrue(!presentation.message.contains("rejected", ignoreCase = true))
    }

    @Test
    fun blockedRedirectExplainsSecureMediaBoundary() {
        val presentation = remoteMediaErrorPresentation(
            kind = RemoteMediaKind.IMAGE,
            details = FailureDetails(
                httpStatusCode = 308,
                retryable = false,
                localKind = LocalFailureKind.SECURITY,
            ),
        )

        assertTrue(presentation.message.contains("blocked", ignoreCase = true))
        assertTrue(presentation.message.contains("secure", ignoreCase = true))
    }
}
