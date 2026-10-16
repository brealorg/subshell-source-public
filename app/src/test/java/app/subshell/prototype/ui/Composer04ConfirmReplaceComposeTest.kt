package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.screens.composerContentLoss
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER04 (ui review round 3 P3): adding a link, photos, a video or a
 * pasted image asks before it removes what the post already has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer04ConfirmReplaceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun image(n: Int) =
        ComposerMediaAttachment("content://media/image/$n", "image/jpeg", ComposerMediaKind.IMAGE)

    private val video =
        ComposerMediaAttachment("content://media/video/1", "video/mp4", ComposerMediaKind.VIDEO)

    private val gallery = PostComposerDraft(
        community = "subshell",
        title = "Gallery",
        type = ComposerPostType.MEDIA,
        media = (1..3).map(::image),
    )

    @Test
    fun addingALinkToAGalleryAsksAndKeepsTheImagesUntilConfirmed() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(gallery)
        composeRule.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        composeRule.waitForIdle()

        addLink()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Remove 3 images?").fetchSemanticsNodes().isNotEmpty())
        assertEquals(3, state.postComposerDraft.media.size)

        composeRule.onNodeWithText("Keep").performClick()
        composeRule.waitForIdle()
        assertEquals(3, state.postComposerDraft.media.size)
        assertEquals(ComposerPostType.MEDIA, state.postComposerDraft.type)

        addLink()
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.waitForIdle()
        assertTrue(state.postComposerDraft.media.isEmpty())
        assertEquals(ComposerPostType.LINK, state.postComposerDraft.type)
    }

    private fun addLink() {
        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("add-content")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add link").performClick()
    }

    @Test
    fun theLossRuleNamesWhatWouldBeRemoved() {
        val linked = PostComposerDraft(type = ComposerPostType.LINK, linkUrl = "https://example.com")
        val withPhoto = linked.copy(type = ComposerPostType.MEDIA, linkUrl = "", media = listOf(image(1)))
        assertEquals("Remove the link?", composerContentLoss(linked, withPhoto)?.title)

        val withVideo = PostComposerDraft(type = ComposerPostType.MEDIA, media = listOf(video))
        assertEquals(
            "Remove the video?",
            composerContentLoss(withVideo, withVideo.copy(media = listOf(image(1))))?.title,
        )
        assertEquals(
            "Remove 3 images?",
            composerContentLoss(gallery, gallery.copy(media = listOf(video)))?.title,
        )
        assertEquals(
            "Remove the image?",
            composerContentLoss(
                gallery.copy(media = listOf(image(1))),
                gallery.copy(type = ComposerPostType.LINK, media = emptyList()),
            )?.title,
        )
    }

    @Test
    fun addingToWhatIsThereOrStartingFromNothingDoesNotAsk() {
        assertNull(composerContentLoss(gallery, gallery.copy(media = gallery.media + image(4))))
        val empty = PostComposerDraft(title = "Nothing yet")
        assertNull(
            composerContentLoss(empty, empty.copy(type = ComposerPostType.LINK)),
        )
        assertNull(
            composerContentLoss(
                empty,
                empty.copy(type = ComposerPostType.MEDIA, media = listOf(video)),
            ),
        )
        val blankLink = PostComposerDraft(type = ComposerPostType.LINK, linkUrl = " ")
        assertNull(
            composerContentLoss(
                blankLink,
                blankLink.copy(type = ComposerPostType.MEDIA, linkUrl = "", media = listOf(image(1))),
            ),
        )
    }

    // STEP3 (a11y01a1): the gallery's remove and reorder buttons are 48 dp
    // targets, and "Move image later" moves the first image one place on.
    @Test
    fun theGalleryButtonsAre48DpTargetsAndReorderMovesTheImage() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(gallery)
        composeRule.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasContentDescription("Move image later"))
        composeRule.waitForIdle()

        for (name in listOf("Remove image 1", "Move image earlier", "Move image later")) {
            val button = composeRule.onAllNodes(hasContentDescription(name)).onFirst()
            button.assertWidthIsAtLeast(48.dp)
            button.assertHeightIsAtLeast(48.dp)
        }

        composeRule.onAllNodes(hasContentDescription("Move image later") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(image(2), image(1), image(3)), state.postComposerDraft.media)
    }
}
