package app.subshell.prototype.data.cache

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.FlairRichTextSegment
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFlair01A3RichTextCachePolicyTest {
    private val richText = listOf(
        FlairRichTextSegment(
            elementType = "emoji",
            emojiRef = ":JackMiller:",
            imageUrl =
                "https://emoji.redditmedia.com/jackmiller.png",
        ),
        FlairRichTextSegment(
            elementType = "text",
            text = "Jack Miller",
        ),
    )

    @Test
    fun postAuthorRichTextSurvivesCacheRoundTrip() {
        val post = Post(
            id = "t3_richflair",
            community = "motogp",
            author = "rider",
            age = "1h",
            title = "Rich flair cache",
            score = 10,
            comments = 2,
            kind = PostKind.TEXT,
            authorFlairText = ":JackMiller: Jack Miller",
            authorFlairRichText = richText,
        )

        assertEquals(
            post,
            SubshellCacheCodec.decodePost(
                SubshellCacheCodec.encodePost(post),
            ),
        )
    }

    @Test
    fun commentAuthorRichTextSurvivesCacheRoundTrip() {
        val comment = Comment(
            id = "t1_richflair",
            author = "fan",
            age = "2h",
            body = "Rich flair comment",
            score = 4,
            depth = 0,
            accent = 0xFF6750A4L,
            authorFlairText = ":JackMiller: Jack Miller",
            authorFlairRichText = richText,
        )

        assertEquals(
            comment,
            SubshellCacheCodec.decodeComment(
                SubshellCacheCodec.encodeComment(comment),
            ),
        )
    }
}
