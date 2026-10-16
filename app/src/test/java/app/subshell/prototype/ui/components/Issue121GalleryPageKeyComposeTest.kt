package app.subshell.prototype.ui.components

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE121_GALLERY_PAGE_KEY_UNIQUE_V1 (#121): a gallery that lists one
 * media_id twice gave the pager two equal keys, and composing the post
 * crashed ("Key ... was already used").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue121GalleryPageKeyComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun item(id: String) = GalleryItem(
        id = id,
        label = id,
        format = MediaFormat.IMAGE,
        remoteImageUri = "https://i.redd.it/$id.jpg",
    )

    @Test
    fun aGalleryWithARepeatedMediaIdComposes() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = Post(
            id = "t3_gallery",
            community = "subshell",
            author = "someone",
            age = "1h",
            title = "Gallery",
            score = 1,
            comments = 0,
            kind = PostKind.GALLERY,
            gallery = listOf(item("a"), item("a"), item("b")),
        )
        composeRule.setContent {
            SubshellTheme { GalleryMedia(post = post, state = state, onLongPress = {}) }
        }
        composeRule.waitForIdle()
        // Swiping composes the next page (the same media_id) beside the first.
        composeRule.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    @Test
    fun pageKeysAreUniquePerPosition() {
        val keys = listOf("a", "b", "a").mapIndexed { index, id -> galleryPageKey(id, index) }
        assertEquals(keys.size, keys.distinct().size)
    }
}
