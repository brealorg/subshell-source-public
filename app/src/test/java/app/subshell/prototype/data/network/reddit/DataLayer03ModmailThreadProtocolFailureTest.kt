package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
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
 * DATALAYER03: opening a modmail thread whose payload Subshell cannot map
 * (no conversation, or an owner it cannot normalise, such as a profile
 * community with a hyphen) returns a protocol failure instead of throwing
 * out of the caller's coroutine.
 */
class DataLayer03ModmailThreadProtocolFailureTest {
    private fun open(body: String): NetworkResult<RedditModmailThread> = runBlocking {
        val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        RedditModmailNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https("https://example.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-unit-test",
                bearerTokenProvider = NetworkBearerTokenProvider { "token" },
            ),
        ).conversation("thread1")
    }

    private fun conversation(ownerDisplayName: String) = """
        {"conversation":{"id":"thread1","subject":"hello",
          "participant":{"name":"someone","isDeleted":false,"isMod":false},
          "owner":{"displayName":"$ownerDisplayName","name":"t5_fixture"},
          "objIds":[{"id":"msg1","key":"messages"}],
          "lastUpdated":"2026-08-27T10:04:00.000Z","numMessages":1,"isInternal":false},
         "messages":{"msg1":{"id":"msg1","author":{"name":"someone","isDeleted":false,"isMod":false},
          "bodyMarkdown":"Hi","date":"2026-08-27T10:03:00.000Z","isInternal":false,"participatingAs":"user"}}}
    """.trimIndent()

    @Test
    fun aPayloadWithoutAConversationIsAProtocolFailure() {
        assertEquals(
            NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON),
            open("""{"messages":{}}"""),
        )
    }

    @Test
    fun anOwnerSubshellCannotNormaliseIsAProtocolFailure() {
        assertEquals(
            NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON),
            open(conversation(ownerDisplayName = "u_foo-bar")),
        )
    }

    @Test
    fun aCompleteThreadStillOpens() {
        val result = open(conversation(ownerDisplayName = "subshell"))
        assertTrue(result.toString(), result is NetworkResult.Success)
        val thread = (result as NetworkResult.Success).value
        assertEquals("thread1", thread.conversation.id)
        assertEquals(listOf("Hi"), thread.messages.map { it.body })
    }
}
