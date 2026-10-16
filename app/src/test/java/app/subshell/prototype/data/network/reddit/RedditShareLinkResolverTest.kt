package app.subshell.prototype.data.network.reddit

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * REDDITURL03: share links resolve through Reddit's redirect, read without
 * credentials. Every example URL here is made up.
 */
class RedditShareLinkResolverTest {
    private class ScriptedReddit(
        private val answers: Map<String, Pair<Int, String?>>,
    ) : Call.Factory {
        val requests = mutableListOf<Request>()
        private val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val (code, location) = answers[request.url.toString()]
                    ?: throw IOException("no route to ${request.url}")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("scripted")
                    .apply { location?.let { header("Location", it) } }
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        override fun newCall(request: Request): Call = client.newCall(request)
    }

    private val share = "https://www.reddit.com/r/kotlin/s/Ab3dE5fG7h"

    @Test
    fun aShareLinkResolvesToWhereRedditRedirectsIt() = runBlocking {
        val reddit = ScriptedReddit(
            mapOf(share to (307 to "https://www.reddit.com/r/kotlin/comments/1abcde/null_safety/")),
        )

        val resolved = RedditShareLinkResolver(callFactory = reddit).resolve(share)

        assertEquals("https://www.reddit.com/r/kotlin/comments/1abcde/null_safety/", resolved)
        val request = reddit.requests.single()
        assertEquals("HEAD", request.method)
        assertNull(request.header("Authorization"))
        assertEquals("subshell-links/1.0", request.header("User-Agent"))
    }

    @Test
    fun aRelativeLocationIsResolvedAgainstTheShareLink() = runBlocking {
        val reddit = ScriptedReddit(mapOf(share to (301 to "/r/kotlin/comments/1abcde/x/kz9y8x7/")))

        assertEquals(
            "https://www.reddit.com/r/kotlin/comments/1abcde/x/kz9y8x7/",
            RedditShareLinkResolver(callFactory = reddit).resolve(share),
        )
    }

    @Test
    fun redirectsAreFollowedWhileTheyAreStillShareLinks() = runBlocking {
        val second = "https://www.reddit.com/u/someone/s/Zz9Yy8Xx7"
        val reddit = ScriptedReddit(
            mapOf(
                share to (307 to second),
                second to (307 to "https://www.reddit.com/user/someone/comments/1abcde/post/"),
            ),
        )

        assertEquals(
            "https://www.reddit.com/user/someone/comments/1abcde/post/",
            RedditShareLinkResolver(callFactory = reddit).resolve(share),
        )
        assertEquals(2, reddit.requests.size)
    }

    @Test
    fun noRedirectAFailureOrAnEndlessChainGivesNothing() = runBlocking {
        assertNull(RedditShareLinkResolver(callFactory = ScriptedReddit(mapOf(share to (200 to null)))).resolve(share))
        assertNull(RedditShareLinkResolver(callFactory = ScriptedReddit(mapOf(share to (307 to null)))).resolve(share))
        assertNull(RedditShareLinkResolver(callFactory = ScriptedReddit(emptyMap())).resolve(share))
        assertNull(
            RedditShareLinkResolver(
                callFactory = ScriptedReddit(mapOf(share to (307 to share))),
                maxRedirects = 3,
            ).resolve(share),
        )
        assertNull(RedditShareLinkResolver(callFactory = ScriptedReddit(emptyMap())).resolve("not a url"))
    }
}
