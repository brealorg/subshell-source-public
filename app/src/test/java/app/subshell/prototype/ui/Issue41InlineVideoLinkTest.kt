package app.subshell.prototype.ui

import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #41: `reddit.com/link/<post>/video/<media>/player` links play the
 * video from v.redd.it in the built-in viewer.
 */
class Issue41InlineVideoLinkTest {
    private val expected = RedditTarget.InlineVideo(postId = "pbpmii5", mediaId = "8n56vczekerh1")

    @Test
    fun inlineVideoLinksResolveWithAndWithoutThePlayerSuffix() {
        listOf(
            "https://reddit.com/link/pbpmii5/video/8n56vczekerh1/player",
            "https://www.reddit.com/link/pbpmii5/video/8n56vczekerh1/player/",
            "https://old.reddit.com/link/pbpmii5/video/8n56vczekerh1",
            "https://www.reddit.com/link/pbpmii5/video/8N56VCZEKERH1/player?utm_source=share",
            "/link/pbpmii5/video/8n56vczekerh1/player",
        ).forEach { url ->
            assertEquals(url, expected, resolveRedditTarget(url))
        }
    }

    @Test
    fun otherLinkShapesAreNotInlineVideos() {
        listOf(
            "https://www.reddit.com/link/pbpmii5/image/8n56vczekerh1/player",
            "https://www.reddit.com/link/pbpmii5/video/8n56vczekerh1/embed",
            "https://www.reddit.com/link/pbpmii5/video",
            "https://www.reddit.com/link/pbpmii5/video/8n56-vcz/player",
            "https://example.com/link/pbpmii5/video/8n56vczekerh1/player",
        ).forEach { url ->
            assertNull(url, resolveRedditTarget(url))
        }
    }

    @Test
    fun anInlineVideoIsNotAPostTargetForRemoteResolution() {
        assertNull(expected.postIdOrNull())
    }

    @Test
    fun theVideoPlaysFromVReddItByMediaIdWithDashFirst() {
        val post = inlineRedditVideoPost(expected)

        assertEquals(PostKind.VIDEO, post.kind)
        assertEquals("https://v.redd.it/8n56vczekerh1/DASHPlaylist.mpd", post.remoteVideoUri)
        assertEquals("https://v.redd.it/8n56vczekerh1/HLSPlaylist.m3u8", post.remoteVideoHlsUri)
        assertEquals(
            "https://www.reddit.com/link/pbpmii5/video/8n56vczekerh1/player",
            post.externalUrl,
        )
    }

    @Test
    fun theViewerTitleIsTheSourcePostsWhenItIsLoaded() {
        val source = app.subshell.prototype.model.Post(
            id = "t3_pbpmii5",
            community = "movies",
            author = "someone",
            age = "7h",
            title = "Best scene in the film",
            score = 1,
            comments = 0,
            kind = PostKind.TEXT,
        )

        val withSource = inlineRedditVideoPost(expected, source)
        val withoutSource = inlineRedditVideoPost(expected)

        assertEquals("Best scene in the film", withSource.title)
        assertEquals("movies", withSource.community)
        assertEquals("Embedded video", withoutSource.title)
        assertEquals("Video", withoutSource.mediaLabel)
    }

    @Test
    fun theVideoPostNeverTakesTheIdOfTheTextPostItCameFrom() {
        val post = inlineRedditVideoPost(expected)

        assertEquals("inline-video-8n56vczekerh1", post.id)
        assertFalse(redditTargetMatchesPost("pbpmii5", post.id))
    }

    @Test
    fun openingTheTargetOpensTheVideoInTheMediaViewer() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        assertTrue(state.openRedditTarget(expected))

        assertEquals(inlineRedditVideoPost(expected), state.selectedMediaPost)
        assertEquals(
            SubshellRoute.Media(postId = "inline-video-8n56vczekerh1", index = 0),
            state.currentNavigationRoute,
        )
    }

    @Test
    fun searchOffersToPlayAPastedInlineVideoLink() {
        val plan = planSearchQuery(
            rawQuery = "https://www.reddit.com/link/pbpmii5/video/8n56vczekerh1/player",
            area = SearchArea.ALL_SUBSHELL,
            authenticated = true,
        )

        assertEquals(expected, plan.directAction?.target)
        assertEquals("Play Reddit video", plan.directAction?.title)
    }
}
