package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail01A1ConversationOwnerParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun conversationOwnerProjectsCommunityInsteadOfBeingDropped() {
        val payload = """
            {
              "conversationIds": ["abc123"],
              "conversations": {
                "abc123": {
                  "id": "abc123",
                  "subject": "A1R2 runtime fixture",
                  "participant": {
                    "name": "subshell_dev",
                    "isDeleted": false,
                    "isMod": false
                  },
                  "owner": {
                    "displayName": "subshell",
                    "name": "t5_fixture"
                  },
                  "messages": {},
                  "lastUpdated": "2026-08-27T09:15:00.000Z",
                  "lastUnread": "2026-08-27T09:15:00.000Z",
                  "numMessages": 1,
                  "isInternal": false,
                  "conversationType": "inprogress"
                }
              },
              "viewerId": "t2_moderator"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(
            RedditModmailConversationsDto.serializer(),
            payload,
        )
        val page = decoded.toConversationPage(limit = 50)

        assertEquals(1, page.items.size)
        val conversation = page.items.single()
        assertEquals("abc123", conversation.id)
        assertEquals("subshell", conversation.communityName)
        assertEquals("subshell_dev", conversation.participantName)
        assertEquals("A1R2 runtime fixture", conversation.subject)
        assertTrue(conversation.unread)
        assertFalse(conversation.isInternal)
    }

    @Test
    fun legacySubredditFieldRemainsAcceptedAsFallback() {
        val dto = RedditModmailConversationDto(
            id = "def456",
            subject = "Fallback",
            subreddit = RedditModmailSubredditDto(displayName = "legacycommunity"),
        )

        val response = RedditModmailConversationsDto(
            conversationIds = listOf("def456"),
            conversations = mapOf("def456" to dto),
        )
        val page = response.toConversationPage(limit = 50)

        assertEquals("legacycommunity", page.items.single().communityName)
    }
}
