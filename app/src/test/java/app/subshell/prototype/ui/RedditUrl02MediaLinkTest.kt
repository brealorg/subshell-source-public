package app.subshell.prototype.ui

import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITURL02: links to Reddit's media hosts open in the media viewer.
 * Every example URL here is made up.
 */
class RedditUrl02MediaLinkTest {
    @Test
    fun iReddItStillImagesAndGifsAreImageTargets() {
        assertEquals(
            RedditTarget.Image("https://i.redd.it/a1b2c3d4e5.jpg", animated = false),
            resolveRedditTarget("https://i.redd.it/a1b2c3d4e5.jpg"),
        )
        assertEquals(
            RedditTarget.Image("https://i.redd.it/a1b2c3d4e5.PNG", animated = false),
            resolveRedditTarget("http://i.redd.it/a1b2c3d4e5.PNG"),
        )
        assertEquals(
            RedditTarget.Image("https://i.redd.it/a1b2c3d4e5.gif", animated = true),
            resolveRedditTarget("https://i.redd.it/a1b2c3d4e5.gif"),
        )
        assertNull(resolveRedditTarget("https://i.redd.it/a1b2c3d4e5.mp4"))
        assertNull(resolveRedditTarget("https://i.redd.it/"))
    }

    @Test
    fun previewImagesKeepTheirSignedQueryAndPreviewGifsUseTheRealGif() {
        val preview = "https://preview.redd.it/a1b2c3d4e5.png?width=640&format=png&auto=webp&s=0f1e2d"
        assertEquals(RedditTarget.Image(preview, animated = false), resolveRedditTarget(preview))
        assertEquals(
            RedditTarget.Image("https://i.redd.it/a1b2c3d4e5.gif", animated = true),
            resolveRedditTarget("https://preview.redd.it/a1b2c3d4e5.gif?format=mp4&s=abc"),
        )
        val external = "https://external-preview.redd.it/AbC-dEf.jpg?auto=webp&s=123"
        assertEquals(RedditTarget.Image(external, animated = false), resolveRedditTarget(external))
    }

    @Test
    fun videoLinksByMediaIdPlayInTheInlineVideoPlayer() {
        val expected = RedditTarget.InlineVideo(postId = null, mediaId = "k3j9x2m1q8zd1")
        assertEquals(expected, resolveRedditTarget("https://v.redd.it/k3j9x2m1q8zd1"))
        assertEquals(expected, resolveRedditTarget("https://v.redd.it/k3j9x2m1q8zd1/"))
        assertEquals(expected, resolveRedditTarget("https://www.reddit.com/video/k3j9x2m1q8zd1"))
        assertEquals(expected, resolveRedditTarget("https://www.reddit.com/r/kotlin/video/k3j9x2m1q8zd1"))
        assertNull(resolveRedditTarget("https://v.redd.it/k3j9x2m1q8zd1/DASH_720.mp4"))
    }

    @Test
    fun theImageProxyPageOpensTheImageItWraps() {
        assertEquals(
            RedditTarget.Image("https://i.redd.it/a1b2c3.jpg", animated = false),
            resolveRedditTarget("https://www.reddit.com/media?url=https%3A%2F%2Fi.redd.it%2Fa1b2c3.jpg"),
        )
        assertNull(resolveRedditTarget("https://www.reddit.com/media?url=https%3A%2F%2Fexample.com%2Fa.jpg"))
        assertNull(resolveRedditTarget("https://www.reddit.com/media"))
    }

    @Test
    fun mediaLinksStayInTheApp() {
        assertTrue(isInAppRedditLink("https://i.redd.it/a1b2c3d4e5.jpg"))
        assertTrue(isInAppRedditLink("https://v.redd.it/k3j9x2m1q8zd1"))
        assertFalse(isInAppRedditLink("https://example.com/a1b2c3d4e5.jpg"))
    }

    @Test
    fun aBareVideoLinkPlaysWithoutAPostAndLinksToVReddIt() {
        val post = inlineRedditVideoPost(RedditTarget.InlineVideo(postId = null, mediaId = "k3j9x2m1q8zd1"))

        assertEquals("https://v.redd.it/k3j9x2m1q8zd1/DASHPlaylist.mpd", post.remoteVideoUri)
        assertEquals("https://v.redd.it/k3j9x2m1q8zd1", post.externalUrl)
        assertEquals("Embedded video", post.title)
    }

    @Test
    fun openingAnImageLinkShowsItInTheMediaViewerUnderItsOwnId() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val gif = RedditTarget.Image("https://i.redd.it/a1b2c3d4e5.gif", animated = true)

        assertTrue(state.openRedditTarget(gif))

        val shown = state.selectedMediaPost!!
        assertEquals(PostKind.GIF, shown.kind)
        assertEquals("https://i.redd.it/a1b2c3d4e5.gif", shown.remoteImageUri)
        assertEquals("Linked GIF", shown.title)
        assertEquals("GIF", shown.mediaLabel)
        assertTrue(shown.id.startsWith("linked-image-"))
        assertEquals(SubshellRoute.Media(postId = shown.id, index = 0), state.currentNavigationRoute)
    }

    @Test
    fun searchOffersToOpenAPastedImageLink() {
        val plan = planSearchQuery(
            rawQuery = "https://i.redd.it/a1b2c3d4e5.jpg",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )

        assertEquals("Open image", plan.directAction?.title)
        assertEquals("i.redd.it", plan.directAction?.subtitle)
    }
}
