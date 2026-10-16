package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail01A1SubredditResponseParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun newModmailSubredditsWrapperDecodesDisplayNameAndLastUpdated() {
        val payload = """
            {
              "subreddits": {
                "t5_alpha": {
                  "display_name": "AlphaMods",
                  "lastUpdated": "2026-08-27T00:00:00.000Z"
                },
                "t5_beta": {
                  "display_name": "BetaMods",
                  "lastUpdated": "2026-08-26T00:00:00.000Z"
                }
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString(
            RedditModmailSubredditsResponseDto.serializer(),
            payload,
        )

        assertEquals(setOf("t5_alpha", "t5_beta"), decoded.subreddits.keys)
        assertEquals("AlphaMods", decoded.subreddits.getValue("t5_alpha").displayName)
        assertEquals(
            "2026-08-27T00:00:00.000Z",
            decoded.subreddits.getValue("t5_alpha").lastUpdated,
        )
    }

    @Test
    fun emptyWrapperDecodesAsNoMailCommunitiesWithoutProtocolFailure() {
        val decoded = json.decodeFromString(
            RedditModmailSubredditsResponseDto.serializer(),
            """{"subreddits":{}}""",
        )

        assertTrue(decoded.subreddits.isEmpty())
    }
}
