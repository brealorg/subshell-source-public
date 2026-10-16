package app.subshell.prototype.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewerGesturePolicyTest {
    @Test
    fun galleryPagerOwnsSingleFingerPanAtFitScale() {
        assertFalse(fullscreenZoomCanPan(1f))
        assertFalse(fullscreenZoomCanPan(1.01f))
    }

    @Test
    fun zoomedStillOwnsPanBeyondFitScale() {
        assertTrue(fullscreenZoomCanPan(1.011f))
        assertTrue(fullscreenZoomCanPan(2.5f))
        assertTrue(fullscreenZoomCanPan(5f))
    }
}
