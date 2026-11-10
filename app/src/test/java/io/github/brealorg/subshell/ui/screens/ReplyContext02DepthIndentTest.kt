package io.github.brealorg.subshell.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// REPLYCONTEXT02_REPLIES_INDENT_WITH_DEPTH_V1
class ReplyContext02DepthIndentTest {
    @Test
    fun contentStepsRightBySixDpPerLevelUpToTheCap() {
        assertEquals(0, commentDepthIndentDp(0))
        assertEquals(6, commentDepthIndentDp(1))
        assertEquals(18, commentDepthIndentDp(3))
        assertEquals(36, commentDepthIndentDp(6))
        assertEquals(36, commentDepthIndentDp(7))
        assertEquals(36, commentDepthIndentDp(40))
    }

    @Test
    fun aNegativeDepthHasNoIndent() {
        assertEquals(0, commentDepthIndentDp(-2))
    }

    @Test
    fun theReplyLineIsOnlyForRepliesPastTheIndentCap() {
        assertFalse(commentDepthShowsReplyContext(2))
        assertFalse(commentDepthShowsReplyContext(6))
        assertTrue(commentDepthShowsReplyContext(7))
    }
}
