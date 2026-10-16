package app.subshell.prototype.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.ArrowCircleUp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.subshell.prototype.ui.components.voteStateIcon
import app.subshell.prototype.ui.components.voteDirectionTint
import app.subshell.prototype.ui.components.voteSwipeContentColor
import app.subshell.prototype.ui.theme.DarkColors
import app.subshell.prototype.ui.theme.LightColors
import app.subshell.prototype.ui.theme.SubshellOrangeSoft
import app.subshell.prototype.ui.theme.contrastRatio
import app.subshell.prototype.ui.theme.withMinContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #218: colours the M3 audit measured below WCAG contrast (vote state,
 * letter avatars, comment rails) reach it in both themes.
 */
class Issue218ContrastTest {
    private val lightBackgrounds = listOf(LightColors.surface, LightColors.surfaceContainerLow)
    private val darkBackgrounds = listOf(DarkColors.surface, DarkColors.surfaceContainerLow, Color.Black)

    @Test
    fun theRatioIsWcags() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(LightColors.surface, LightColors.surface), 0.001f)
    }

    @Test
    fun aCastVoteReadsInEveryPaletteAndTheme() {
        for (palette in VoteColorPalette.entries) {
            for (direction in listOf(1, -1)) {
                for (bg in lightBackgrounds) {
                    val tint = voteDirectionTint(direction, palette, darkTheme = false)
                    assertTrue("$palette $direction light", contrastRatio(tint, bg) >= 4.5f)
                    assertTrue(contrastRatio(voteSwipeContentColor(false), tint) >= 4.5f)
                }
                for (bg in darkBackgrounds) {
                    val tint = voteDirectionTint(direction, palette, darkTheme = true)
                    assertTrue("$palette $direction dark", contrastRatio(tint, bg) >= 4.5f)
                    assertTrue(contrastRatio(voteSwipeContentColor(true), tint) >= 4.5f)
                }
            }
        }
        // The dark tones are the #39 tints; the old light-theme tint failed.
        assertEquals(SubshellOrangeSoft, voteDirectionTint(1, VoteColorPalette.NEUTRAL, darkTheme = true))
        assertTrue(contrastRatio(SubshellOrangeSoft, LightColors.surface) < 3f)
    }

    @Test
    fun aCastVoteIsAlsoAShapeNotOnlyAColour() {
        assertEquals(Icons.Rounded.ThumbUp, voteStateIcon(1, VoteControlMode.COMFORTABLE, voted = true))
        assertEquals(Icons.Outlined.ThumbUpOffAlt, voteStateIcon(1, VoteControlMode.COMFORTABLE, voted = false))
        assertEquals(Icons.Rounded.ThumbDown, voteStateIcon(-1, VoteControlMode.COMFORTABLE, voted = true))
        assertEquals(Icons.Outlined.ThumbDownOffAlt, voteStateIcon(-1, VoteControlMode.COMFORTABLE, voted = false))
        assertEquals(Icons.Rounded.ArrowCircleUp, voteStateIcon(1, VoteControlMode.COMPACT, voted = true))
        assertEquals(Icons.Rounded.ArrowUpward, voteStateIcon(1, VoteControlMode.COMPACT, voted = false))
    }

    @Test
    fun dataColoursAreMovedUntilTheyReadAndOtherwiseLeftAlone() {
        // The pastel letter-avatar accents (1.3–2.2:1 measured in light theme).
        val pastels = listOf(Color(0xFFFFC765), Color(0xFF78C7FF), Color(0xFF7ED6A5), Color(0xFFC5A7FF), Color(0xFFFF8A3D))
        for (accent in pastels) {
            for (surface in listOf(LightColors.surface, DarkColors.surface)) {
                val background = accent.copy(alpha = 0.18f).compositeOver(surface)
                val letter = accent.withMinContrast(background, 4.5f)
                assertTrue("$accent on $surface", contrastRatio(letter, background) >= 4.5f)
            }
        }
        // A rail drawn at 0.62 alpha reaches 3:1; a colour that already reads is unchanged.
        val rail = Color(0xFFFFC765).copy(alpha = 0.62f).withMinContrast(LightColors.surface, 3f)
        assertTrue(contrastRatio(rail, LightColors.surface) >= 3f)
        assertEquals(Color.Black, Color.Black.withMinContrast(LightColors.surface, 4.5f))
        assertNotEquals(Color(0xFFFFC765), rail)
    }
}
