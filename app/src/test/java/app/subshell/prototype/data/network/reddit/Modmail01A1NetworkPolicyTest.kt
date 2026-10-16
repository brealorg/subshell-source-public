package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail01A1NetworkPolicyTest {
    @Test
    fun newModmailReadRequestsUseCanonicalEndpointsWithoutReadMutation() {
        val communities = redditModmailSubredditsRequest()
        assertEquals(
            listOf("api", "mod", "conversations", "subreddits"),
            communities.pathSegments,
        )

        val conversations = redditModmailConversationsRequest(
            after = "abc123",
            limit = 50,
        )
        assertEquals(
            listOf("api", "mod", "conversations"),
            conversations.pathSegments,
        )
        assertTrue(conversations.queryParameters.contains("state" to "all"))
        assertTrue(conversations.queryParameters.contains("sort" to "recent"))
        assertTrue(conversations.queryParameters.contains("after" to "abc123"))
        assertTrue(conversations.queryParameters.contains("limit" to "50"))

        val detail = redditModmailConversationRequest("abc123")
        assertEquals(
            listOf("api", "mod", "conversations", "abc123"),
            detail.pathSegments,
        )
        assertTrue(detail.queryParameters.contains("markRead" to "false"))
    }

    @Test
    fun conversationListingPreservesServerOrderAndProjectsUnreadMetadata() {
        val dto = SubshellNetworkJson.providerJson.decodeFromString(
            RedditModmailConversationsDto.serializer(),
            """
            {
              "conversationIds": ["ModmailConversation_second", "ModmailConversation_first"],
              "conversations": {
                "ModmailConversation_first": {
                  "id": "ModmailConversation_first",
                  "subject": "First subject",
                  "participant": {"name": "alice"},
                  "subreddit": {"displayName": "subshell"},
                  "lastUpdated": "2026-08-26T18:00:00Z",
                  "lastUnread": null,
                  "numMessages": 1,
                  "messages": {
                    "ModmailMessage_m1": {
                      "id": "ModmailMessage_m1",
                      "author": {"name": "alice"},
                      "bodyMarkdown": "First body",
                      "date": "2026-08-26T18:00:00Z"
                    }
                  }
                },
                "ModmailConversation_second": {
                  "id": "ModmailConversation_second",
                  "subject": "Second subject",
                  "participant": {"name": "bob"},
                  "subreddit": {"displayName": "android"},
                  "lastUpdated": "2026-08-26T19:00:00Z",
                  "lastUnread": "2026-08-26T18:30:00Z",
                  "numMessages": 2,
                  "messages": {
                    "ModmailMessage_m2": {
                      "id": "ModmailMessage_m2",
                      "author": {"name": "bob"},
                      "bodyMarkdown": "Latest body",
                      "date": "2026-08-26T19:00:00Z"
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val page = dto.toConversationPage(limit = 50)
        assertEquals(listOf("second", "first"), page.items.map { it.id })
        assertEquals(listOf("android", "subshell"), page.items.map { it.communityName })
        assertTrue(page.items.first().unread)
        assertFalse(page.items.last().unread)
        assertEquals("Latest body", page.items.first().preview)
        assertNull(page.nextCursor)
    }

    @Test
    fun threadProjectionIsReadOnlyAndOrdersMessagesChronologically() {
        val conversation = RedditModmailConversationDto(
            id = "ModmailConversation_abc123",
            subject = "Need help",
            participant = RedditModmailParticipantDto(name = "reader"),
            subreddit = RedditModmailSubredditDto(displayName = "subshell"),
            numMessages = 2,
            messages = linkedMapOf(
                "ModmailMessage_later" to RedditModmailMessageDto(
                    id = "ModmailMessage_later",
                    author = RedditModmailParticipantDto(name = "mod-one", isMod = true),
                    bodyMarkdown = "Later",
                    date = "2026-08-26T20:00:00Z",
                    participatingAs = "subreddit",
                ),
                "ModmailMessage_earlier" to RedditModmailMessageDto(
                    id = "ModmailMessage_earlier",
                    author = RedditModmailParticipantDto(name = "reader"),
                    bodyMarkdown = "Earlier",
                    date = "2026-08-26T19:00:00Z",
                ),
            ),
        )

        val thread = conversation.toThread("abc123")
        assertEquals("abc123", thread.conversation.id)
        assertEquals(listOf("earlier", "later"), thread.messages.map { it.id })
        assertEquals(listOf("Earlier", "Later"), thread.messages.map { it.body })
        assertEquals("subreddit", thread.messages.last().participatingAs)
    }
}
