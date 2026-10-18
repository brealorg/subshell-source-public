package app.subshell.prototype.data.network

import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.error.toSafeTechnicalDetail
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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
 * DIAG03 (#202): a response kotlinx could not decode was only "Malformed
 * response". The field it failed on is kept, sanitized (no values, map keys
 * hidden), in the failure details, the error text and the diagnostics log;
 * diagnostics lines written before this field still read.
 */
class Diag03JsonFieldPathTest {
    @Serializable
    private data class Thing(val count: Int)

    @Serializable
    private data class Page(val data: Thing)

    @Test
    fun aDecodeFailureNamesTheFieldButNeverItsValue() = runBlocking {
        val result = client("""{"data":{"count":"top-secret-value"}}""")
            .getJson(JsonGetRequest(listOf("api", "thing")), Page.serializer())

        val failure = result as NetworkResult.ProtocolFailure
        assertEquals(NetworkProtocolFailureKind.MALFORMED_JSON, failure.kind)
        assertEquals("$.data.count", failure.jsonPath)
        val detail = result.failureDetailsOrNull()!!.toSafeTechnicalDetail()!!
        assertTrue(detail, detail.contains("Field $.data.count"))
        assertFalse(detail, detail.contains("top-secret-value"))
    }

    @Test
    fun thePathKeepsOnlyItsOwnCharactersAndHidesMapKeys() {
        assertEquals(
            "$.json.data.things[0].data.media_metadata[*].s",
            sanitizeJsonFieldPath("$.json.data.things[0].data.media_metadata['abc123'].s"),
        )
        assertEquals("$.a[*]", sanitizeJsonFieldPath("$.a[\"some key\"]"))
        assertNull(sanitizeJsonFieldPath("not a path"))
        assertNull(IllegalStateException("no path here").safeJsonFieldPath())
    }

    @Test
    fun diagnosticsKeepThePathAndStillReadOlderLines() {
        val directory = Files.createTempDirectory("diag03").toFile()
        try {
            val store = DiagnosticEventStore(directory = directory, maxEvents = 20, maxBytes = 8192)
            val details = NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, "$.data.count")
                .failureDetailsOrNull()
            store.recordViewportFailure("home", details, nowEpochMs = 1_000L)
            assertEquals("$.data.count", store.snapshot().events.single().failure?.jsonPath)

            // A line from before DIAG03 has its first 16 fields only (DIAG04 appended more after the path).
            val log = File(directory, "events-v1.log")
            val legacy = log.readLines().map { line ->
                if (line.startsWith("E|")) line.split('|').take(16).joinToString("|") else line
            }
            log.writeText(legacy.joinToString("\n", postfix = "\n"))
            val event = DiagnosticEventStore(directory = directory, maxEvents = 20, maxBytes = 8192)
                .snapshot().events.single()
            assertEquals("malformed_json", event.failure?.protocolKind)
            assertNull(event.failure?.jsonPath)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun client(body: String) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
    )
}
