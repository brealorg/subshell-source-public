package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.OnlineVisibilityResult
import app.subshell.prototype.data.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class UserStatus01A1NetworkTest {
    @Test fun strictBooleanParsingRejectsMissingNullStringsAndNumbers() {
        val json = SubshellNetworkJson.providerJson
        assertEquals(OnlineVisibilityResult.Known(true), parseOnlineVisibility(json.parseToJsonElement("""{"show_presence":true}""")))
        assertEquals(OnlineVisibilityResult.Known(false), parseOnlineVisibility(json.parseToJsonElement("""{"show_presence":false}""")))
        for (body in listOf("{}", "[]", "null", """{"show_presence":null}""", """{"show_presence":"false"}""", """{"show_presence":0}""", """{"show_presence":{}}""")) {
            assertEquals(body, OnlineVisibilityResult.Unavailable, parseOnlineVisibility(json.parseToJsonElement(body)))
        }
    }

    @Test fun getIsFieldFilteredAndPatchContainsOnlyOneBoolean() {
        val get = onlineVisibilityGetRequest()
        assertEquals(listOf("api", "v1", "me", "prefs"), get.pathSegments)
        assertEquals(listOf("fields" to "show_presence"), get.queryParameters)
        assertEquals("""{"show_presence":true}""", onlineVisibilityPatchRequest(true).body.toString())
        assertEquals("""{"show_presence":false}""", onlineVisibilityPatchRequest(false).body.toString())
    }

    @Test fun actualSharedHttpClientUsesJsonPatchAndPinnedBearer() = runBlocking {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            bodies += Buffer().also { request.body?.writeTo(it) }.readUtf8()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"show_presence":false,"unrelated":"ignored"}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https("https://example.test/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-unit-test",
            bearerTokenProvider = NetworkBearerTokenProvider { "pinned-token" },
        )
        val network = RedditOnlineVisibilityNetworkDataSource(client)
        assertEquals(OnlineVisibilityResult.Known(false), network.read())
        assertEquals(OnlineVisibilityResult.Known(false), network.patch(false))
        assertEquals("GET", requests[0].method)
        assertEquals("show_presence", requests[0].url.queryParameter("fields"))
        assertEquals("PATCH", requests[1].method)
        assertEquals("/api/v1/me/prefs", requests[1].url.encodedPath)
        assertEquals("Bearer pinned-token", requests[1].header("Authorization"))
        assertEquals("application/json", requests[1].body!!.contentType()!!.toString().substringBefore(';'))
        assertEquals("""{"show_presence":false}""", bodies[1])
    }

    @Test fun writeFactoryDoesNotRetryOrFollowRedirects() {
        val client = SubshellOkHttpClientFactory.createNonRetryingWriteClient()
        assertFalse(client.retryOnConnectionFailure)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }
}
