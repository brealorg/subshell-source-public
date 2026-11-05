package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DIRECTLINK01_A_LINKS_COMMENT_IS_FOUND_BY_ITS_FULLNAME_V1: a loaded comment's
 * id is Reddit's fullname (t1_xxx); a link carries the bare id. The landing
 * compared them as they were, so every direct comment link ended in "Couldn't
 * find this comment" although Reddit had returned the comment (measured on the
 * Pixel 6: returned_ids t1_pgqq4pl, target pgqq4pl).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DirectLink01CommentFullnameTest {
    private val post = SampleData.posts.first().copy(id = "t3_1abcde")

    @Test
    fun aLinkToACommentFocusesItsFullname() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(initialInteractionSideConfigured = true)
        val target = resolveRedditTarget("https://www.reddit.com/comments/1abcde/_/pgqq4pl/")
        assertTrue(target is RedditTarget.Comment)

        assertTrue(state.openRedditTarget(target!!, resolvedPost = post))

        assertNotNull(state.activeFeedPostRoute)
        assertEquals("t1_pgqq4pl", state.activeFeedPostRoute?.focusedCommentId)
    }

    @Test
    fun aFullnameInTheLinkIsNotPrefixedTwice() {
        assertEquals("t1_abc123", redditCommentFullname("abc123"))
        assertEquals("t1_abc123", redditCommentFullname("t1_abc123"))
    }
}
