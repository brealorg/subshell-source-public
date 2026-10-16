package app.subshell.prototype.ui.screens

import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Settings03CAppearancePreviewPolicyTest {
    @Test
    fun themeLabelsAreProductCopy() {
        assertEquals("System", appearanceThemeLabel(ThemeMode.SYSTEM))
        assertEquals("Light", appearanceThemeLabel(ThemeMode.LIGHT))
        assertEquals("Dark", appearanceThemeLabel(ThemeMode.DARK))
    }

    @Test
    fun motionTrackUsesTheAvailableWidth() {
        assertEquals(0.dp, appearanceMotionTargetX(false, 52.dp, 12.dp))
        assertEquals(40.dp, appearanceMotionTargetX(true, 52.dp, 12.dp))
    }

    @Test
    fun appearanceOwnsThemeAndMotionSectionLabels() {
        assertEquals("Theme & style", settingsSectionTitle(SettingsDestination.APPEARANCE, "Appearance"))
        assertEquals("Motion", settingsSectionTitle(SettingsDestination.APPEARANCE, "Accessibility"))
        assertTrue(
            settingsSectionVisible(
                destination = SettingsDestination.APPEARANCE,
                query = "",
                searchResult = settingsSearchResult(""),
                section = "Appearance",
                debug = false,
            ),
        )
    }
    @Test
    fun votePreviewUsesOneSelectedDirectionAtATime() {
        assertEquals(
            AppearanceVoteTone.UPVOTE,
            appearanceVoteTone(
                direction = 1,
                selectedDirection = 1,
                palette = app.subshell.prototype.ui.VoteColorPalette.RED_BLUE,
            ),
        )
        assertEquals(
            AppearanceVoteTone.NEUTRAL,
            appearanceVoteTone(
                direction = -1,
                selectedDirection = 1,
                palette = app.subshell.prototype.ui.VoteColorPalette.RED_BLUE,
            ),
        )
        assertEquals(
            AppearanceVoteTone.DOWNVOTE,
            appearanceVoteTone(
                direction = -1,
                selectedDirection = -1,
                palette = app.subshell.prototype.ui.VoteColorPalette.RED_BLUE,
            ),
        )
        assertEquals(
            AppearanceVoteTone.NEUTRAL,
            appearanceVoteTone(
                direction = 1,
                selectedDirection = -1,
                palette = app.subshell.prototype.ui.VoteColorPalette.RED_BLUE,
            ),
        )
    }

    @Test
    fun themeAccentPreviewUsesAccentOnlyForSelectedVote() {
        assertEquals(
            AppearanceVoteTone.THEME_ACCENT,
            appearanceVoteTone(
                direction = 1,
                selectedDirection = 1,
                palette = app.subshell.prototype.ui.VoteColorPalette.NEUTRAL,
            ),
        )
        assertEquals(
            AppearanceVoteTone.NEUTRAL,
            appearanceVoteTone(
                direction = -1,
                selectedDirection = 1,
                palette = app.subshell.prototype.ui.VoteColorPalette.NEUTRAL,
            ),
        )
    }

    @Test
    fun previewReplayAlternatesSelectedVoteDirection() {
        assertEquals(1, appearancePreviewSelectedVoteDirection(motionAtEnd = false))
        assertEquals(-1, appearancePreviewSelectedVoteDirection(motionAtEnd = true))
    }

}
