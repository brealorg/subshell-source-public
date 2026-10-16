package app.subshell.prototype.data.error

import app.subshell.prototype.data.account.RedditCommentUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiFailureKind
import app.subshell.prototype.data.account.RedditFeedUiFailureKind
import app.subshell.prototype.data.account.RedditInboxUiFailureKind
import app.subshell.prototype.data.account.RedditProfileCommentsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.redditCommentsErrorPresentation
import app.subshell.prototype.ui.error.redditCommunityDiscoveryErrorPresentation
import app.subshell.prototype.ui.error.redditCommunityDiscoverySuggestionErrorPresentation
import app.subshell.prototype.ui.error.redditFeedAppendErrorPresentation
import app.subshell.prototype.ui.error.redditFeedErrorPresentation
import app.subshell.prototype.ui.error.redditInboxErrorPresentation
import app.subshell.prototype.ui.error.redditProfileCommentsErrorPresentation
import app.subshell.prototype.ui.error.redditSubscriptionsErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Error01a2CoreRedditReadErrorPolicyTest {
    @Test
    fun cachedCommentsRetainHttpCauseRetryDelayAndDegradedState() {
        val presentation = redditCommentsErrorPresentation(
            failureKind = RedditCommentUiFailureKind.HTTP,
            details = FailureDetails(
                httpStatusCode = 429,
                retryAfterMs = 18_000L,
                retryable = true,
            ),
            retryable = true,
            showingCachedContent = true,
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertEquals("Couldn't refresh comments", presentation.title)
        assertTrue(presentation.message.contains("Saved comments remain visible."))
        assertEquals("HTTP 429 · Retry after 18 s", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun appendFailureUsesDegradedLoadMoreCopyAndRetry() {
        val presentation = redditFeedAppendErrorPresentation(
            failureKind = RedditFeedUiFailureKind.TRANSPORT,
            details = FailureDetails(
                retryable = true,
                transportKind = NetworkTransportFailureKind.DNS,
            ),
            retryable = true,
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertEquals("Couldn't load more posts", presentation.title)
        assertTrue(presentation.message.contains("Posts already loaded remain visible."))
        assertEquals("Network: DNS", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun feedTransportFailureIsBlockingWithoutCachedPostsAndRetryable() {
        val presentation = redditFeedErrorPresentation(
            failureKind = RedditFeedUiFailureKind.TRANSPORT,
            details = FailureDetails(
                retryable = true,
                transportKind = NetworkTransportFailureKind.TIMEOUT,
            ),
            retryable = true,
            showingCachedContent = false,
        )

        assertEquals(ErrorPresentationKind.BLOCKING, presentation.kind)
        assertTrue(presentation.message.contains("couldn't reach Reddit"))
        assertEquals("Network: Timeout", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun discoveryRateLimitUsesSpecificReasonAndSharedHttpDetail() {
        val presentation = redditCommunityDiscoveryErrorPresentation(
            failureKind = RedditCommunityDiscoveryUiFailureKind.HTTP,
            details = FailureDetails(
                httpStatusCode = 429,
                retryAfterMs = 61_000L,
                retryable = true,
            ),
            retryable = true,
            showingCachedContent = true,
        )

        assertEquals(ErrorPresentationKind.DEGRADED, presentation.kind)
        assertTrue(presentation.message.contains("request limit was reached"))
        assertTrue(presentation.message.contains("Saved Explore results remain visible."))
        assertEquals("HTTP 429 · Retry after 2 min", presentation.technicalDetail)
    }

    @Test
    fun suggestionFailureDistinguishesInlineFailureFromFallbackDegradation() {
        val details = FailureDetails(
            protocolKind = NetworkProtocolFailureKind.MALFORMED_JSON,
            retryable = false,
        )
        val inline = redditCommunityDiscoverySuggestionErrorPresentation(
            failureKind = RedditCommunityDiscoveryUiFailureKind.PROTOCOL,
            details = details,
            retryable = false,
            showingFallbackResults = false,
        )
        val fallback = redditCommunityDiscoverySuggestionErrorPresentation(
            failureKind = RedditCommunityDiscoveryUiFailureKind.PROTOCOL,
            details = details,
            retryable = false,
            showingFallbackResults = true,
        )

        assertEquals(ErrorPresentationKind.INLINE, inline.kind)
        assertEquals(ErrorPresentationKind.DEGRADED, fallback.kind)
        assertEquals("Response: Malformed response", inline.technicalDetail)
        assertTrue(fallback.message.contains("Quick suggestions remain visible."))
        assertNull(fallback.primaryAction)
    }

    @Test
    fun sessionAndConfigurationCopyRemainActionableWithoutPretendingScopeFailure() {
        val session = redditInboxErrorPresentation(
            failureKind = RedditInboxUiFailureKind.SESSION,
            details = null,
            retryable = false,
            showingCachedContent = false,
        )
        val configuration = redditSubscriptionsErrorPresentation(
            failureKind = RedditSubscriptionsUiFailureKind.CLIENT_CONFIGURATION,
            details = FailureDetails(localKind = LocalFailureKind.CONFIGURATION),
            retryable = false,
            showingCachedContent = false,
        )

        assertTrue(session.message.contains("sign-in"))
        assertFalse(session.message.contains("permission", ignoreCase = true))
        assertTrue(configuration.message.contains("isn't configured"))
        assertEquals("Local: Configuration", configuration.technicalDetail)
    }

    @Test
    fun allCoreReadPresentationsAvoidGenericFallbackCopy() {
        val presentations = listOf(
            redditFeedErrorPresentation(
                RedditFeedUiFailureKind.HTTP,
                FailureDetails(httpStatusCode = 503),
                retryable = true,
                showingCachedContent = false,
            ),
            redditCommentsErrorPresentation(
                RedditCommentUiFailureKind.TRANSPORT,
                FailureDetails(transportKind = NetworkTransportFailureKind.DNS),
                retryable = true,
                showingCachedContent = true,
            ),
            redditInboxErrorPresentation(
                RedditInboxUiFailureKind.PROTOCOL,
                FailureDetails(protocolKind = NetworkProtocolFailureKind.EMPTY_BODY),
                retryable = false,
                showingCachedContent = false,
            ),
            redditSubscriptionsErrorPresentation(
                RedditSubscriptionsUiFailureKind.STORAGE,
                FailureDetails(localKind = LocalFailureKind.STORAGE),
                retryable = true,
                showingCachedContent = true,
            ),
            redditCommunityDiscoveryErrorPresentation(
                RedditCommunityDiscoveryUiFailureKind.HTTP,
                FailureDetails(httpStatusCode = 403),
                retryable = false,
                showingCachedContent = false,
            ),
            redditProfileCommentsErrorPresentation(
                RedditProfileCommentsUiFailureKind.TRANSPORT,
                FailureDetails(transportKind = NetworkTransportFailureKind.CONNECT),
                retryable = true,
                showingCachedContent = true,
            ),
        )

        presentations.forEach(::assertUsefulPresentation)
    }

    private fun assertUsefulPresentation(presentation: ErrorPresentation) {
        assertTrue(presentation.title.isNotBlank())
        assertTrue(presentation.message.isNotBlank())
        assertFalse(presentation.title.contains("Something went wrong", ignoreCase = true))
        assertFalse(presentation.message.contains("Something went wrong", ignoreCase = true))
        assertFalse(presentation.title.equals("Error", ignoreCase = true))
        assertFalse(presentation.message.equals("Failed", ignoreCase = true))
    }
}
