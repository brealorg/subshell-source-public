package app.subshell.prototype.ui.components

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedImageAspectRatioTest {
    @Test
    fun sourceRatioIsPreservedInsideFeedBounds() {
        assertEquals(4f / 3f, boundedFeedImageAspectRatio(1600, 1200), 0.0001f)
        assertEquals(1f, boundedFeedImageAspectRatio(1200, 1200), 0.0001f)
    }

    @Test
    fun extremeSourcesAreBoundedForAStableFeed() {
        assertEquals(4f / 5f, boundedFeedImageAspectRatio(900, 2000), 0.0001f)
        assertEquals(16f / 9f, boundedFeedImageAspectRatio(2400, 900), 0.0001f)
        assertEquals(16f / 9f, boundedFeedImageAspectRatio(0, 0), 0.0001f)
    }

    @Test
    fun redditVideoUsesDisplayOrientedPreviewDimensions() {
        val post = Post(
            id = "video",
            community = "subshell",
            author = "tester",
            age = "now",
            title = "Rotated source video",
            score = 0,
            comments = 0,
            kind = PostKind.VIDEO,
            videoWidth = 1280,
            videoHeight = 720,
        )

        assertEquals(16f / 9f, mediaAspectRatio(post), 0.0001f)
    }
}
