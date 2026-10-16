package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Moderation02A4ModLogNetworkTest {
    @Test
    fun requestUsesCanonicalCommunityLogAndPreservesCaseSensitiveCursor() {
        val request = redditModerationLogRequest(
            rawCommunityName = "SubShell",
            after = "ModAction_ABC-def-123",
        )
        assertEquals(listOf("r", "subshell", "about", "log"), request.pathSegments)
        assertEquals(
            listOf(
                "limit" to "100",
                "raw_json" to "1",
                "after" to "ModAction_ABC-def-123",
            ),
            request.queryParameters,
        )
    }

    @Test
    fun typedListingPreservesUsefulActionContextAndPaging() {
        val listing = SubshellNetworkJson.providerJson
            .decodeFromString<RedditModerationLogListingDto>(
                """
                {
                  "data": {
                    "after": "ModAction_Next-ABC",
                    "children": [
                      {
                        "kind": "modaction",
                        "data": {
                          "id": "ModAction_One-ABC",
                          "mod": "b_real",
                          "action": "lock",
                          "subreddit": "subshell",
                          "target_author": "author",
                          "target_fullname": "t3_post",
                          "target_title": "A post title",
                          "target_permalink": "/r/subshell/comments/post/title/",
                          "details": "details",
                          "created_utc": 1234
                        }
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        val page = listing.toModeratorLogPage("subshell")
        assertEquals("ModAction_Next-ABC", page.nextCursor)
        val entry = page.items.single()
        assertEquals("ModAction_One-ABC", entry.id)
        assertEquals("b_real", entry.moderatorName)
        assertEquals("lock", entry.action)
        assertEquals("author", entry.targetAuthor)
        assertEquals("A post title", entry.targetTitle)
        assertEquals(1234L, entry.createdEpochSeconds)
    }

    @Test
    fun mismatchedCommunityAndMalformedActionsAreDropped() {
        fun item(id: String?, action: String?, subreddit: String) =
            RedditModerationLogThingDto(
                kind = "modaction",
                data = RedditModerationLogDataDto(
                    id = id,
                    action = action,
                    subreddit = subreddit,
                ),
            )
        val listing = RedditModerationLogListingDto(
            data = RedditModerationLogListingDataDto(
                children = listOf(
                    item("ModAction_ok", "lock", "subshell"),
                    item("ModAction_wrong", "lock", "android"),
                    item(null, "lock", "subshell"),
                    item("ModAction_missing_action", null, "subshell"),
                ),
            ),
        )
        val page = listing.toModeratorLogPage("subshell")
        assertEquals(listOf("ModAction_ok"), page.items.map { it.id })
        assertNull(page.nextCursor)
    }
}
