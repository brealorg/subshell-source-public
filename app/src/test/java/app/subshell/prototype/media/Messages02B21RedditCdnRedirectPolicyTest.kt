package app.subshell.prototype.media

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02B21RedditCdnRedirectPolicyTest {
    private val source =
        "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/293rdk045tih1".toHttpUrl()

    @Test
    fun productionMatrix308MayRedirectToExactRedditImageCdn() {
        val target = resolveTrustedRedditMatrixMediaRedirect(
            source = source,
            location = "https://i.redd.it/293rdk045tih1.gif",
        )

        assertEquals("https://i.redd.it/293rdk045tih1.gif", target?.toString())
    }

    @Test
    fun sameHostMatrixMediaRedirectRemainsAccepted() {
        val target = resolveTrustedRedditMatrixMediaRedirect(
            source = source,
            location = "/_matrix/client/v1/media/download/reddit.com/293rdk045tih1",
        )

        assertEquals(
            "matrix.redditspace.com",
            target?.host,
        )
    }

    @Test
    fun unrelatedCrossHostRedirectRemainsBlocked() {
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source = source,
                location = "https://example.com/293rdk045tih1.gif",
            ),
        )
    }

    @Test
    fun redditCdnRedirectStillRequiresHttpsNoCredentialsAndNoAccessToken() {
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source = source,
                location = "http://i.redd.it/293rdk045tih1.gif",
            ),
        )
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source = source,
                location = "https://user:secret@i.redd.it/293rdk045tih1.gif",
            ),
        )
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source = source,
                location = "https://i.redd.it/293rdk045tih1.gif?access_token=nope",
            ),
        )
    }

    @Test
    fun oneHopRedirectLimitRemainsUnchanged() {
        assertEquals(1, REMOTE_IMAGE_MAX_TRUSTED_REDIRECTS)
        assertTrue(isSupportedRemoteImageRedirectCode(308))
        assertFalse(isSupportedRemoteImageRedirectCode(300))
    }
}
