package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
import app.subshell.prototype.data.auth.REDDIT_PEOPLE_SEARCH_PAGE_SIZE
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.RedditPeopleSearchPageSource
import app.subshell.prototype.model.RedditPeopleSearchItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditPeopleSearchLoaderTest {
    @Test
    fun targetNormalizesQueryAndUsesBoundedFirstPage() {
        val target = AuthenticatedRedditPeopleSearchTarget.search(
            rawQuery = "  Android   developer ",
        )

        assertEquals("Android developer", target.searchQuery)
        assertEquals(REDDIT_PEOPLE_SEARCH_PAGE_SIZE, target.pageSize)
    }

    @Test
    fun oneLivePagePreservesRedditOrderAndDeduplicatesUsernames() = runBlocking {
        val source = RecordingPeopleSearchSource(
            NetworkResult.Success(
                value = NetworkPage(
                    items = listOf(
                        item("AndroidDev"),
                        item("KotlinDev"),
                        item("androiddev"),
                    ),
                    nextCursor = "t5_ignored_in_c2",
                ),
                metadata = metadata(),
            ),
        )

        val result = loadRedditPeopleSearch(
            source = source,
            target = AuthenticatedRedditPeopleSearchTarget.search("developer"),
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals(
            listOf("AndroidDev", "KotlinDev"),
            (result as NetworkResult.Success<List<RedditPeopleSearchItem>>)
                .value
                .map { value -> value.username },
        )
        assertEquals(listOf("developer"), source.queries)
        assertEquals(listOf(null), source.cursors)
        assertEquals(listOf(REDDIT_PEOPLE_SEARCH_PAGE_SIZE), source.limits)
    }

    private fun item(username: String): RedditPeopleSearchItem =
        RedditPeopleSearchItem(
            username = username,
            title = "",
            description = "",
        )

    private fun metadata(): NetworkResponseMetadata = NetworkResponseMetadata(
        statusCode = 200,
        receivedAtEpochMs = 1L,
        rateLimit = NetworkRateLimit(null, null, null),
        retryAfterMs = null,
    )
}

private class RecordingPeopleSearchSource(
    private val result: NetworkResult<NetworkPage<RedditPeopleSearchItem>>,
) : RedditPeopleSearchPageSource {
    val queries = mutableListOf<String>()
    val cursors = mutableListOf<String?>()
    val limits = mutableListOf<Int>()

    override suspend fun page(
        searchQuery: String,
        cursor: String?,
        limit: Int,
    ): NetworkResult<NetworkPage<RedditPeopleSearchItem>> {
        queries += searchQuery
        cursors += cursor
        limits += limit
        return result
    }
}
