package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.model.markdownLinkTargetsForRenderedText
import app.subshell.prototype.model.projectCommentContent
import app.subshell.prototype.model.redditSourceMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #24: a Giphy comment arrives as `![gif](giphy|<id>)` and Reddit's
 * media_metadata entry for it is a stub (status "invalid", no kind, no
 * source URL — observed on 25 comments of the reported thread), so the id in
 * the placeholder is the only address of the GIF. It is rewritten to the
 * canonical Giphy URL (as a tagged title since ISSUE8) and then travels the
 * ordinary inline-media path.
 */
class Issue24GiphyCommentMediaPolicyTest {
    private fun giphyBody(body: String): String =
        RedditCommentDataDto(body = body).bodyWithRedditMediaMetadata()

    @Test
    fun giphyPlaceholderResolvesToTheCanonicalGifUrl() {
        // ISSUE8_INLINE_REDDIT_MEDIA_V1: the placeholder stays as Reddit wrote
        // it (an edit sends it back) and carries the GIF address as a title.
        assertEquals(
            "![gif](giphy|Icp7SunbILYe4 \"subshell-media https://i.giphy.com/media/Icp7SunbILYe4/giphy.gif\")",
            giphyBody("![gif](giphy|Icp7SunbILYe4)"),
        )
        // A size hint is part of the placeholder, not of the address.
        assertEquals(
            "![gif](giphy|3o6ZtfeZA0LHdVStcA|downsized \"subshell-media https://i.giphy.com/media/3o6ZtfeZA0LHdVStcA/giphy.gif\")",
            giphyBody("![gif](giphy|3o6ZtfeZA0LHdVStcA|downsized)"),
        )
        // Text around the placeholder is kept, and the source comes back exactly.
        val around = giphyBody("before\n\n![gif](giphy|abc123)\n\nafter")
        assertTrue(around.startsWith("before\n\n![gif](giphy|abc123 "))
        assertTrue(around.endsWith("\n\nafter"))
        assertEquals("before\n\n![gif](giphy|abc123)\n\nafter", around.redditSourceMarkdown())
        // A native Reddit placeholder without metadata is left unresolved.
        assertEquals("![img](abc123)", giphyBody("![img](abc123)"))
    }

    @Test
    fun aGiphyCommentProjectsAsAnInlineGifLikeAnyOther() {
        val body = giphyBody("Caption\n\n![gif](giphy|Icp7SunbILYe4)")
        val projection = projectCommentContent(body)

        assertEquals("Caption", projection.displayText)
        assertEquals(1, projection.media.size)
        assertEquals(RemoteCommentMediaKind.GIF, projection.media.single().kind)
        assertEquals("https://i.giphy.com/media/Icp7SunbILYe4/giphy.gif", projection.media.single().uri)
    }

    @Test
    fun anUnresolvedImagePlaceholderIsNotALinkTarget() {
        // The renderer drops `![alt](target)`, so it must claim no range in
        // the rendered text (this is what painted the stray "!gif" link).
        val source = "![gif](giphy|abc123) and [real link](https://example.com/page)"
        val rendered = "real link"

        val targets = markdownLinkTargetsForRenderedText(source, rendered)
        assertEquals(1, targets.size)
        assertEquals("https://example.com/page", targets.single().url)
        assertFalse(targets.any { target -> target.url.contains("giphy") })
        assertTrue(targets.single().start >= 0)
    }
}
