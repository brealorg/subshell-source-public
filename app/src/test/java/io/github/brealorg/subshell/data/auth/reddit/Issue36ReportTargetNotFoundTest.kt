package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModerationFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditReportTargetResult
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.data.network.NetworkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE36_A_404_THREAD_IS_NOT_PROOF_OF_DELETION_V1 (#36, /code-review of
 * #281): a moderator report's target is read from its thread; a thread Reddit
 * does not know (404) is "not found", as an empty /api/info answer was, not
 * a generic HTTP error with Retry. Other statuses stay failures.
 */
class Issue36ReportTargetNotFoundTest {
    private val metadata = NetworkResponseMetadata(404, 0L, NetworkRateLimit(null, null, null), null)

    @Test
    fun a404ThreadIsNotFound() {
        assertEquals(
            AuthenticatedRedditReportTargetResult.NotFound,
            reportTargetResultOf(NetworkResult.HttpFailure(404, retryable = false, metadata = metadata)),
        )
    }

    @Test
    fun anotherStatusStaysAFailure() {
        val result = reportTargetResultOf(NetworkResult.HttpFailure(403, retryable = false, metadata = metadata.copy(statusCode = 403)))
        assertTrue("$result", result is AuthenticatedRedditReportTargetResult.Failed)
        assertEquals(AuthenticatedRedditModerationFailureKind.HTTP, (result as AuthenticatedRedditReportTargetResult.Failed).kind)
    }
}
