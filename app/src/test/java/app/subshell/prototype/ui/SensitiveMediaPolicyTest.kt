package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveMediaPolicyTest {
    private val nsfwPost = SampleData.posts.single { it.id == "nsfw-cable-horror" }
    private val spoilerPost = SampleData.posts.single { it.id == "spoiler-rack" }
    private val ordinaryPost = SampleData.posts.first { !it.nsfw && !it.spoiler }

    @Test
    fun nsfwPostsAreLocallyHiddenUntilTheUserEnablesThem() {
        assertFalse(postVisibleWithNsfwPreference(nsfwPost, showNsfwPosts = false))
        assertTrue(postVisibleWithNsfwPreference(nsfwPost, showNsfwPosts = true))
        assertTrue(postVisibleWithNsfwPreference(ordinaryPost, showNsfwPosts = false))
    }

    @Test
    fun sensitiveAnimationAndVideoRequireAnExplicitReveal() {
        assertFalse(sensitiveMediaCanAutoplay(nsfwPost, emptySet()))
        assertFalse(sensitiveMediaCanAutoplay(spoilerPost, emptySet()))
        assertTrue(sensitiveMediaCanAutoplay(nsfwPost, setOf(nsfwPost.id)))
        assertTrue(sensitiveMediaCanAutoplay(ordinaryPost, emptySet()))
    }

    @Test
    fun previewModesKeepTheThreeDeliberateUserChoices() {
        assertTrue(
            SensitiveMediaPreviewMode.entries.map { it.label } ==
                listOf("Blurred", "Load on tap", "Hide preview"),
        )
    }

    @Test
    fun blurredSensitiveVideoUsesPosterOnlyUntilReveal() {
        val video = nsfwPost.copy(kind = PostKind.VIDEO)
        val image = nsfwPost.copy(kind = PostKind.IMAGE)

        assertTrue(
            sensitiveMediaUsesPosterOnly(
                post = video,
                mode = SensitiveMediaPreviewMode.BLUR_AND_PRELOAD,
                revealed = false,
            ),
        )
        assertFalse(
            sensitiveMediaUsesPosterOnly(
                post = video,
                mode = SensitiveMediaPreviewMode.BLUR_AND_PRELOAD,
                revealed = true,
            ),
        )
        assertFalse(
            sensitiveMediaUsesPosterOnly(
                post = video,
                mode = SensitiveMediaPreviewMode.LOAD_ON_TAP,
                revealed = false,
            ),
        )
        assertFalse(
            sensitiveMediaUsesPosterOnly(
                post = image,
                mode = SensitiveMediaPreviewMode.BLUR_AND_PRELOAD,
                revealed = false,
            ),
        )
    }

    @Test
    fun quickVisibilitySwitchPersistsAndRehidesPreviouslyRevealedNsfwMedia() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            initialShowNsfwPosts = true,
            persistPreference = { key, value -> persisted[key] = value },
        )
        state.revealMedia(nsfwPost)

        state.updateShowNsfwPosts(false)

        assertFalse(state.showNsfwPosts)
        assertFalse(nsfwPost.id in state.revealedMediaIds)
        assertEquals("false", persisted[SubshellSettings.SHOW_NSFW_POSTS])
    }
}
