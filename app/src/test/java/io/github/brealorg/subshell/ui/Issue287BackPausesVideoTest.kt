package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #287 (reader, 0.91): a video opened from search results or a profile kept its sound after Back.
 * Back from the fullscreen viewer starts a handoff so a surface underneath can carry the video on;
 * a search or profile list has no such surface, nothing ended the handoff, and the player kept
 * playing with no owner. Only a feed that owns playback, or the post screen showing the same post,
 * takes a video over; anywhere else it is paused.
 */
class Issue287BackPausesVideoTest {
    private val post = SampleData.posts.first { it.kind == PostKind.VIDEO }

    private fun newState() = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    )

    @Test
    fun backFromTheViewerToASearchListPausesTheVideo() {
        val state = newState()
        state.openSearch()
        state.openMedia(post)
        assertEquals(post.id, state.activeAnimatedMediaId)

        state.closeMedia()

        assertFalse("nothing in a search list takes the video over", state.isVideoPresentationHandoff(post.id))
        assertNull("the video is no longer the playing one", state.activeAnimatedMediaId)
    }

    @Test
    fun backFromThePostScreenToASearchListPausesTheVideo() {
        val state = newState()
        state.openSearch()
        state.open(post)
        state.playVideo(post.id)

        state.closePost()

        assertFalse(state.isVideoPresentationHandoff(post.id))
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun backFromTheViewerToThePostScreenStillHandsTheVideoOver() {
        val state = newState()
        state.open(post)
        state.playVideo(post.id)
        state.openMedia(post)

        state.closeMedia()

        assertTrue("the post screen shows the same video and takes it over", state.isVideoPresentationHandoff(post.id))
        assertEquals(post.id, state.activeAnimatedMediaId)
    }
}
