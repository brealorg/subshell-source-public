package io.github.brealorg.subshell.data.network

import io.github.brealorg.subshell.data.account.RedditCommentUiFailureKind
import io.github.brealorg.subshell.data.diagnostics.REDDIT_REFUSAL_API
import io.github.brealorg.subshell.data.diagnostics.RedditRefusals
import io.github.brealorg.subshell.data.error.REDDIT_BLOCK_PAGE_REASON
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.ui.error.REDDIT_BLOCK_PAGE_MESSAGE
import io.github.brealorg.subshell.ui.error.redditCommentsErrorPresentation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITBLOCK01_REDDITS_BLOCK_PAGE_IS_NAMED_V1: Reddit's 403 HTML page
 * "You've been blocked by network security" (seen on /api/info and
 * /api/morechildren since 2026-10-30) is named in the diagnostic report and in
 * the error text, so it is not mistaken for an ordinary 403.
 */
class RedditBlock01BlockPageTest {
    private val reported = mutableListOf<Triple<String, Int, String?>>()

    @After
    fun uninstall() {
        RedditRefusals.installForTest(null)
    }

    private fun apiCall(code: Int, body: String, mediaType: String): NetworkResult<JsonElement> = runBlocking {
        SubshellHttpClient(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("answer")
                    .body(body.toResponseBody(mediaType.toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test",
        ).getJson(JsonGetRequest(pathSegments = listOf("api", "info")), JsonElement.serializer())
    }

    @Test
    fun theBlockPageIsNamedInTheReportAndTheErrorText() {
        RedditRefusals.installForTest { source, status, reason -> reported += Triple(source, status, reason) }

        assertTrue(BLOCK_PAGE.indexOf("blocked") > 180_000)
        val blocked = apiCall(403, BLOCK_PAGE, "text/html; charset=utf-8") as NetworkResult.HttpFailure
        val ordinary = apiCall(403, "<html><body>Forbidden</body></html>", "text/html") as NetworkResult.HttpFailure

        assertEquals(REDDIT_BLOCK_PAGE_REASON, blocked.providerReason)
        assertNull(ordinary.providerReason)
        assertEquals(
            listOf(
                Triple(REDDIT_REFUSAL_API, 403, REDDIT_BLOCK_PAGE_REASON),
                Triple(REDDIT_REFUSAL_API, 403, null),
            ),
            reported,
        )

        fun message(result: NetworkResult<*>, cached: Boolean) = redditCommentsErrorPresentation(
            failureKind = RedditCommentUiFailureKind.HTTP,
            details = result.failureDetailsOrNull(),
            retryable = true,
            showingCachedContent = cached,
        ).message
        assertEquals(REDDIT_BLOCK_PAGE_MESSAGE, message(blocked, cached = false))
        assertEquals("$REDDIT_BLOCK_PAGE_MESSAGE Saved comments remain visible.", message(blocked, cached = true))
        assertEquals("Reddit rejected the comments request.", message(ordinary, cached = false))
    }

    private companion object {
        // Measured 2026-10-31: 189 906 characters, no title, the sentence at 189 327.
        val BLOCK_PAGE = "<!doctype html><html><head>" + "<style>.x{}</style>".repeat(9_960) + "</head><body>" +
            "<div class=\"font-bold text-24\">You've been blocked by network security.</div></body></html>"
    }
}
