package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.CommentActionPreference
import app.subshell.prototype.ui.CommentTimeFormat
import app.subshell.prototype.ui.InteractionSide
import org.junit.Assert.assertEquals
import org.junit.Test

class Settings03FCommentsPreviewPolicyTest {
    @Test
    fun followActionPlacementTracksInteractionSide() {
        assertEquals(
            CommentActionPlacement.LEFT,
            commentsPreviewResolvedPlacement(
                preference = CommentActionPreference.FOLLOW,
                interactionSide = InteractionSide.LEFT,
            ),
        )
        assertEquals(
            CommentActionPlacement.RIGHT,
            commentsPreviewResolvedPlacement(
                preference = CommentActionPreference.FOLLOW,
                interactionSide = InteractionSide.RIGHT,
            ),
        )
    }

    @Test
    fun explicitBalancedPlacementStaysBalanced() {
        assertEquals(
            CommentActionPlacement.BALANCED,
            commentsPreviewResolvedPlacement(
                preference = CommentActionPreference.BALANCED,
                interactionSide = InteractionSide.RIGHT,
            ),
        )
    }

    @Test
    fun timePreviewUsesTheExistingFormatExamples() {
        // LOWUI4 (OCB-258): the preview shows the format's own example.
        assertEquals("2h", CommentTimeFormat.RELATIVE.example)
        assertEquals("20:20", CommentTimeFormat.LOCAL_24_HOUR.example)
        assertEquals("8:20 PM", CommentTimeFormat.LOCAL_12_HOUR.example)
    }
}
