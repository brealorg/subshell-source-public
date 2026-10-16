package app.subshell.prototype.data.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NET04 (#144): a form field of non-Latin text is form-encoded at up to 9
 * bytes per character. The longest texts the app allows (a 40 000-character
 * post body, a 100 000-character richtext_json) must reach Reddit instead of
 * failing the client's size check before sending.
 */
class Net04FormBodyLimitTest {
    @Test
    fun theLongestAllowedNonLatinTextsAreSent() = runBlocking {
        for (length in listOf(40_000, 100_000)) {
            var sentBytes = 0L
            val client = SubshellHttpClient(
                callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                    sentBytes = chain.request().body!!.contentLength()
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }.build(),
                endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-test",
            )

            val result = client.postFormJson(
                FormPostRequest(
                    pathSegments = listOf("api", "editusertext"),
                    fields = listOf("thing_id" to "t3_abc", "text" to "日".repeat(length)),
                ),
                JsonElement.serializer(),
            )

            assertTrue("$length: $result", result is NetworkResult.Success)
            assertEquals(length * 9L + "thing_id=t3_abc&text=".length, sentBytes)
        }
    }
}
