package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02E2AMatrixReactionWriterPolicyTest {
    @Test
    fun reactionPathUsesStandardMatrixSendEventRoute() {
        assertEquals(
            listOf("_matrix", "client", "v3", "rooms", "!room:reddit.com", "send", "m.reaction", "txn"),
            redditChatMatrixReactionPathSegments("!room:reddit.com", "txn"),
        )
    }

    @Test
    fun reactionPayloadMatchesRuntimeProvenRedditWebAnnotationShape() {
        val root = Json.parseToJsonElement(
            redditChatMatrixReactionPayload("\$target", "g6akcitga7081.gif"),
        ).jsonObject
        val relation = root.getValue("m.relates_to").jsonObject
        assertEquals("\$target", relation.getValue("event_id").jsonPrimitive.content)
        assertEquals("g6akcitga7081.gif", relation.getValue("key").jsonPrimitive.content)
        assertEquals("m.annotation", relation.getValue("rel_type").jsonPrimitive.content)
        assertEquals(1, root.size)
    }

    @Test
    fun reactionKeyAcceptsOnlyCapturedRedditCatalogAssets() {
        assertEquals(
            "g6akcitga7081.gif",
            redditChatMatrixReactionKeyOrNull("g6akcitga7081.gif"),
        )
        assertEquals(
            "ytv3x0sfa7081.png",
            redditChatMatrixReactionKeyOrNull(" ytv3x0sfa7081.png "),
        )
        assertNull(redditChatMatrixReactionKeyOrNull(""))
        assertNull(redditChatMatrixReactionKeyOrNull("🔥"))
        assertNull(redditChatMatrixReactionKeyOrNull("unknown.gif"))
        assertNull(redditChatMatrixReactionKeyOrNull("g6akcitga7081.gif\nsecret"))
    }
}
