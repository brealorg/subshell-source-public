package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.FullscreenCaptionMode
import app.subshell.prototype.ui.VideoAutoplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Settings03DMediaPreviewPolicyTest {
    @Test
    fun autoplayPreviewUsesProductMeaningInsteadOfTechnicalCopy() {
        assertEquals("Auto", mediaPreviewAutoplayLabel(VideoAutoplay.ALWAYS))
        assertEquals("Wi-Fi", mediaPreviewAutoplayLabel(VideoAutoplay.WIFI_ONLY))
        assertEquals("Manual", mediaPreviewAutoplayLabel(VideoAutoplay.NEVER))
    }

    @Test
    fun fullscreenCaptionsWithControlsHideWhenViewerControlsHide() {
        assertTrue(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.WITH_CONTROLS,
                controlsVisible = true,
            ),
        )
        assertFalse(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.WITH_CONTROLS,
                controlsVisible = false,
            ),
        )
    }

    @Test
    fun alwaysVisibleFullscreenCaptionsIgnoreViewerControls() {
        assertTrue(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.ALWAYS_VISIBLE,
                controlsVisible = true,
            ),
        )
        assertTrue(
            mediaPreviewFullscreenCaptionVisible(
                mode = FullscreenCaptionMode.ALWAYS_VISIBLE,
                controlsVisible = false,
            ),
        )
    }
}
