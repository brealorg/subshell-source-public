package app.subshell.prototype.data.cache

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFlair01A2AuthorFlairCachePolicyTest {
    @Test
    fun postAuthorFlairSurvivesCacheRoundTrip() {
        val post = Post(
            id = "t3_authorflair",
            community = "norge",
            author = "someone",
            age = "1h",
            title = "Cached flair post",
            score = 1,
            comments = 2,
            kind = PostKind.TEXT,
            authorFlairText = "Innlandet",
            authorFlairTextColor = "dark",
            authorFlairBackgroundColor = "#d6e4ff",
        )

        assertEquals(
            post,
            SubshellCacheCodec.decodePost(
                SubshellCacheCodec.encodePost(post),
            ),
        )
    }

    @Test
    fun commentAuthorFlairSurvivesCacheRoundTrip() {
        val comment = Comment(
            id = "t1_authorflair",
            author = "commenter",
            age = "2h",
            body = "Cached flair comment",
            score = 3,
            depth = 0,
            accent = 0xFF6750A4L,
            authorFlairText = "Oslo",
            authorFlairTextColor = "light",
            authorFlairBackgroundColor = "#123456",
        )

        assertEquals(
            comment,
            SubshellCacheCodec.decodeComment(
                SubshellCacheCodec.encodeComment(comment),
            ),
        )
    }
}
