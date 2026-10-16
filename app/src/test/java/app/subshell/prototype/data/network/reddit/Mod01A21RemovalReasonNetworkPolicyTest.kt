package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mod01A21RemovalReasonNetworkPolicyTest {
    @Test
    fun removalReasonsUseClassicCommunityEndpointAndPreserveConfiguredOrder() {
        val request = redditRemovalReasonsRequest("SubShell")
        assertEquals(
            listOf("api", "v1", "subshell", "removal_reasons"),
            request.pathSegments,
        )

        val dto = RedditRemovalReasonsDto(
            order = listOf("reason-b", "reason-a"),
            data = mapOf(
                "reason-a" to RedditRemovalReasonDto(
                    id = "reason-a",
                    title = "Rule 1",
                    message = "First reason",
                ),
                "reason-b" to RedditRemovalReasonDto(
                    id = "reason-b",
                    title = "Rule 2",
                    message = "Second reason",
                ),
            ),
        )
        assertEquals(listOf("reason-b", "reason-a"), dto.toDomain().map { it.id })
    }

    @Test
    fun removalReasonAttachmentUsesModactionsEndpointAndBoundedPayload() {
        val request = redditAttachRemovalReasonRequest(
            thingFullname = "t1_abc123",
            reasonId = "reason-id",
            modNote = "mod note",
        )
        assertEquals(
            listOf("api", "v1", "modactions", "removal_reasons"),
            request.pathSegments,
        )
        val payload = request.fields.toMap().getValue("json")
        assertEquals("json", request.fields.toMap().getValue("api_type"))
        assertTrue(payload.contains("t1_abc123"))
        assertTrue(payload.contains("reason-id"))
        assertTrue(payload.contains("mod note"))
    }

    // STEP3 (mod01a21): the mod note is cut to 100 characters and no reason
    // is sent as JSON null, not an empty id.
    @Test
    fun theModNoteIsBoundedAndNoReasonIsNull() {
        val payload = redditAttachRemovalReasonRequest(
            thingFullname = "t3_abc123",
            reasonId = "  ",
            modNote = "n".repeat(150),
        ).fields.toMap().getValue("json")

        assertTrue(payload, payload.contains("\"mod_note\":\"${"n".repeat(100)}\""))
        assertTrue(payload, !payload.contains("n".repeat(101)))
        assertTrue(payload, payload.contains("\"reason_id\":null"))
    }
}
