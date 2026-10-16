package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.FlairRichTextSegment
import app.subshell.prototype.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #44: post flair emoji from link_flair_richtext, for posts and crosspost sources. */
class Issue44PostFlairRichTextTest {
    private val json = SubshellNetworkJson.providerJson

    private val upvote = FlairRichTextSegment(
        elementType = "emoji",
        emojiRef = ":upvote:",
        imageUrl = "https://emoji.redditmedia.com/upvote.png",
    )
    private val outfitCheck = FlairRichTextSegment(elementType = "text", text = "Outfit Check")

    private val richText =
        """[{"e":"emoji","a":":upvote:","u":"https://emoji.redditmedia.com/upvote.png"},""" +
            """{"e":"text","t":"Outfit Check"}]"""

    private fun post(fields: String): Post =
        json.decodeFromString<RedditListingDto>(
            """
            {"kind":"Listing","data":{"children":[{"kind":"t3","data":{
              "id":"1wqy991","subreddit":"RateMyFitSFW","title":"Can I still pull this off?",
              $fields
            }}]}}
            """.trimIndent(),
        ).toNetworkPage(nowEpochSeconds = 0L).items.single()

    @Test
    fun anEmojiFlairKeepsItsTextAndGainsItsSegments() {
        val post = post(
            """"link_flair_text":":upvote:Outfit Check","link_flair_richtext":$richText""",
        )

        assertEquals(":upvote:Outfit Check", post.flair)
        assertEquals(listOf(upvote, outfitCheck), post.flairRichText)
    }

    @Test
    fun aPlainFlairWithoutRichTextIsUnchanged() {
        val post = post(""""link_flair_text":"Outfit Check"""")

        assertEquals("Outfit Check", post.flair)
        assertEquals(emptyList<FlairRichTextSegment>(), post.flairRichText)
    }

    @Test
    fun richTextWithoutFlairTextIsIgnored() {
        val post = post(""""link_flair_richtext":$richText""")

        assertNull(post.flair)
        assertEquals(emptyList<FlairRichTextSegment>(), post.flairRichText)
    }

    @Test
    fun anEmojiUrlThatIsNotHttpsIsDropped() {
        val post = post(
            """"link_flair_text":":upvote:Outfit Check",""" +
                """"link_flair_richtext":[{"e":"emoji","a":":upvote:","u":"http://emoji.redditmedia.com/upvote.png"}]""",
        )

        assertNull(post.flairRichText.single().imageUrl)
    }

    @Test
    fun aCrosspostSourceKeepsItsFlairSegments() {
        val post = post(
            """
            "crosspost_parent_list":[{"id":"source","subreddit":"RateMyFitSFW","title":"Source",
              "link_flair_text":":upvote:Outfit Check","link_flair_richtext":$richText}]
            """.trimIndent(),
        )

        val source = requireNotNull(post.crosspost)
        assertEquals(":upvote:Outfit Check", source.flair)
        assertEquals(listOf(upvote, outfitCheck), source.flairRichText)
    }
}
