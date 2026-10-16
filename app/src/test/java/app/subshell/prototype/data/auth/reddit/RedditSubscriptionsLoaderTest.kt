package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.RedditSubscribedCommunity
import app.subshell.prototype.data.network.reddit.RedditSubscriptionPageSource
import app.subshell.prototype.model.Community
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditSubscriptionsLoaderTest {
    @Test
    fun allCursorPagesAreDeduplicatedAndSorted() = runBlocking {
        val source = RecordingSubscriptionSource(
            mutableListOf(
                success(
                    communities = listOf(community("t5_b", "beta"), community("t5_a", "Alpha")),
                    nextCursor = "t5_after_1",
                ),
                success(
                    communities = listOf(community("t5_a-copy", "alpha"), community("t5_c", "car")),
                    nextCursor = null,
                ),
            ),
        )

        val result = loadAllRedditSubscriptions(source)

        assertTrue(result is NetworkResult.Success)
        assertEquals(
            listOf("Alpha", "beta", "car"),
            (result as NetworkResult.Success<RedditSubscriptionList>).value.communities.map(Community::name),
        )
        assertEquals(listOf(null, "t5_after_1"), source.requestedCursors)
        assertEquals(listOf(100, 100), source.requestedLimits)
    }

    @Test
    fun repeatedRemoteCursorIsAProtocolFailure() = runBlocking {
        val source = RecordingSubscriptionSource(
            mutableListOf(
                success(emptyList(), "same"),
                success(emptyList(), "same"),
            ),
        )

        val result = loadAllRedditSubscriptions(source)

        assertEquals(
            NetworkProtocolFailureKind.MALFORMED_JSON,
            (result as NetworkResult.ProtocolFailure).kind,
        )
    }

    private fun community(id: String, name: String): Community = Community(
        id = id,
        name = name,
        description = "",
        members = "—",
        accent = 0xFF6750A4L,
        joined = true,
    )

    private fun success(
        communities: List<Community>,
        nextCursor: String?,
    ): NetworkResult.Success<NetworkPage<RedditSubscribedCommunity>> = NetworkResult.Success(
        value = NetworkPage(communities.map { community -> RedditSubscribedCommunity(community, favorite = false) }, nextCursor),
        metadata = NetworkResponseMetadata(
            statusCode = 200,
            receivedAtEpochMs = 1L,
            rateLimit = NetworkRateLimit(null, null, null),
            retryAfterMs = null,
        ),
    )
}

private class RecordingSubscriptionSource(
    private val results: MutableList<NetworkResult<NetworkPage<RedditSubscribedCommunity>>>,
) : RedditSubscriptionPageSource {
    val requestedCursors = mutableListOf<String?>()
    val requestedLimits = mutableListOf<Int>()

    override suspend fun page(
        cursor: String?,
        limit: Int,
    ): NetworkResult<NetworkPage<RedditSubscribedCommunity>> {
        requestedCursors += cursor
        requestedLimits += limit
        return results.removeAt(0)
    }
}
