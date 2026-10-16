package app.subshell.prototype.ui.components

import app.subshell.prototype.model.MediaAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtoClean01FixtureIsolationPolicyTest {
    @Test
    fun videoLabRawResourcesUseNameBasedDebugLookup() {
        assertEquals(
            "subshell_video_lab_a",
            prototypeVideoRawResourceName(MediaAsset.VIDEO_LAB_A),
        )
        assertEquals(
            "subshell_video_lab_z",
            prototypeVideoRawResourceName(MediaAsset.VIDEO_LAB_Z),
        )
        assertNull(
            prototypeVideoRawResourceName(MediaAsset.MEDIA03_DASH),
        )
        assertNull(
            prototypeVideoRawResourceName(MediaAsset.HOMELAB),
        )
    }

    @Test
    fun adaptiveFixturesReuseDebugPosterNamesWithoutMainResourceIds() {
        assertEquals(
            "subshell_video_lab_a",
            prototypeVideoPosterResourceName(MediaAsset.MEDIA03_DASH),
        )
        assertEquals(
            "subshell_video_lab_e",
            prototypeVideoPosterResourceName(MediaAsset.MEDIA03_HLS),
        )
        assertEquals(
            "subshell_video_lab_z",
            prototypeVideoPosterResourceName(MediaAsset.MEDIA03_FALLBACK),
        )
        assertNull(
            prototypeVideoPosterResourceName(MediaAsset.ANDROID_DESK),
        )
    }
}
