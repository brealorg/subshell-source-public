package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.ui.components.modmailMessageAuthorLabel
import app.subshell.prototype.ui.components.modmailMessageSemanticRole
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail03A1R2HiddenAuthorProjectionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun hiddenModeratorAuthorProjectsAsCommunityWhileRetainingModeratorIdentity() {
        val payload = """
            {
              "conversation": {
                "id": "hidden1",
                "subject": "Hidden author fixture",
                "participant": {"name": "subshell_dev"},
                "owner": {"displayName": "subshell", "name": "t5_fixture"},
                "objIds": [{"id": "msg1", "key": "messages"}],
                "numMessages": 1
              },
              "messages": {
                "msg1": {
                  "id": "msg1",
                  "author": {
                    "name": "brealorg",
                    "isMod": true,
                    "isHidden": true
                  },
                  "bodyMarkdown": "Hidden reply",
                  "date": "2026-08-27T21:15:00Z",
                  "isInternal": false,
                  "participatingAs": "moderator"
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString(
            RedditModmailConversationResponseDto.serializer(),
            payload,
        )
        val thread = requireNotNull(response.conversation).toThread(
            fallbackId = "hidden1",
            topLevelMessages = response.messages,
        )
        val message = thread.messages.single()

        assertTrue(message.authorHidden)
        assertEquals("brealorg", message.authorName)
        assertEquals("r/subshell", modmailMessageAuthorLabel(message, "subshell"))
        assertTrue(
            requireNotNull(modmailMessageSemanticRole(message))
                .contains("u/brealorg · hidden from participant"),
        )
    }

    @Test
    fun visibleModeratorAuthorRemainsUserIdentity() {
        val dto = RedditModmailMessageDto(
            id = "msg2",
            author = RedditModmailParticipantDto(
                name = "brealorg",
                isMod = true,
                isHidden = false,
            ),
            bodyMarkdown = "Visible reply",
            participatingAs = "moderator",
        )
        val conversation = RedditModmailConversationDto(
            id = "visible1",
            subject = "Visible author fixture",
            owner = RedditModmailSubredditDto(displayName = "subshell"),
            messages = mapOf("msg2" to dto),
        )
        val message = conversation.toThread("visible1").messages.single()

        assertFalse(message.authorHidden)
        assertEquals("u/brealorg", modmailMessageAuthorLabel(message, "subshell"))
    }
}
