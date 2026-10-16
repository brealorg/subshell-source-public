package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.redditMediaEditMarkdown
import app.subshell.prototype.data.network.reddit.redditMediaEditRichTextJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RTJSONOFFSETS01 (OCB-640): Reddit counts RTJSON "f" ranges in code points;
 * an emoji before a media token moved the bold after it one character early.
 */
class RtjsonOffsets01EmojiBeforeMediaTest {
    private val json = Json

    @Test
    fun boldAfterAGifStaysOnItsWordWhenAnEmojiComesFirst() {
        val edit = redditMediaEditMarkdown("😀 ![gif](giphy|abc|downsized) **bold**")!!
        val token = edit.targets.keys.single()
        val text = "😀 $token bold"
        // Code points: the emoji is one, so "bold" starts at 2 + token + 1.
        val boldStart = 2 + token.length + 1
        val document = json.parseToJsonElement(
            """[{"e":"par","c":[{"e":"text","t":"$text","f":[[1,$boldStart,4]]}]}]""",
        ).jsonArray

        val richtext = redditMediaEditRichTextJson(document, edit) { "gif" }!!
        val paragraph = json.parseToJsonElement(richtext).jsonObject
            .getValue("document").jsonArray[0].jsonObject.getValue("c").jsonArray

        val after = paragraph[2].jsonObject
        assertEquals(" bold", after.getValue("t").jsonPrimitive.content)
        assertEquals("[[1,1,4]]", after.getValue("f").toString())
    }

    @Test
    fun aRangeBeforeTheMediaCountsTheEmojiAsOneCharacter() {
        val edit = redditMediaEditMarkdown("**😀 hi** ![gif](giphy|abc|downsized)")!!
        val token = edit.targets.keys.single()
        val text = "😀 hi $token"
        val document = json.parseToJsonElement(
            """[{"e":"par","c":[{"e":"text","t":"$text","f":[[1,0,4]]}]}]""",
        ).jsonArray

        val richtext = redditMediaEditRichTextJson(document, edit) { "gif" }!!
        val before = json.parseToJsonElement(richtext).jsonObject
            .getValue("document").jsonArray[0].jsonObject.getValue("c").jsonArray[0].jsonObject

        assertEquals("😀 hi ", before.getValue("t").jsonPrimitive.content)
        assertEquals("[[1,0,4]]", before.getValue("f").toString())
    }
}
