package io.github.brealorg.subshell.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

// REPLYCONTEXT01_REPLY_SCROLLS_THE_TARGET_ABOVE_THE_COMPOSER_V1
class ReplyContext01ReplyTargetScrollTest {
    private fun delta(top: Int, size: Int, bottomLimit: Int = 1000) =
        replyTargetScrollDeltaPx(
            itemTopPx = top,
            itemSizePx = size,
            viewportStartPx = 0,
            availableBottomPx = bottomLimit,
            marginPx = 20,
        )

    @Test
    fun aTargetAlreadyAboveTheComposerStays() {
        assertEquals(0, delta(top = 100, size = 300))
    }

    @Test
    fun aTargetBehindTheComposerScrollsForwardJustEnough() {
        // bottom 1100, free space ends at 980
        assertEquals(120, delta(top = 800, size = 300))
    }

    @Test
    fun aTargetAboveTheViewportScrollsBack() {
        assertEquals(-150, delta(top = -150, size = 300))
    }

    @Test
    fun aTallTargetKeepsItsTopVisible() {
        // bottom 1700 would need 720, but only 500 keeps the top at the edge
        assertEquals(500, delta(top = 500, size = 1200))
    }
}
