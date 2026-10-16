package app.subshell.prototype.media

import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02b1, 02b2, 02b21): a chat image on Reddit's Matrix media
 * host may redirect once — to the same host's media path or to Reddit's image
 * CDN — and every hop is a fresh GET for an image with no Authorization or
 * Cookie; a second redirect, an http target or a target carrying an access
 * token is refused; only Matrix media may answer as octet-stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3MatrixImageRedirectLoaderTest {
    private val requests = mutableListOf<Request>()
    private var counter = 0

    private fun matrix() = "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/m${counter++}-${System.nanoTime()}"

    /** Answers each request from [answers] by its full URL; records every request. */
    private fun server(answers: (String) -> Pair<Int, Any?>) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request
        val (code, value) = answers(request.url.toString())
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("answer")
            .apply { if (code in 300..399) header("Location", value as String) }
            .body(
                if (code in 300..399) "".toResponseBody(null)
                else "not really an image".toResponseBody((value as String).toMediaType()),
            )
            .build()
    }.build()

    private fun load(uri: String, factory: okhttp3.Call.Factory) = runBlocking { RemoteImageLoader.load(uri, factory) }

    private fun RemoteMediaLoadResult<*>.failure() = (this as? RemoteMediaLoadResult.Failed)?.details

    /** The bytes reached the decoder: no HTTP, protocol or security failure. */
    private fun RemoteMediaLoadResult<*>.reachedTheDecoder() = failure().let { details ->
        details == null || (details.localKind == LocalFailureKind.DECODING && details.httpStatusCode == null && details.protocolKind == null)
    }

    private fun assertPlainImageGets() {
        for (request in requests) {
            assertEquals("GET", request.method)
            assertEquals("image/*", request.header("Accept"))
            assertTrue(!request.header("User-Agent").isNullOrBlank())
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
        }
    }

    @Test
    fun aMatrixImageFollowsOneRedirectOnTheSameHostWithAFreshPlainGet() {
        val source = matrix()
        val id = source.substringAfterLast('/')
        val result = load(
            source,
            server { url ->
                if (url == source) 308 to "/_matrix/client/v1/media/download/reddit.com/$id"
                else 200 to "application/octet-stream"
            },
        )

        assertTrue(result.toString(), result.reachedTheDecoder())
        assertEquals(
            listOf(source, "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/$id"),
            requests.map { it.url.toString() },
        )
        assertPlainImageGets()
    }

    @Test
    fun aMatrixImageMayRedirectToRedditsImageCdn() {
        for (target in listOf("https://i.redd.it/a.gif", "https://preview.redd.it/b.gif", "https://external-preview.redd.it/c.jpg")) {
            requests.clear()
            val source = matrix()
            val result = load(source, server { url -> if (url == source) 308 to target else 200 to "image/gif" })

            assertTrue(target, result.reachedTheDecoder())
            assertEquals(listOf(source, target), requests.map { it.url.toString() })
            assertPlainImageGets()
        }
    }

    @Test
    fun aSecondRedirectIsNotFollowed() {
        val source = matrix()
        val next = "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/next-${System.nanoTime()}"
        val result = load(
            source,
            server { url -> if (url == source) 308 to next else 302 to "https://i.redd.it/third.gif" },
        )

        assertEquals(302, result.failure()?.httpStatusCode)
        assertEquals(false, result.failure()?.retryable)
        assertEquals(2, requests.size)
    }

    @Test
    fun anHttpTargetOrOneWithAnAccessTokenIsRefused() {
        for (target in listOf(
            "http://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/x",
            "https://matrix.redditspace.com/_matrix/client/v1/media/download/reddit.com/x?access_token=secret",
        )) {
            requests.clear()
            val source = matrix()
            val result = load(source, server { 308 to target })

            assertEquals(target, LocalFailureKind.SECURITY, result.failure()?.localKind)
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun onlyMatrixMediaMayAnswerAsOctetStream() {
        val cdn = "https://i.redd.it/octet-${System.nanoTime()}.png"
        val result = load(cdn, server { 200 to "application/octet-stream" })

        assertEquals(NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE, result.failure()?.protocolKind)
    }
}
