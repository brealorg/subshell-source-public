package app.subshell.prototype.data.cache

import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.FlairRichTextSegment
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #44: post flair rich text is the post payload's field 36 and the
 * crosspost payload's field 24. Payloads written before it (36 and 24
 * fields) still decode, with no rich text.
 */
class Issue44PostFlairRichTextCacheTest {
    private val richText = listOf(
        FlairRichTextSegment(
            elementType = "emoji",
            emojiRef = ":upvote:",
            imageUrl = "https://emoji.redditmedia.com/upvote.png",
        ),
        FlairRichTextSegment(elementType = "text", text = "Outfit Check"),
    )

    private val source = CrosspostPreview(
        community = "ratemyfitsfw",
        author = "source_author",
        title = "Source",
        score = 3,
        id = "t3_source",
        flair = ":upvote:Outfit Check",
        flairRichText = richText,
    )

    private val post = Post(
        id = "t3_1wqy991",
        community = "ratemyfitsfw",
        author = "poster",
        age = "1h",
        title = "Can I still pull this off?",
        score = 12,
        comments = 4,
        kind = PostKind.CROSSPOST,
        crosspost = source,
        flair = ":upvote:Outfit Check",
        flairRichText = richText,
    )

    @Test
    fun postAndCrosspostFlairRichTextSurviveTheCache() {
        assertEquals(post, SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(post)))
    }

    @Test
    fun payloadsWrittenBeforeTheFieldStillDecodeWithoutRichText() {
        val fields = SubshellCacheCodec.decodeMutationFields(SubshellCacheCodec.encodePost(post), expectedSize = 37)
        val crosspostIndex = 16
        val crosspostFields =
            SubshellCacheCodec.decodeMutationFields(requireNotNull(fields[crosspostIndex]), expectedSize = 25)

        val historicalCrosspost = SubshellCacheCodec.encodeMutationFields(*crosspostFields.take(24).toTypedArray())
        val historical = SubshellCacheCodec.encodeMutationFields(
            *fields.take(36)
                .mapIndexed { index, value -> if (index == crosspostIndex) historicalCrosspost else value }
                .toTypedArray(),
        )

        assertEquals(
            post.copy(flairRichText = emptyList(), crosspost = source.copy(flairRichText = emptyList())),
            SubshellCacheCodec.decodePost(historical),
        )
    }
}
