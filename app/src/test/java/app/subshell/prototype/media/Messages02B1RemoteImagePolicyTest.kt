package app.subshell.prototype.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02B1RemoteImagePolicyTest {
    @Test
    fun normalImageContentTypeRemainsAllowed() {
        assertTrue(
            isAllowedRemoteImageContentType(
                host = "i.redd.it",
                encodedPath = "/example.png",
                type = "image",
                subtype = "png",
            ),
        )
    }

    @Test
    fun redditMatrixMediaMayUseOctetStream() {
        assertTrue(
            isAllowedRemoteImageContentType(
                host = "matrix.redditspace.com",
                encodedPath = "/_matrix/media/v3/download/reddit.com/media-id",
                type = "application",
                subtype = "octet-stream",
            ),
        )
        assertTrue(
            isAllowedRemoteImageContentType(
                host = "matrix.redditspace.com",
                encodedPath = "/_matrix/client/v1/media/download/reddit.com/media-id",
                type = "application",
                subtype = "octet-stream",
            ),
        )
    }

    @Test
    fun octetStreamRelaxationIsRestrictedToRedditMatrixMedia() {
        assertFalse(
            isAllowedRemoteImageContentType(
                host = "example.com",
                encodedPath = "/_matrix/media/v3/download/reddit.com/media-id",
                type = "application",
                subtype = "octet-stream",
            ),
        )
        assertFalse(
            isAllowedRemoteImageContentType(
                host = "matrix.redditspace.com",
                encodedPath = "/not-a-matrix-media-path",
                type = "application",
                subtype = "octet-stream",
            ),
        )
        assertFalse(
            isAllowedRemoteImageContentType(
                host = "matrix.redditspace.com",
                encodedPath = "/_matrix/media/v3/download/reddit.com/media-id",
                type = "text",
                subtype = "html",
            ),
        )
    }
}
