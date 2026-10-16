package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail01A1ThreadMessageParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun topLevelMessagesFollowConversationObjIdsAndProjectIntoThread() {
        val payload = """
            {
              "conversation": {
                "id": "thread1",
                "subject": "is this thing on",
                "participant": {
                  "name": "subshell_dev",
                  "isDeleted": false,
                  "isMod": false
                },
                "owner": {
                  "displayName": "subshell",
                  "name": "t5_fixture"
                },
                "objIds": [
                  {"id": "msg1", "key": "messages"},
                  {"id": "action1", "key": "modActions"},
                  {"id": "msg2", "key": "messages"}
                ],
                "lastUpdated": "2026-08-27T10:04:00.000Z",
                "lastUnread": "2026-08-27T10:04:00.000Z",
                "numMessages": 2,
                "isInternal": false
              },
              "messages": {
                "msg2": {
                  "id": "msg2",
                  "author": {
                    "name": "moderator_name",
                    "isDeleted": false,
                    "isMod": true
                  },
                  "bodyMarkdown": "Second message",
                  "date": "2026-08-27T10:04:00.000Z",
                  "isInternal": true,
                  "participatingAs": "subreddit"
                },
                "msg1": {
                  "id": "msg1",
                  "author": {
                    "name": "subshell_dev",
                    "isDeleted": false,
                    "isMod": false
                  },
                  "bodyMarkdown": "First message",
                  "date": "2026-08-27T10:03:00.000Z",
                  "isInternal": false,
                  "participatingAs": "user"
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString(
            RedditModmailConversationResponseDto.serializer(),
            payload,
        )
        val thread = requireNotNull(response.conversation).toThread(
            fallbackId = "thread1",
            topLevelMessages = response.messages,
        )

        assertEquals("subshell", thread.conversation.communityName)
        assertEquals(2, thread.messages.size)

        assertEquals("msg1", thread.messages[0].id)
        assertEquals("subshell_dev", thread.messages[0].authorName)
        assertEquals("First message", thread.messages[0].body)
        assertFalse(thread.messages[0].isInternal)

        assertEquals("msg2", thread.messages[1].id)
        assertEquals("moderator_name", thread.messages[1].authorName)
        assertEquals("Second message", thread.messages[1].body)
        assertTrue(thread.messages[1].isInternal)
        assertEquals("subreddit", thread.messages[1].participatingAs)
    }

    @Test
    fun legacyNestedMessagesRemainSupportedWithoutObjIds() {
        val conversation = RedditModmailConversationDto(
            id = "legacy1",
            subject = "Legacy fixture",
            owner = RedditModmailSubredditDto(displayName = "subshell"),
            messages = mapOf(
                "later" to RedditModmailMessageDto(
                    id = "later",
                    bodyMarkdown = "Later",
                    date = "2026-08-27T10:02:00Z",
                ),
                "earlier" to RedditModmailMessageDto(
                    id = "earlier",
                    bodyMarkdown = "Earlier",
                    date = "2026-08-27T10:01:00Z",
                ),
            ),
        )

        val thread = conversation.toThread(fallbackId = "legacy1")

        assertEquals(listOf("earlier", "later"), thread.messages.map { it.id })
        assertEquals(listOf("Earlier", "Later"), thread.messages.map { it.body })
    }
}
