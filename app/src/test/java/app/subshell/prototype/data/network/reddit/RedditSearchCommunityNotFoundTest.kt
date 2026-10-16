package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITSEARCH02: an empty search in one community asks Reddit whether the
 * community exists; a missing one is HTTP 404 instead of "no results".
 */
class RedditSearchCommunityNotFoundTest {
    private data class Scripted(val code: Int, val body: String = "{}")

    private val paths = mutableListOf<String>()

    private fun dataSource(search: Scripted, about: Scripted): RedditListingNetworkDataSource {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                paths += request.url.encodedPath
                val scripted = if (request.url.encodedPath.endsWith("/about")) about else search
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(scripted.code)
                    .message("scripted")
                    .body(scripted.body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
        return RedditListingNetworkDataSource(
            httpClient = SubshellHttpClient(
                callFactory = client,
                endpoint = NetworkEndpoint.https("https://oauth.reddit.com"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-test/1.0",
            ),
        )
    }

    private val emptyListing = """{"kind":"Listing","data":{"after":null,"children":[]}}"""
    private val community = """{"kind":"t5","data":{"display_name":"kotlin"}}"""

    private fun statusOf(result: NetworkResult<*>): Int? = (result as? NetworkResult.HttpFailure)?.statusCode

    @Test
    fun anEmptySearchInAMissingCommunityIsNotFound() = runBlocking {
        val result = dataSource(Scripted(200, emptyListing), Scripted(404))
            .searchPosts("test", community = "doesnotexist")

        assertEquals(404, statusOf(result))
        assertEquals(false, (result as NetworkResult.HttpFailure).retryable)
        assertEquals(listOf("/r/doesnotexist/search", "/r/doesnotexist/about"), paths.map { it.removeSuffix(".json") })
    }

    @Test
    fun aRedirectedSearchInACommunityRedirectingAboutIsNotFound() = runBlocking {
        val result = dataSource(Scripted(302), Scripted(302)).searchPosts("test", community = "doesnotexist")

        assertEquals(404, statusOf(result))
    }

    @Test
    fun anAboutAnswerThatIsNotACommunityIsNotFound() = runBlocking {
        val result = dataSource(Scripted(200, emptyListing), Scripted(200, emptyListing))
            .searchPosts("test", community = "doesnotexist")

        assertEquals(404, statusOf(result))
    }

    @Test
    fun anExistingCommunityKeepsTheEmptyResult() = runBlocking {
        val result = dataSource(Scripted(302), Scripted(200, community)).searchPosts("https://example.com/a", community = "kotlin")

        assertTrue(result is NetworkResult.Success)
        assertEquals(emptyList<Any>(), (result as NetworkResult.Success).value.items)
    }

    @Test
    fun aFailedCheckKeepsTheEmptyResult() = runBlocking {
        for (code in listOf(403, 429, 500)) {
            val result = dataSource(Scripted(200, emptyListing), Scripted(code)).searchPosts("test", community = "private")
            assertTrue("about $code", result is NetworkResult.Success)
        }
    }

    @Test
    fun noCheckWithoutACommunityOnALaterPageOrWithResults() = runBlocking {
        dataSource(Scripted(200, emptyListing), Scripted(404)).searchPosts("test")
        dataSource(Scripted(200, emptyListing), Scripted(404))
            .searchPosts("test", community = "kotlin", request = RepositoryPageRequest(cursor = "t3_abc"))
        val withPost = """{"kind":"Listing","data":{"after":null,"children":[
            {"kind":"t3","data":{"id":"1abcde","name":"t3_1abcde","subreddit":"kotlin","title":"Flows","selftext":"x","is_self":true}}
        ]}}"""
        dataSource(Scripted(200, withPost), Scripted(404)).searchPosts("flows", community = "kotlin")

        assertEquals(emptyList<String>(), paths.filter { it.endsWith("/about") || it.endsWith("/about.json") })
    }
}
