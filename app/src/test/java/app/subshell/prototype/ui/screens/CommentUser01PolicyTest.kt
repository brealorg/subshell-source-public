package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentUser01PolicyTest {
    @Test
    fun validCommentAuthorTargetsExistingUserProfileRoute() {
        assertEquals("Some_User", commentAuthorProfileTarget("  Some_User  "))
    }

    @Test
    fun deletedAndBlankAuthorsDoNotCreateProfileTargets() {
        assertNull(commentAuthorProfileTarget(""))
        assertNull(commentAuthorProfileTarget("   "))
        assertNull(commentAuthorProfileTarget("[deleted]"))
        assertNull(commentAuthorProfileTarget("[DELETED]"))
    }
}
