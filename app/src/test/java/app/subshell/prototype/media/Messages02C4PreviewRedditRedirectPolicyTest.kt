package app.subshell.prototype.media

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02C4PreviewRedditRedirectPolicyTest {
    private val source =
        "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/9vj34eq42llh1".toHttpUrl()

    @Test
    fun matrixDownloadMayRedirectToPreviewRedditCdn() {
        val target = resolveTrustedRedditMatrixMediaRedirect(
            source = source,
            location = "https://preview.redd.it/9vj34eq42llh1.gif?auto=webp&s=abc",
        )

        assertEquals("preview.redd.it", target?.host)
    }

    @Test
    fun previewRedditRedirectStillRejectsAccessTokens() {
        assertNull(
            resolveTrustedRedditMatrixMediaRedirect(
                source = source,
                location = "https://preview.redd.it/9vj34eq42llh1.gif?access_token=nope",
            ),
        )
    }
}
