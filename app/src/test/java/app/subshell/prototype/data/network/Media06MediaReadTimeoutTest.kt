package app.subshell.prototype.data.network

import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * MEDIA06 (#149): OkHttp's call timeout also covers reading the body, so the
 * shared client's whole-call limit aborted every image share/save or large
 * display load that took longer, and a retry failed the same way. The media
 * read client keeps the connection pool and idle read timeout but has no
 * whole-call limit.
 */
class Media06MediaReadTimeoutTest {
    @Test
    fun aTransferLongerThanTheBaseCallTimeoutCompletesOnTheMediaReadClient() {
        // Stand-in for the shared client: a 300 ms whole-call limit, and a
        // "transfer" that takes 600 ms.
        val base = OkHttpClient.Builder()
            .callTimeout(300, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                Thread.sleep(600)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body("image".toResponseBody("image/png".toMediaType()))
                    .build()
            }
            .build()
        val request = Request.Builder().url("https://i.redd.it/large.png").build()

        assertThrows(InterruptedIOException::class.java) { base.newCall(request).execute().close() }

        val media = SubshellOkHttpClientFactory.createMediaReadClient(base)
        media.newCall(request).execute().use { response -> assertEquals("image", response.body.string()) }
        assertEquals(0, media.callTimeoutMillis)
        assertEquals(SubshellOkHttpClientFactory.MEDIA_READ_TIMEOUT_SECONDS * 1_000, media.readTimeoutMillis.toLong())
        assertEquals(base.connectionPool, media.connectionPool)
    }

    // STEP3 (messages02b2): media reads follow no redirect on their own; the
    // image loader decides each hop itself (one trusted hop at most).
    @Test
    fun mediaReadsFollowNoRedirectOnTheirOwn() {
        val media = SubshellOkHttpClientFactory.createMediaReadClient(SubshellOkHttpClientFactory.create())

        assertEquals(false, media.followRedirects)
        assertEquals(false, media.followSslRedirects)
    }
}
