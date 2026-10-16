package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** LOWUI6b: data kept across process death and clean stored values. */
class LowUi6bTest {
    // LOWUI6_PROFILE_TAB_SURVIVES_PROCESS_DEATH_V1 (OCB-097)
    @Test
    fun anOwnProfileTabPastCommentsSurvivesTheProcessSnapshot() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.selectUserProfileTab(state.activeAccountUsername, 6)

        val restored = SubshellState(
            initialProcessSnapshotToken = encodeSubshellProcessSnapshot(state.processStateSnapshot()),
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )

        assertEquals(6, restored.userProfileTab(state.activeAccountUsername))
    }

    // LOWUI6_FILTER_VALUES_HAVE_NO_CONTROL_CHARACTERS_V1 (OCB-193)
    @Test
    fun aPastedCarriageReturnOrTabBecomesASpace() {
        assertEquals("free  giveaway", contentFilterInputWithoutControlCharacters("free\r\ngiveaway"))
        assertEquals("a b", contentFilterInputWithoutControlCharacters("a\tb"))
    }
}
