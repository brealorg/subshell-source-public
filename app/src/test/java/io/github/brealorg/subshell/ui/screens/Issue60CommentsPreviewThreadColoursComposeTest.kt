package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.brealorg.subshell.ui.CommentActionPreference
import io.github.brealorg.subshell.ui.CommentCollapseMode
import io.github.brealorg.subshell.ui.CommentHierarchyStyle
import io.github.brealorg.subshell.ui.CommentThreadColours
import io.github.brealorg.subshell.ui.CommentTimeFormat
import io.github.brealorg.subshell.ui.InteractionSide
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ISSUE60_THE_COMMENTS_PREVIEW_SHOWS_THREAD_COLOURS_V1 (#60 finding 4): the live
 * preview in Settings › Comments draws the card and the coloured line the thread
 * has in a post, so the Thread colours choice can be seen where it is made.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue60CommentsPreviewThreadColoursComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun warmPixels(colours: CommentThreadColours): Int {
        composeRule.setContent {
            SubshellTheme {
                CommentsSettingsPreview(
                    highlightNewComments = false,
                    showCommentAvatars = false,
                    actionPreference = CommentActionPreference.RIGHT,
                    interactionSide = InteractionSide.RIGHT,
                    timeFormat = CommentTimeFormat.entries.first(),
                    collapseMode = CommentCollapseMode.entries.first(),
                    hierarchyStyle = CommentHierarchyStyle.GUIDE_LINES,
                    threadColours = colours,
                )
            }
        }
        val image = composeRule.onRoot().captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        // The first lane of By depth is a warm red: strong red, little green and blue.
        return pixels.count { argb ->
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            r > 200 && g < 140 && b < 130
        }
    }

    @Test
    fun byDepthDrawsTheWarmFirstLaneAndOffDoesNot() {
        assertTrue(warmPixels(CommentThreadColours.BY_DEPTH) > 100)
    }

    @Test
    fun offDrawsNoWarmLane() {
        assertFalse(warmPixels(CommentThreadColours.OFF) > 100)
    }
}
