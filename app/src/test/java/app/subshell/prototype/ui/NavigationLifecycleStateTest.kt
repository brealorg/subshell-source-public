package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.media.FeedScrollDirection
import app.subshell.prototype.media.FeedViewportMotion
import app.subshell.prototype.media.FeedViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationLifecycleStateTest {
    @Test
    fun restoredCommunityRoutePreservesOriginSortAndGlobalMute() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialDestination = MainDestination.SUBS,
            initialSelectedSubreddit = SampleData.VIDEO_LAB_COMMUNITY_NAME,
            initialCommunitySort = CommunitySort.NEW,
            initialFeedVideoMuted = false,
        )

        assertEquals(MainDestination.SUBS, state.destination)
        assertEquals(SampleData.VIDEO_LAB_COMMUNITY_NAME, state.selectedSubreddit)
        assertEquals(CommunitySort.NEW, state.communitySort)
        assertFalse(state.feedVideoMuted)

        state.closeSubreddit()
        assertNull(state.selectedSubreddit)
        assertEquals(MainDestination.SUBS, state.destination)
    }

    @Test
    fun restoredCommunityMediaReacquiresAfterViewportBecomesIdle() {
        val mediaId = "video-lab-f"
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialDestination = MainDestination.SUBS,
            initialSelectedSubreddit = SampleData.VIDEO_LAB_COMMUNITY_NAME,
            initialBackgroundResumeVideoId = mediaId,
        )
        val surface = Any()

        assertEquals(mediaId, state.navigationLifecycleMediaId)

        state.updateVideoFeedContext(listOf(mediaId), terminalVideoId = mediaId)
        state.updateVideoVisibility(mediaId, surface, visibleFraction = 0.80f)
        state.updateFeedViewportState(
            FeedViewportState(
                motion = FeedViewportMotion.IDLE,
                direction = FeedScrollDirection.NONE,
            ),
        )

        assertEquals(mediaId, state.activeAnimatedMediaId)
    }

    @Test
    fun unknownRestoredCommunityIsRejected() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialSelectedSubreddit = "not-a-real-community",
        )

        assertNull(state.selectedSubreddit)
    }

    @Test
    fun restoredFullscreenRoutePreservesUnderlyingPostAndViewerIndex() {
        val post = SampleData.posts.first { it.id == "video-lab-a" }
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialDestination = MainDestination.HOME,
            initialSelectedPostId = post.id,
            initialSelectedMediaPostId = post.id,
            initialSelectedMediaIndex = 2,
            initialPostVisitId = 7,
            initialBackgroundResumeVideoId = post.id,
            initialBackgroundResumeWasManual = true,
        )

        assertEquals(post.id, state.selectedPost?.id)
        assertEquals(post.id, state.selectedMediaPost?.id)
        assertEquals(2, state.selectedMediaIndex)
        assertEquals(7, state.postVisitId)
        assertEquals(post.id, state.navigationLifecycleMediaId)

        state.closeMedia()

        assertNull(state.selectedMediaPost)
        assertNotNull(state.selectedPost)
        assertEquals(post.id, state.selectedPost?.id)
    }

    @Test
    fun unknownRestoredPostAndFullscreenIdsAreRejected() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialSelectedPostId = "not-a-real-post",
            initialSelectedMediaPostId = "not-a-real-media-post",
        )

        assertNull(state.selectedPost)
        assertNull(state.selectedMediaPost)
    }

    @Test
    fun saveableRouteMirrorObservesFullscreenOnlyTransition() {
        val post = SampleData.posts.first { it.id == "video-lab-a" }
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )

        state.open(post)
        val postSnapshot = navigationRouteRestoreSnapshot(state)
        assertEquals(post.id, postSnapshot.selectedPostId)
        assertNull(postSnapshot.selectedMediaPostId)

        state.openMedia(post)
        val fullscreenSnapshot = navigationRouteRestoreSnapshot(state)
        assertEquals(post.id, fullscreenSnapshot.selectedPostId)
        assertEquals(post.id, fullscreenSnapshot.selectedMediaPostId)
        assertEquals(state.selectedMediaIndex, fullscreenSnapshot.selectedMediaIndex)
        assertEquals(state.postVisitId, fullscreenSnapshot.postVisitId)
    }

}
