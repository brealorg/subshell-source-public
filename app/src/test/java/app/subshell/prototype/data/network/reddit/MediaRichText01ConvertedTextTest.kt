package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MEDIARICHTEXT01 (#145): a comment or message reply with an uploaded image
 * sent its text as one literal RTJSON text node, so markdown showed raw. The
 * text now goes through Reddit's converter and the image block is appended;
 * a failed conversion sends nothing.
 */
class MediaRichText01ConvertedTextTest {
    private val convertedParagraph =
        """{"e":"par","c":[{"e":"text","t":"Look at this","f":[[1,0,4]]}]}"""

    @Test
    fun theTextIsConvertedByRedditAndTheImageIsAppended() = runBlocking {
        val requests = mutableListOf<String>()
        val richtext = prepareRedditInlineMediaRichText(
            httpClient = client(200, """{"output":{"document":[$convertedParagraph]}}""", requests),
            text = " **Look** at this ",
            assetId = "AbC123",
        )

        assertEquals(1, requests.size)
        assertTrue(requests.single().startsWith("/api/convert_rte_body_format "))
        val form = URLDecoder.decode(requests.single(), "UTF-8")
        assertTrue(form, form.contains("markdown_text=**Look** at this"))
        assertTrue(requests.single().contains("output_mode=rtjson"))
        val document = Json.parseToJsonElement(richtext!!).let { it as JsonObject }["document"] as JsonArray
        assertEquals(Json.parseToJsonElement(convertedParagraph), document[0])
        assertEquals(Json.parseToJsonElement("""{"e":"img","id":"abc123"}"""), document[1])
        assertEquals(2, document.size)
    }

    @Test
    fun aFailedConversionSendsNothingAndAnImageOnlyReplyNeedsNoConversion() = runBlocking {
        for ((code, body) in listOf(500 to "{}", 200 to """{"output":{"document":[]}}""", 200 to "{}")) {
            assertNull("$code $body", prepareRedditInlineMediaRichText(client(code, body), "**Look**", "abc123"))
        }
        val requests = mutableListOf<String>()
        val imageOnly = prepareRedditInlineMediaRichText(client(500, "{}", requests), "  ", "abc123")
        assertEquals(0, requests.size)
        assertEquals("""{"document":[{"e":"img","id":"abc123"}]}""", imageOnly)
    }

    private fun client(code: Int, body: String, requests: MutableList<String> = mutableListOf()) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val form = Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
            requests += "${chain.request().url.encodedPath} $form"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
    )
}
