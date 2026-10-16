package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITSEARCH01: Reddit answers some searches (URL-shaped queries, seen on
 * device as "HTTP 302") with a redirect; that is an empty result, not a
 * failed request. Other failures stay failures.
 */
class RedditSearchRedirectTest {
    private fun dataSource(code: Int, body: String = "", location: String? = null): RedditListingNetworkDataSource {
        val callFactory = object : Call.Factory {
            private val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("scripted")
                        .apply { location?.let { header("Location", it) } }
                        .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                }
                .build()

            override fun newCall(request: Request): Call = client.newCall(request)
        }
        return RedditListingNetworkDataSource(
            httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https("https://oauth.reddit.com"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-test/1.0",
            ),
        )
    }

    @Test
    fun aRedirectedSearchIsAnEmptyResult() = runBlocking {
        val result = dataSource(302, location = "https://www.reddit.com/submit?url=https%3A%2F%2Fexample.com%2Fa")
            .searchPosts("https://example.com/a")

        assertTrue(result is NetworkResult.Success)
        val page = (result as NetworkResult.Success).value
        assertEquals(emptyList<Any>(), page.items)
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun otherFailuresStayFailures() = runBlocking {
        val result = dataSource(403).searchPosts("kotlin")

        assertTrue(result is NetworkResult.HttpFailure)
        assertEquals(403, (result as NetworkResult.HttpFailure).statusCode)
    }

    @Test
    fun aNormalSearchStillReturnsItsPosts() = runBlocking {
        val listing = """{"kind":"Listing","data":{"after":null,"children":[
            {"kind":"t3","data":{"id":"1abcde","name":"t3_1abcde","subreddit":"kotlin","title":"Flows","selftext":"x","is_self":true}}
        ]}}"""
        val result = dataSource(200, body = listing).searchPosts("flows")

        assertTrue(result is NetworkResult.Success)
        assertEquals(1, (result as NetworkResult.Success).value.items.size)
    }
}
