package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.model.MediaAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtoClean01FixtureIsolationPolicyTest {
    @Test
    fun videoLabRawResourcesUseNameBasedDebugLookup() {
        assertEquals(
            "subshell_video_lab_a",
            bundledVideoRawResourceName(MediaAsset.VIDEO_LAB_A),
        )
        assertEquals(
            "subshell_video_lab_z",
            bundledVideoRawResourceName(MediaAsset.VIDEO_LAB_Z),
        )
        assertNull(
            bundledVideoRawResourceName(MediaAsset.MEDIA03_DASH),
        )
        assertNull(
            bundledVideoRawResourceName(MediaAsset.HOMELAB),
        )
    }

    @Test
    fun adaptiveFixturesReuseDebugPosterNamesWithoutMainResourceIds() {
        assertEquals(
            "subshell_video_lab_a",
            bundledVideoPosterResourceName(MediaAsset.MEDIA03_DASH),
        )
        assertEquals(
            "subshell_video_lab_e",
            bundledVideoPosterResourceName(MediaAsset.MEDIA03_HLS),
        )
        assertEquals(
            "subshell_video_lab_z",
            bundledVideoPosterResourceName(MediaAsset.MEDIA03_FALLBACK),
        )
        assertNull(
            bundledVideoPosterResourceName(MediaAsset.ANDROID_DESK),
        )
    }
}
