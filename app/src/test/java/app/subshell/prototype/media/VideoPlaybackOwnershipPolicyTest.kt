package app.subshell.prototype.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackOwnershipPolicyTest {
    @Test
    fun activityStopPausesOnlyForRealBackgrounding() {
        assertTrue(shouldPauseVideoPlaybackForActivityStop(isChangingConfigurations = false))
        assertFalse(shouldPauseVideoPlaybackForActivityStop(isChangingConfigurations = true))
    }

    @Test
    fun presentationDetachPreservesIntentForHandoffOrConfigurationChange() {
        assertFalse(
            shouldPreserveVideoPlaybackIntentOnDetach(
                presentationHandoff = false,
                isChangingConfigurations = false,
            ),
        )
        assertTrue(
            shouldPreserveVideoPlaybackIntentOnDetach(
                presentationHandoff = true,
                isChangingConfigurations = false,
            ),
        )
        assertTrue(
            shouldPreserveVideoPlaybackIntentOnDetach(
                presentationHandoff = false,
                isChangingConfigurations = true,
            ),
        )
    }
}
