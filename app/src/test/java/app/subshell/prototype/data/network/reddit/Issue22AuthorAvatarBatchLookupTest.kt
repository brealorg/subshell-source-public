package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.Call
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

class Issue22AuthorAvatarBatchLookupTest {
    @Test
    fun oneRequestPerHundredIdsAndOnlyHttpsAvatarsSurvive() = runBlocking {
        val factory = ScriptedCallFactory { request ->
            val ids = request.url.queryParameter("ids").orEmpty().split(",")
            val entries = ids.mapIndexed { index, id ->
                val image = when (index % 3) {
                    0 -> "\"https://i.redd.it/$id.png\""
                    1 -> "\"http://insecure.example/$id.png\""
                    else -> "\"\""
                }
                "\"$id\": {\"name\": \"user_$id\", \"profile_img\": $image, \"profile_color\": \"\"}"
            }
            200 to "{${entries.joinToString(",")}}"
        }
        val ids = (1..150).map { index -> "t2_id$index" }

        val result = RedditIdentityNetworkDataSource(httpClient(factory)).authorAvatars(ids + ids.take(5) + "junk")

        assertTrue(result is NetworkResult.Success)
        val value = (result as NetworkResult.Success).value
        assertEquals(2, factory.requests.size)
        assertEquals(
            "api/user_data_by_account_ids",
            factory.requests.first().url.pathSegments.joinToString("/"),
        )
        assertEquals("1", factory.requests.first().url.queryParameter("raw_json"))
        assertEquals(100, factory.requests[0].url.queryParameter("ids")!!.split(",").size)
        assertEquals(50, factory.requests[1].url.queryParameter("ids")!!.split(",").size)
        assertEquals(150, value.size)
        assertEquals("https://i.redd.it/t2_id1.png", value["t2_id1"])
        assertNull("http avatars are not accepted", value["t2_id2"])
        assertNull("empty avatars map to null", value["t2_id3"])
    }

    @Test
    fun notFoundMeansNoMatchingAccountsNotAFailure() = runBlocking {
        val factory = ScriptedCallFactory { 404 to "{\"message\": \"Not Found\", \"error\": 404}" }

        val result = RedditIdentityNetworkDataSource(httpClient(factory)).authorAvatars(listOf("t2_nobody"))

        assertTrue(result is NetworkResult.Success)
        assertTrue((result as NetworkResult.Success).value.isEmpty())
    }

    @Test
    fun otherHttpFailuresPropagateAndNoValidIdSendsNothing() = runBlocking {
        val failing = ScriptedCallFactory { 403 to "{}" }
        val failed = RedditIdentityNetworkDataSource(httpClient(failing)).authorAvatars(listOf("t2_x"))
        assertTrue(failed is NetworkResult.HttpFailure)
        assertEquals(403, (failed as NetworkResult.HttpFailure).statusCode)

        val idle = ScriptedCallFactory { 200 to "{}" }
        val empty = RedditIdentityNetworkDataSource(httpClient(idle)).authorAvatars(listOf("junk", "t1_comment"))
        assertTrue(empty is NetworkResult.Success)
        assertTrue((empty as NetworkResult.Success).value.isEmpty())
        assertEquals(0, idle.requests.size)
    }

    private fun httpClient(factory: Call.Factory) = SubshellHttpClient(
        callFactory = factory,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.com/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
        bearerTokenProvider = NetworkBearerTokenProvider { "token" },
    )
}

private class ScriptedCallFactory(
    private val script: (Request) -> Pair<Int, String>,
) : Call.Factory {
    val requests = CopyOnWriteArrayList<Request>()
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val (code, body) = script(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    override fun newCall(request: Request): Call = client.newCall(request)
}
