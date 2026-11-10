package io.github.brealorg.subshell.data.cache

import io.github.brealorg.subshell.data.network.reddit.redditPostIsRemoved
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #295 part 3: whether a post was removed is the post payload's field 37.
 * Payloads written before it (36 and 37 fields) decode as not removed.
 * ISSUE295_REMOVED_POST_IS_MARKED_V1.
 */
class Issue295RemovedPostCacheTest {
    private val post = Post(
        id = "t3_abc",
        community = "subshell",
        author = "someone",
        age = "1h",
        title = "A removed post",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        removed = true,
    )

    @Test
    fun aRemovedPostStaysRemovedThroughTheCache() {
        assertEquals(post, SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(post)))
        val kept = post.copy(removed = false)
        assertEquals(kept, SubshellCacheCodec.decodePost(SubshellCacheCodec.encodePost(kept)))
    }

    @Test
    fun payloadsWrittenBeforeTheFieldReadAsNotRemoved() {
        val fields = SubshellCacheCodec.decodeMutationFields(SubshellCacheCodec.encodePost(post), expectedSize = 38)
        listOf(37, 36).forEach { size ->
            val historical = SubshellCacheCodec.encodeMutationFields(*fields.take(size).toTypedArray())
            assertEquals(post.copy(removed = false), SubshellCacheCodec.decodePost(historical))
        }
    }

    @Test
    fun redditsOwnWordDecidesAndTheAuthorsDeletionIsNotARemoval() {
        assertTrue(redditPostIsRemoved(removed = true, removedByCategory = null))
        assertFalse(redditPostIsRemoved(removed = false, removedByCategory = "moderator"))
        assertTrue(redditPostIsRemoved(removed = null, removedByCategory = "moderator"))
        assertTrue(redditPostIsRemoved(removed = null, removedByCategory = "automod_filtered"))
        assertFalse(redditPostIsRemoved(removed = null, removedByCategory = "deleted"))
        assertFalse(redditPostIsRemoved(removed = null, removedByCategory = "author"))
        assertFalse(redditPostIsRemoved(removed = null, removedByCategory = null))
    }
}
