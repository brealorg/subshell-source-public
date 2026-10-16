package app.subshell.prototype.ui.screens

import app.subshell.prototype.media.VideoMediaCacheLimit
import app.subshell.prototype.ui.error.ErrorPresentationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCacheLimitNoticeTest {
    @Test
    fun theRestartNoticeNamesTheChosenSizeInTheTrayStyle() {
        val notice = videoCacheLimitRestartNotice(VideoMediaCacheLimit.GB_2)
        assertEquals(ErrorPresentationKind.DEGRADED, notice.kind)
        assertEquals("Restart to apply", notice.title)
        assertEquals("The 2 GB video cache is used the next time subshell starts", notice.message)
    }
}
