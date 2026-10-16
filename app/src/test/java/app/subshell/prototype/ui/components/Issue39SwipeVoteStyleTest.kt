package app.subshell.prototype.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import app.subshell.prototype.ui.CommentSwipeAction
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.theme.SubshellOrangeSoft
import app.subshell.prototype.ui.theme.SubshellVoteDown
import app.subshell.prototype.ui.theme.SubshellVoteUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #39: vote swipes take colour and icon from the vote bar's helpers,
 * so they follow "Vote colours" and "Vote controls" and do not depend on the
 * theme's colour scheme.
 */
class Issue39SwipeVoteStyleTest {
    @Test
    fun redAndBlueGivesRedUpvoteAndBlueDownvoteSwipes() {
        val up = voteSwipe(1, VoteColorPalette.RED_BLUE, VoteControlMode.COMFORTABLE)
        val down = voteSwipe(-1, VoteColorPalette.RED_BLUE, VoteControlMode.COMFORTABLE)

        assertEquals(SubshellVoteUp, up.containerColor)
        assertEquals(SubshellVoteDown, down.containerColor)
    }

    @Test
    fun themeAccentGivesBothDirectionsTheVoteBarAccent() {
        val up = voteSwipe(1, VoteColorPalette.NEUTRAL, VoteControlMode.COMFORTABLE)
        val down = voteSwipe(-1, VoteColorPalette.NEUTRAL, VoteControlMode.COMFORTABLE)

        assertEquals(SubshellOrangeSoft, up.containerColor)
        assertEquals(SubshellOrangeSoft, down.containerColor)
    }

    @Test
    fun swipeColoursAreTheVoteBarColoursForEveryPalette() {
        VoteColorPalette.entries.forEach { palette ->
            listOf(1, -1).forEach { direction ->
                // #218: the tint takes the theme; voteSwipe() builds the dark-theme visual.
                assertEquals(
                    voteDirectionTint(direction, palette, darkTheme = true),
                    voteSwipe(direction, palette, VoteControlMode.COMPACT).containerColor,
                )
            }
        }
    }

    @Test
    fun comfortableUsesThumbsAndCompactUsesArrows() {
        assertEquals(
            Icons.Rounded.ThumbUp,
            voteSwipe(1, VoteColorPalette.NEUTRAL, VoteControlMode.COMFORTABLE).icon,
        )
        assertEquals(
            Icons.Rounded.ThumbDown,
            voteSwipe(-1, VoteColorPalette.NEUTRAL, VoteControlMode.COMFORTABLE).icon,
        )
        assertEquals(
            Icons.Rounded.ArrowUpward,
            voteSwipe(1, VoteColorPalette.NEUTRAL, VoteControlMode.COMPACT).icon,
        )
        assertEquals(
            Icons.Rounded.ArrowDownward,
            voteSwipe(-1, VoteColorPalette.NEUTRAL, VoteControlMode.COMPACT).icon,
        )
    }

    @Test
    fun commentVoteSwipesFollowTheSameSettings() {
        val down = commentSwipeActionVisual(
            action = CommentSwipeAction.DOWNVOTE,
            vote = 0,
            enabled = true,
            voteColorPalette = VoteColorPalette.RED_BLUE,
            voteControlMode = VoteControlMode.COMPACT,
        )!!

        assertEquals("Downvote", down.label)
        assertEquals(SubshellVoteDown, down.containerColor)
        assertEquals(Icons.Rounded.ArrowDownward, down.icon)
        assertEquals(VoteSwipeContentColor, down.contentColor)
    }

    @Test
    fun theLabelNeedsRoomForPaddingOnBothSidesIconGapAndTheWholeLabel() {
        // 18 + 22 + 8 + 70 + 18 = 136
        assertTrue(swipeActionLabelFits(136, 18, 22, 8, 70))
        assertFalse(swipeActionLabelFits(135, 18, 22, 8, 70))
        assertFalse(swipeActionLabelFits(0, 18, 22, 8, 70))
    }

    private fun voteSwipe(
        direction: Int,
        palette: VoteColorPalette,
        mode: VoteControlMode,
    ) = voteSwipeActionVisual(
        direction = direction,
        label = "label",
        palette = palette,
        mode = mode,
        enabled = true,
    )
}
