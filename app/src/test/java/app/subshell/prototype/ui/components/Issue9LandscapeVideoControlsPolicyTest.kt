package app.subshell.prototype.ui.components

import java.io.File

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue9LandscapeVideoControlsPolicyTest {
    @Test
    fun controlsRequireFullscreenLandscapeMasterAndAtLeastOneGesture() {
        assertTrue(fullscreenVideoGesturesActive(true, true, true, true, false))
        assertFalse(fullscreenVideoGesturesActive(false, true, true, true, true))
        assertFalse(fullscreenVideoGesturesActive(true, false, true, true, true))
        assertFalse(fullscreenVideoGesturesActive(true, true, false, true, true))
        assertFalse(fullscreenVideoGesturesActive(true, true, true, false, false))
    }

    @Test
    fun leftAndRightHalvesRespectIndependentToggles() {
        assertEquals(FullscreenVideoGesture.BRIGHTNESS, fullscreenVideoGestureForPosition(249f, 1000f, true, true))
        assertEquals(FullscreenVideoGesture.VOLUME, fullscreenVideoGestureForPosition(750f, 1000f, true, true))
        assertNull(fullscreenVideoGestureForPosition(100f, 1000f, false, true))
        assertNull(fullscreenVideoGestureForPosition(900f, 1000f, true, false))
    }

    @Test
    fun verticalDragRaisesOnUpLowersOnDownAndClamps() {
        assertEquals(0.75f, fullscreenGestureLevelAfterDrag(0.5f, -250f, 1000f), 0.0001f)
        assertEquals(0.25f, fullscreenGestureLevelAfterDrag(0.5f, 250f, 1000f), 0.0001f)
        assertEquals(1f, fullscreenGestureLevelAfterDrag(0.9f, -500f, 1000f), 0.0001f)
        assertEquals(0f, fullscreenGestureLevelAfterDrag(0.1f, 500f, 1000f), 0.0001f)
    }
    @Test
    fun settingsUsesSingleIntegratedMediaPreviewForLandscapeGestures() {
        val settingsScreen = File(
            "src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt",
        ).readText()
        val mediaPreview = File(
            "src/main/java/app/subshell/prototype/ui/screens/Settings03MediaPreview.kt",
        ).readText()

        assertFalse(settingsScreen.contains("media-landscape-gesture-preview"))
        assertFalse(settingsScreen.contains("Landscape gesture preview"))
        assertFalse(settingsScreen.contains("LandscapeVideoGestureSettingsPreview("))
        assertTrue(
            settingsScreen.contains(
                "landscapeVideoGesturesEnabled = state.landscapeVideoGesturesEnabled",
            ),
        )

        assertFalse(mediaPreview.contains("LandscapeVideoGestureInlinePreview("))
        assertTrue(mediaPreview.contains("FjordPreviewBackdrop("))
        assertTrue(mediaPreview.contains("GestureZoneHint("))
        assertFalse(mediaPreview.contains("ViewerControlsSample("))
        assertTrue(mediaPreview.contains("sensitiveMediaPreviewMode = sensitiveMediaPreviewMode"))
        assertTrue(mediaPreview.contains("mediaCaptionMode = mediaCaptionMode"))
        assertTrue(mediaPreview.contains("mediaLabelsVisible = mediaLabelsVisible"))
        assertFalse(mediaPreview.contains("PreviewContextLabel(\"Feed\")"))
        assertFalse(mediaPreview.contains("PreviewContextLabel(\"Viewer\")"))
        assertTrue(mediaPreview.contains("UnifiedMediaPreviewSample("))
        assertTrue(mediaPreview.contains("landscapeVideoGesturesEnabled = landscapeVideoGesturesEnabled"))
        assertTrue(mediaPreview.contains("landscapeBrightnessGestureEnabled = landscapeBrightnessGestureEnabled"))
        assertTrue(mediaPreview.contains("landscapeVolumeGestureEnabled = landscapeVolumeGestureEnabled"))
    }

}
