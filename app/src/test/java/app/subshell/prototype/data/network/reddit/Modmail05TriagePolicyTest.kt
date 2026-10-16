package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.auth.reddit.redditModmailHighlightPathSegments
import app.subshell.prototype.data.auth.reddit.redditModmailMuteFormRequest
import app.subshell.prototype.data.auth.reddit.redditModmailMutePathSegments
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Modmail05TriagePolicyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun officialTriageQueueStatesAreEncodedExactly() {
        val expectedStates = listOf(
            "all",
            "new",
            "inprogress",
            "filtered",
            "highlighted",
            "archived",
            "appeals",
            "join_requests",
            "mod",
            "notifications",
        )

        expectedStates.forEach { expected ->
            val request = redditModmailConversationsRequest(state = expected)
            assertEquals(
                expected,
                request.queryParameters.single { (name, _) -> name == "state" }.second,
            )
        }
    }

    @Test
    fun highlightedAndParticipantMuteStatusProjectFromThreadPayload() {
        val payload = """
            {
              "conversation": {
                "id": "triage1",
                "subject": "Triage fixture",
                "participant": {"name": "fixture_user"},
                "owner": {"displayName": "subshell", "name": "t5_fixture"},
                "isHighlighted": true,
                "state": 1
              },
              "messages": {},
              "user": {
                "muteStatus": {
                  "isMuted": true,
                  "endDate": "2026-09-01T12:00:00Z",
                  "muteCount": 2
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString(
            RedditModmailConversationResponseDto.serializer(),
            payload,
        )
        val thread = requireNotNull(response.conversation).toThread(
            fallbackId = "triage1",
            topLevelMessages = response.messages,
            participantUser = response.user,
        )

        assertTrue(thread.conversation.highlighted)
        assertFalse(thread.conversation.archived)
        val mute = requireNotNull(thread.participantMuteStatus)
        assertTrue(mute.muted)
        assertNotNull(mute.endEpochSeconds)
        assertEquals(2, mute.muteCount)
    }

    @Test
    fun highlightAndMuteRequestsMatchRedditConversationActions() {
        assertEquals(
            listOf("api", "mod", "conversations", "abc123", "highlight"),
            redditModmailHighlightPathSegments("abc123"),
        )
        assertEquals(
            listOf("api", "mod", "conversations", "abc123", "unmute"),
            redditModmailMutePathSegments("abc123", muted = false),
        )

        listOf(72, 168, 672).forEach { hours ->
            val request = redditModmailMuteFormRequest("abc123", hours)
            assertEquals(
                listOf("api", "mod", "conversations", "abc123", "mute"),
                request.pathSegments,
            )
            assertEquals(listOf("num_hours" to hours.toString()), request.fields)
        }

    }

}
