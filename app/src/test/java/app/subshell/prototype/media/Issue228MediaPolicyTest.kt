package app.subshell.prototype.media

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.ui.components.MediaFailurePlaceholder
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.RemoteMediaKind
import app.subshell.prototype.ui.error.remoteMediaErrorPresentation
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #228 (owner): an image linked over plain http is asked for over https
 * instead of being refused outright; a failure that cannot pass (blocked,
 * invalid, undecodable) offers no Retry but names the host and opens the link
 * in the browser; a failure that can pass keeps Retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue228MediaPolicyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun recording(requested: MutableList<String>) = OkHttpClient.Builder().addInterceptor { chain ->
        requested += chain.request().url.toString()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(404).message("missing")
            .body("x".toResponseBody("image/png".toMediaType()))
            .build()
    }.build()

    @Test
    fun anHttpImageIsAskedForOverHttps() {
        val requested = mutableListOf<String>()
        val uri = "http://images.example.org/issue228-${System.nanoTime()}.png"

        runBlocking { RemoteImageLoader.load(uri, recording(requested)) }

        assertEquals(listOf(uri.replaceFirst("http://", "https://")), requested)
    }

    @Test
    fun aLinkWithCredentialsIsStillRefusedWithoutARequest() {
        val requested = mutableListOf<String>()

        val result = runBlocking {
            RemoteImageLoader.load("http://user:secret@images.example.org/a.png", recording(requested))
        }

        val details = (result as RemoteMediaLoadResult.Failed).details
        assertEquals(LocalFailureKind.SECURITY, details.localKind)
        assertEquals(emptyList<String>(), requested)
    }

    @Test
    fun aLinkToAnImageHostItselfStaysAText() {
        // The reported post: "New: [i.redd.it](http://i.redd.it) image links open ..."
        val bare = app.subshell.prototype.model.projectCommentContent("New: [i.redd.it](http://i.redd.it) links")
        val slash = app.subshell.prototype.model.projectCommentContent("See https://i.redd.it/ here")
        val image = app.subshell.prototype.model.projectCommentContent("https://i.redd.it/abc123")

        assertTrue(bare.media.isEmpty())
        assertTrue(bare.displayText.contains("i.redd.it"))
        assertTrue(slash.media.isEmpty())
        assertEquals(1, image.media.size)
    }

    @Test
    fun onlyAFailureThatCanPassOffersRetry() {
        val blocked = FailureDetails(retryable = false, localKind = LocalFailureKind.SECURITY)
        val server = FailureDetails(retryable = true, httpStatusCode = 503)
        val link = "https://images.example.org/a.png"

        assertEquals(
            ErrorActionKind.OPEN_IN_BROWSER,
            remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, blocked, link).primaryAction?.kind,
        )
        assertEquals(
            ErrorActionKind.RETRY,
            remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, server, link).primaryAction?.kind,
        )
        assertNull(remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, blocked).primaryAction)
    }

    @Test
    fun aBlockedImageNamesItsHostAndOpensInTheBrowser() {
        val opened = mutableListOf<String>()
        val link = "http://www.oldhost.example/patch.png"
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalUriHandler provides object : UriHandler {
                        override fun openUri(uri: String) {
                            opened += uri
                        }
                    },
                ) {
                    MediaFailurePlaceholder(
                        presentation = remoteMediaErrorPresentation(
                            RemoteMediaKind.IMAGE,
                            FailureDetails(retryable = false, localKind = LocalFailureKind.SECURITY),
                            link,
                        ),
                        diagnosticScopeKey = "issue228",
                        onRetry = {},
                        sourceUri = link,
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
        composeRule.onNodeWithText("oldhost.example").assertExists()
        composeRule.onNodeWithText("Open in browser").performClick()
        assertEquals(listOf(link), opened)
    }

    @Test
    fun aServerFailureKeepsRetryAndNoBrowserLink() {
        var retried = 0
        val link = "https://i.redd.it/a.png"
        composeRule.setContent {
            SubshellTheme {
                MediaFailurePlaceholder(
                    presentation = remoteMediaErrorPresentation(
                        RemoteMediaKind.IMAGE,
                        FailureDetails(retryable = true, httpStatusCode = 503),
                        link,
                    ),
                    diagnosticScopeKey = "issue228",
                    onRetry = { retried += 1 },
                    sourceUri = link,
                )
            }
        }

        composeRule.onAllNodesWithText("Open in browser").assertCountEquals(0)
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried == 1)
    }
}
