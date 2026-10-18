package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LISTINGSKIP01_ONE_BAD_POST_DOES_NOT_FAIL_THE_PAGE_V1 (owner 2026-10-18): a
 * read listing leaves out a post it cannot read and keeps the rest and the
 * next-page cursor; a body that is not a listing still fails, and the strict
 * listing used for read-backs after a write still rejects the page.
 */
class ListingSkip01Test {
    private val json = SubshellNetworkJson.providerJson

    // The middle post has a poll_data shape no Reddit post can have.
    private val pageWithOneBadPost = """
        {
          "kind": "Listing",
          "data": {
            "after": "t3_next",
            "children": [
              {"kind": "t3", "data": {"id": "one", "title": "First"}},
              {"kind": "t3", "data": {"id": "bad", "title": "Broken", "poll_data": [1, 2]}},
              {"kind": "t3", "data": {"id": "three", "title": "Third"}}
            ]
          }
        }
    """.trimIndent()

    private fun httpClient(body: String) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("scripted")
                    .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            }
            .build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.com"),
        json = json,
        userAgent = "subshell-test/1.0",
    )

    @Test
    fun aReadListingLeavesOutThePostItCannotReadAndKeepsTheRest() {
        val page = json.decodeFromString(RedditReadListingDto.serializer(), pageWithOneBadPost)
            .toNetworkPage(json, nowEpochSeconds = 1_700_000_000L)

        assertEquals(listOf("one", "three"), page.items.map { it.id })
        assertEquals("t3_next", page.nextCursor)
    }

    @Test
    fun aBodyThatIsNotAListingStillFails() {
        val notAList = """{"kind": "Listing", "data": {"children": {"kind": "t3"}}}"""
        val failure = runCatching { json.decodeFromString(RedditReadListingDto.serializer(), notAList) }
            .exceptionOrNull()

        assertTrue(failure is SerializationException)
    }

    @Test
    fun theStrictListingForReadBacksStillRejectsThePage() {
        val failure = runCatching { json.decodeFromString(RedditListingDto.serializer(), pageWithOneBadPost) }
            .exceptionOrNull()

        assertTrue(failure is SerializationException)
    }

    @Test
    fun aFeedPageWithOneBadPostLoads() = runBlocking {
        val result = RedditListingNetworkDataSource(httpClient(pageWithOneBadPost)).posts()

        assertEquals(listOf("one", "three"), (result as NetworkResult.Success).value.items.map { it.id })
    }

    @Test
    fun anUpvotedHistoryPageWithOneBadPostLoads() = runBlocking {
        val result = RedditAccountHistoryNetworkDataSource(httpClient(pageWithOneBadPost))
            .posts("root", RedditAccountHistoryKind.UPVOTED, RepositoryPageRequest())

        assertEquals(listOf("one", "three"), (result as NetworkResult.Success).value.items.map { it.id })
    }

    @Test
    fun aFeedBodyThatIsNotAListingIsStillMalformed() = runBlocking {
        val result = RedditListingNetworkDataSource(httpClient("""{"data": {"children": 3}}""")).posts()

        assertEquals(NetworkProtocolFailureKind.MALFORMED_JSON, (result as NetworkResult.ProtocolFailure).kind)
    }
}
