package app.subshell.prototype.media

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.ui.components.MediaFailurePlaceholder
import app.subshell.prototype.ui.error.RemoteMediaKind
import app.subshell.prototype.ui.error.remoteMediaErrorPresentation
import app.subshell.prototype.ui.theme.SubshellTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MEDIAGONE01 (owner 2026-10-18, from a diagnostic report with ~45 image 404s
 * in one scope): a removed image reads "Image removed" and is not recorded as
 * an error, a dead link is not fetched again, the diagnostic scope names the
 * kind of source, and GIFs may be 100 MB.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaGone01Test {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun scripted(code: Int, contentLength: Long? = null, calls: AtomicInteger = AtomicInteger()) =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                calls.incrementAndGet()
                val small = "x".toResponseBody("image/gif".toMediaType())
                val body = if (contentLength == null) small else object : ResponseBody() {
                    override fun contentType() = small.contentType()
                    override fun contentLength() = contentLength
                    override fun source(): BufferedSource = small.source()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("scripted")
                    .body(body)
                    .build()
            }
            .build()

    @Test
    fun aRemovedImageReadsRemovedWithoutAnAction() {
        val removed = remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, FailureDetails(httpStatusCode = 404, retryable = false))
        assertEquals("Image removed", removed.title)
        assertNull(removed.primaryAction)
        assertEquals("GIF removed", remoteMediaErrorPresentation(RemoteMediaKind.GIF, FailureDetails(httpStatusCode = 410)).title)
        assertEquals(
            "Couldn't load image",
            remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, FailureDetails(httpStatusCode = 500, retryable = true)).title,
        )
    }

    @Test
    fun aDeadLinkIsNotFetchedAgain() = runBlocking {
        val calls = AtomicInteger()
        val uri = "https://i.redd.it/mediagone01-dead.png"
        val first = RemoteImageLoader.load(uri, scripted(404, calls = calls))
        val second = RemoteImageLoader.load(uri, scripted(404, calls = calls))

        assertTrue(first is RemoteMediaLoadResult.Failed)
        assertEquals(404, (second as RemoteMediaLoadResult.Failed).details.httpStatusCode)
        assertEquals(1, calls.get())
    }

    @Test
    fun aGifMayBeLargerThanAnImage() = runBlocking {
        val sixtyMegabytes = 60L * 1_024L * 1_024L
        val image = RemoteImageLoader.load("https://i.redd.it/mediagone01-big.png", scripted(200, sixtyMegabytes))
        val gif = RemoteImageLoader.loadAnimated("https://i.redd.it/mediagone01-big.gif", scripted(200, sixtyMegabytes))

        assertEquals(NetworkProtocolFailureKind.BODY_TOO_LARGE, (image as RemoteMediaLoadResult.Failed).details.protocolKind)
        assertNotEquals(NetworkProtocolFailureKind.BODY_TOO_LARGE, (gif as? RemoteMediaLoadResult.Failed)?.details?.protocolKind)
    }

    @Test
    fun theScopeNamesTheKindOfSource() {
        assertEquals("media_image_i_redd_it", remoteMediaDiagnosticScope("media_image", "https://i.redd.it/a.png"))
        assertEquals("media_image_preview_redd_it", remoteMediaDiagnosticScope("media_image", "https://preview.redd.it/a.png?s=1"))
        assertEquals("media_image_external_preview", remoteMediaDiagnosticScope("media_image", "https://external-preview.redd.it/a.png"))
        assertEquals("media_gif_imgur", remoteMediaDiagnosticScope("media_gif", "https://i.imgur.com/a.gif"))
        assertEquals("media_image_other", remoteMediaDiagnosticScope("media_image", "https://example.org/a.png"))
    }

    @Test
    fun aRemovedImageIsNotRecordedButAFailureIs() {
        val store = DiagnosticEventStore(context)
        store.clear()
        composeRule.setContent {
            SubshellTheme {
                MediaFailurePlaceholder(
                    presentation = remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, FailureDetails(httpStatusCode = 404)),
                    diagnosticScopeKey = "media_image_i_redd_it",
                    onRetry = {},
                )
                MediaFailurePlaceholder(
                    presentation = remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, FailureDetails(httpStatusCode = 500, retryable = true)),
                    diagnosticScopeKey = "media_image_preview_redd_it",
                    onRetry = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Image removed").assertExists()

        assertEquals(listOf(500), store.snapshot().events.map { it.failure?.httpStatusCode })
    }
}
