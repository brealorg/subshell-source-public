package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.COMMENT_EDIT_TEXT_MAX_CHARS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UIPOST03 (ui/ review round 1, finding 6): Save on a comment edit is off
 * over the 10 000-character limit. It used to stay on, and the request
 * silently failed to build, so tapping Save did nothing.
 */
class UiPost03CommentEditLimitTest {
    @Test
    fun saveIsOffOverTheLimit() {
        val atLimit = "a".repeat(COMMENT_EDIT_TEXT_MAX_CHARS)
        assertTrue(commentEditSaveEnabled(atLimit, originalBody = "old", idle = true))
        assertFalse(commentEditSaveEnabled(atLimit + "b", originalBody = "old", idle = true))
    }

    @Test
    fun theOtherRulesAreUnchanged() {
        assertFalse(commentEditSaveEnabled("   ", originalBody = "old", idle = true))
        assertFalse(commentEditSaveEnabled(" old ", originalBody = "old", idle = true))
        assertFalse(commentEditSaveEnabled("new", originalBody = "old", idle = false))
        assertTrue(commentEditSaveEnabled("new", originalBody = "old", idle = true))
    }
}
