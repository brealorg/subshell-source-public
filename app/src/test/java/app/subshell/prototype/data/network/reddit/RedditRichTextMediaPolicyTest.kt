package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditRichTextMediaPolicyTest {
    @Test
    fun inlineMediaRtjsonUsesRedditAssetIdentityAndNeverTheUploadObjectUrl() {
        val encoded = redditInlineMediaRichTextJson(
            text = " Caption ",
            assetId = "AbC123",
        )
        val root = Json.parseToJsonElement(encoded).jsonObject
        val document = root.getValue("document").jsonArray

        assertEquals("par", document[0].jsonObject.getValue("e").jsonPrimitive.content)
        val textNode = document[0].jsonObject.getValue("c").jsonArray[0].jsonObject
        assertEquals("text", textNode.getValue("e").jsonPrimitive.content)
        assertEquals("Caption", textNode.getValue("t").jsonPrimitive.content)
        assertEquals("img", document[1].jsonObject.getValue("e").jsonPrimitive.content)
        assertEquals("abc123", document[1].jsonObject.getValue("id").jsonPrimitive.content)
        assertFalse(encoded.contains("mediaUrl"))
        assertFalse(encoded.contains("reddit-uploaded-media.s3"))
    }

    @Test
    fun mediaOnlyRtjsonContainsOnlyTheImageNode() {
        val encoded = redditInlineMediaRichTextJson(text = " ", assetId = "asset42")
        val document = Json.parseToJsonElement(encoded)
            .jsonObject
            .getValue("document")
            .jsonArray

        assertEquals(1, document.size)
        assertEquals("img", document.single().jsonObject.getValue("e").jsonPrimitive.content)
        assertEquals("asset42", document.single().jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun invalidAssetIdentityIsRejectedBeforeFinalSubmit() {
        listOf("", "asset-id", "https://example.invalid/x", "x".repeat(65)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                normalizeRedditInlineMediaAssetId(value)
            }
        }
        assertTrue(runCatching { normalizeRedditInlineMediaAssetId("AbC123") }.isSuccess)
    }
}
