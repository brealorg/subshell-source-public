package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02C5R3MatrixIdentityPolicyTest {
    @Test
    fun matrixIdentityMustMatchTheActiveRedditProviderAccountId() {
        assertTrue(
            matrixUserMatchesRedditProviderId(
                matrixUserId = "@t2_jlmpi0:reddit.com",
                redditProviderAccountId = "jlmpi0",
            ),
        )
        assertTrue(
            matrixUserMatchesRedditProviderId(
                matrixUserId = "@T2_JLMPI0:reddit.com",
                redditProviderAccountId = "jlmpi0",
            ),
        )
        assertFalse(
            matrixUserMatchesRedditProviderId(
                matrixUserId = "@t2_other:reddit.com",
                redditProviderAccountId = "jlmpi0",
            ),
        )
    }
    @Test
    fun matrixMxcMediaIdentityMapsToStableDownloadUri() {
        val contentUri = "mxc://reddit.com/media_abc123"
        assertEquals("media_abc123", redditMatrixMediaId(contentUri))
        assertEquals(
            "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/media_abc123",
            redditMatrixDownloadUri(contentUri),
        )
        assertEquals(null, redditMatrixMediaId("mxc://elsewhere.example/media_abc123"))
    }

}
