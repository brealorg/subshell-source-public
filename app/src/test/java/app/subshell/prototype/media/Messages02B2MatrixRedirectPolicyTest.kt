package app.subshell.prototype.media

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02B2MatrixRedirectPolicyTest {
    @Test
    fun legacyMatrixMediaMayRedirectOnceToAuthenticatedMediaPathOnSameHost() {
        val source =
            "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media-id".toHttpUrl()

        val target = resolveTrustedRedditMatrixMediaRedirect(
            source = source,
            location = "/_matrix/client/v1/media/download/reddit.com/media-id",
        )

        assertEquals(
            "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/media-id",
            target?.toString(),
        )
    }

    @Test
    fun absoluteSameHostMatrixMediaRedirectIsAccepted() {
        val source =
            "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media-id".toHttpUrl()

        val target = resolveTrustedRedditMatrixMediaRedirect(
            source = source,
            location =
                "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/media-id",
        )

        assertEquals("matrix.redditspace.com", target?.host)
    }

    @Test
    fun crossHostCleartextCredentialAndNonMediaRedirectsAreRejected() {
        val source =
            "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media-id".toHttpUrl()

        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source,
                "https://example.com/_matrix/client/v1/media/download/reddit.com/media-id",
            ),
        )
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source,
                "http://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/media-id",
            ),
        )
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source,
                "https://user:secret@matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/media-id",
            ),
        )
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source,
                "https://matrix.redditspace.com/not-matrix-media",
            ),
        )
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source,
                "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/media-id?access_token=nope",
            ),
        )
    }

    @Test
    fun redirectSupportIsNarrowAndOneHopOnly() {
        assertTrue(isSupportedRemoteImageRedirectCode(301))
        assertTrue(isSupportedRemoteImageRedirectCode(302))
        assertTrue(isSupportedRemoteImageRedirectCode(303))
        assertTrue(isSupportedRemoteImageRedirectCode(307))
        assertTrue(isSupportedRemoteImageRedirectCode(308))
        assertFalse(isSupportedRemoteImageRedirectCode(300))
        assertFalse(isSupportedRemoteImageRedirectCode(304))
        assertFalse(isSupportedRemoteImageRedirectCode(399))
        assertEquals(1, REMOTE_IMAGE_MAX_TRUSTED_REDIRECTS)
    }
}
