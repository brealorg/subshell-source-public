package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.screens.bodyFocusRevealRange
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER17_BODY_FOCUS_REVEAL_V1: focusing the post body moves "Body" to
 * the top of the list once the keyboard is up, not only the cursor line into
 * view; a cursor too far down keeps the area around it in view instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer17BodyFocusRevealComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bodyField = hasSetTextAction() and hasText("Body text", substring = true)

    private fun settle() {
        repeat(5) {
            composeRule.mainClock.advanceTimeBy(200)
            composeRule.waitForIdle()
        }
    }

    @Test
    fun aKeyboardOpeningAfterFocusMovesTheBodyToTheTopOfTheList() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(community = "subshell", title = "A post", body = "Body text"),
        )
        // The keyboard is emulated by a spacer that takes height from below.
        var keyboard by mutableStateOf(0.dp)
        composeRule.setContent {
            SubshellTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { CreatePostScreen(state = state) }
                    Spacer(Modifier.height(keyboard))
                }
            }
        }
        settle()

        val density = composeRule.density.density
        val shown = 120f * density
        fun listBounds() = composeRule.onAllNodes(hasScrollToKeyAction()).onFirst()
            .fetchSemanticsNode().boundsInRoot
        fun fieldBounds() = composeRule.onNode(bodyField).fetchSemanticsNode().boundsInRoot

        // Focus first (the field is in view), then the keyboard opens and
        // leaves only the field's first line above it, as on the device.
        // The clock is held so the reveal (which waits for the keyboard) has
        // not run yet when the keyboard is in place.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNode(bodyField).performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        val leaveVisible = 40f * density
        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        keyboard = ((rootBottom - (fieldBounds().top + leaveVisible)) / density).dp
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        System.err.println("COMPOSER17 keyboard list=${listBounds()} field=${fieldBounds()}")
        assertTrue(
            "the keyboard must leave the body mostly hidden",
            fieldBounds().top + shown > listBounds().bottom,
        )

        composeRule.mainClock.autoAdvance = true
        settle()

        System.err.println("COMPOSER17 after list=${listBounds()} field=${fieldBounds()}")
        assertTrue(fieldBounds().top >= listBounds().top)
        assertTrue(fieldBounds().top + shown <= listBounds().bottom)
        // Only the "Body" heading sits between the list top and the field.
        assertTrue(fieldBounds().top <= listBounds().top + 80f * density)
    }

    @Test
    fun theRevealRangeRunsFromTheTopToLinesBelowTheCursor() {
        assertEquals(0f..130f, bodyFocusRevealRange(400f, 70f, 20f, 600f))
    }

    @Test
    fun aShortFieldIsShownWhole() {
        assertEquals(0f..100f, bodyFocusRevealRange(100f, 70f, 20f, 600f))
    }

    @Test
    fun whenTooTallTheCursorAreaWinsOverTheTop() {
        assertEquals(1060f..1300f, bodyFocusRevealRange(2000f, 1240f, 20f, 240f))
    }
}
