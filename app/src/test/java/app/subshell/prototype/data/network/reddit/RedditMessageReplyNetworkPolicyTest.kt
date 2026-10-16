package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditMessageReplyNetworkPolicyTest {
    @Test
    fun privateMessageReplyUsesCommentEndpointWithStrictT4Target() {
        val request = redditPrivateMessageReplyFormRequest(
            parentFullname = " T4_AbC123 ",
            text = "  reply text  ",
        )

        assertEquals(listOf("api", "comment"), request.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "raw_json" to "1",
                "thing_id" to "t4_abc123",
                "text" to "reply text",
            ),
            request.fields,
        )
    }

    @Test
    fun directChatMediaReplyUsesRichtextJsonOnTheSameStrictT4Endpoint() {
        val richtext = """{"document":[{"e":"img","id":"asset123"}]}"""
        val request = redditPrivateMessageReplyFormRequest(
            parentFullname = "t4_chat123",
            text = "caption",
            richtextJson = richtext,
        )

        assertEquals(listOf("api", "comment"), request.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "raw_json" to "1",
                "thing_id" to "t4_chat123",
                "richtext_json" to richtext,
            ),
            request.fields,
        )
        assertTrue(request.fields.none { (name, _) -> name == "text" })
    }

    @Test
    fun privateMessageReplyRejectsCommentAndPostParents() {
        assertTrue(runCatching { normalizeRedditPrivateMessageFullname("t1_abc") }.isFailure)
        assertTrue(runCatching { normalizeRedditPrivateMessageFullname("t3_abc") }.isFailure)
    }

    @Test
    fun providerCodeExtractionIsBoundedAndSanitized() {
        val ratelimit = Json.parseToJsonElement("[\"RATELIMIT\",\"slow down\",\"text\"]")
        val unsafe = Json.parseToJsonElement("[\"bad-code!\",\"no\",\"text\"]")
        assertEquals("RATELIMIT", redditMessageReplyProviderCode(ratelimit))
        assertNull(redditMessageReplyProviderCode(unsafe))
    }
}
