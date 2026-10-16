package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DATALAYER02: Reddit refuses a morechildren request (rate limit and other
 * API errors) with HTTP 200 and json.errors. That must not look like an
 * empty expansion: merging it would drop the requested replies from the
 * cached thread for good.
 */
class DataLayer02MoreChildrenRejectionTest {
    private fun source(body: String): RedditCommentNetworkDataSource {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return RedditCommentNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https("https://example.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-unit-test",
                bearerTokenProvider = NetworkBearerTokenProvider { "token" },
            ),
        )
    }

    private fun page(body: String): RedditMoreChildrenPage = runBlocking {
        val result = source(body).moreChildren(
            postId = "t3_post",
            childIds = listOf("c1", "c2"),
            sort = RedditCommentSort.entries.first(),
        )
        (result as NetworkResult.Success).value
    }

    private val thread = listOf(
        Comment(
            id = "t1_parent",
            author = "a",
            age = "1h",
            body = "parent",
            score = 1,
            depth = 0,
            accent = 0,
            children = listOf(
                Comment(
                    id = "more_node",
                    author = "",
                    age = "",
                    body = "",
                    score = 0,
                    depth = 1,
                    accent = 0,
                    moreChildIds = listOf("c1", "c2"),
                    moreCount = 2,
                ),
            ),
        ),
    )

    @Test
    fun aRateLimitedExpansionIsMarkedRejected() {
        val rejected = page(
            """{"json":{"errors":[["RATELIMIT","you are doing that too much","ratelimit"]],"data":{"things":[]}}}""",
        )
        assertTrue(rejected.rejectedByReddit)
        assertEquals(emptyList<Comment>(), rejected.comments)
    }

    @Test
    fun aNormalExpansionIsNotRejected() {
        assertFalse(page("""{"json":{"errors":[],"data":{"things":[]}}}""").rejectedByReddit)
    }

    @Test
    fun aRejectedPageLeavesTheThreadAsItWas() {
        assertNull(
            mergeMoreChildrenPage(
                current = thread,
                moreNodeId = "more_node",
                page = RedditMoreChildrenPage(comments = emptyList(), rejectedByReddit = true),
                requestedChildCount = 2,
            ),
        )
    }

    @Test
    fun anAcceptedEmptyPageStillReplacesTheMoreNode() {
        val merged = mergeMoreChildrenPage(
            current = thread,
            moreNodeId = "more_node",
            page = RedditMoreChildrenPage(comments = emptyList()),
            requestedChildCount = 2,
        )
        assertEquals(emptyList<Comment>(), merged?.single()?.children)
    }
}
