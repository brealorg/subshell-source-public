package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.SubshellRepository
import io.github.brealorg.subshell.data.createFakeSubshellRepository
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.projectCommentContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two held findings taken on owner request:
 * LOW_COMMENT_IMAGES_LOAD_ONLY_FROM_KNOWN_HOSTS_V1 (OCB-569),
 * LOW_NSFW_SWITCH_READS_POSTS_BY_EXACT_KEY_V1 (OCB-528).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeldFindingsTest {
    @Test
    fun commentImagesLoadOnlyFromKnownHosts() {
        val known = projectCommentContent("look https://i.imgur.com/abc.png")
        val unknown = projectCommentContent("look https://tracker.example.net/pixel.png")

        assertEquals(1, known.media.size)
        assertTrue(unknown.media.isEmpty())
        assertTrue(unknown.displayText.contains("https://tracker.example.net/pixel.png"))
        assertEquals(1, projectCommentContent("https://media2.giphy.com/media/x/giphy.gif").media.size)
    }

    @Test
    fun turningNsfwOffDoesNotScanTheCachedPosts() {
        val nsfw: Post = SampleData.posts.first().copy(id = "t3_n528", nsfw = true)
        val fake = createFakeSubshellRepository()
        val repository = object : SubshellRepository by fake {
            override fun post(postId: String): RepositoryResult<Post> =
                error("post(id) falls back to a full scan of cached posts")

            override fun postForAccount(postId: String, accountStableId: String?): RepositoryResult<Post> =
                if (postId == nsfw.id) RepositoryResult.Content(nsfw) else fake.postForAccount(postId, accountStableId)
        }
        val state = SubshellState(repository = repository)
        state.updateShowNsfwPosts(true)
        state.revealMedia(nsfw.id)

        state.updateShowNsfwPosts(false)

        assertFalse(nsfw.id in state.revealedMediaIds)
    }
}
