package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (homeaction01): in the fullscreen gallery a double tap zooms the
 * page, and while it is zoomed a swipe pans instead of changing page; a
 * second double tap zooms out and the swipe pages again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3GalleryZoomComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun aZoomedPageKeepsTheSwipeAndZoomingOutPagesAgain() {
        // Still images zoom; the sample galleries are animated GIFs, so their
        // pictures are reused as still-image pages.
        val source = SampleData.posts.first { it.gallery.size >= 2 }
        val gallery = source.copy(gallery = source.gallery.map { it.copy(format = MediaFormat.IMAGE) })
        val state = SubshellState()
        composeRule.setContent { MediaViewerScreen(post = gallery, state = state, onBack = {}) }
        composeRule.waitForIdle()
        assertEquals(0, state.selectedMediaIndex)

        composeRule.onRoot().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(0, state.selectedMediaIndex)

        composeRule.onRoot().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertTrue("paged to ${state.selectedMediaIndex}", state.selectedMediaIndex >= 1)
    }
}
