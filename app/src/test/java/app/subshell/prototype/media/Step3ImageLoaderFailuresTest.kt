package app.subshell.prototype.media

import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.SubshellNetworkTransport
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a3): a remote image that cannot be shown fails with a typed
 * reason instead of a bare null — HTTP status (retryable only for a server
 * or rate answer), a wrong content type, an empty body, an unreachable host,
 * and a redirect to an untrusted host. An image request carries no account
 * credentials (auth06c): no Authorization header and no cookies, on the
 * shared media client the app uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ImageLoaderFailuresTest {
    private var counter = 0

    private fun uri() = "https://i.redd.it/step3-${counter++}-${System.nanoTime()}.png"

    private fun answering(code: Int, type: String = "image/png", body: String = "x", location: String? = null) =
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .apply { location?.let { header("Location", it) } }
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }.build()

    private fun failure(callFactory: okhttp3.Call.Factory) = runBlocking {
        (RemoteImageLoader.load(uri(), callFactory) as RemoteMediaLoadResult.Failed).details
    }

    @Test
    fun anHttpFailureKeepsItsStatusAndIsRetryableOnlyForAServerAnswer() {
        val server = failure(answering(503))
        val missing = failure(answering(404))

        assertEquals(503, server.httpStatusCode)
        assertEquals(true, server.retryable)
        assertEquals(404, missing.httpStatusCode)
        assertEquals(false, missing.retryable)
    }

    @Test
    fun aWrongContentTypeAndAnEmptyBodyAreProtocolFailures() {
        assertEquals(NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE, failure(answering(200, type = "text/html")).protocolKind)
        assertEquals(NetworkProtocolFailureKind.EMPTY_BODY, failure(answering(200, body = "")).protocolKind)
    }

    @Test
    fun anUnreachableHostIsATransportFailure() {
        val offline = OkHttpClient.Builder().addInterceptor { throw UnknownHostException("i.redd.it") }.build()

        assertEquals(NetworkTransportFailureKind.DNS, failure(offline).transportKind)
    }

    @Test
    fun aRedirectToAnUntrustedHostIsRefused() {
        val details = failure(answering(302, location = "https://evil.example/image.png"))

        assertEquals(LocalFailureKind.SECURITY, details.localKind)
        assertEquals(302, details.httpStatusCode)
    }

    @Test
    fun anImageRequestCarriesNoAccountCredentials() {
        val shared = SubshellNetworkTransport.mediaReadCallFactory as OkHttpClient
        var seen: Request? = null
        val recording = shared.newBuilder().addInterceptor { chain ->
            seen = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(404).message("answer")
                .body("x".toResponseBody("image/png".toMediaType()))
                .build()
        }.build()

        failure(recording)

        val request = requireNotNull(seen) { "the image was asked for" }
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
        assertEquals("image/*", request.header("Accept"))
        // Cookies are added below the interceptors, from the client's jar.
        assertSame(CookieJar.NO_COOKIES, shared.cookieJar)
    }
}
