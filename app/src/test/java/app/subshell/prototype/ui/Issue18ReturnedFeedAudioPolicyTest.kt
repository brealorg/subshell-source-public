package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue18ReturnedFeedAudioPolicyTest {
    @Test
    fun orphanedPlayerPausesWhileReturnedFeedWaitsForQualification() {
        assertTrue(
            returnedFeedPlayerNeedsPause(
                playerMediaId = "video-a",
                selectedMediaId = null,
            ),
        )
    }

    @Test
    fun differentFeedCandidateCannotLeaveOldPlayerAdvancing() {
        assertTrue(
            returnedFeedPlayerNeedsPause(
                playerMediaId = "video-a",
                selectedMediaId = "video-b",
            ),
        )
    }

    @Test
    fun selectedSameMediaKeepsNormalQualifiedPlaybackPath() {
        assertFalse(
            returnedFeedPlayerNeedsPause(
                playerMediaId = "video-a",
                selectedMediaId = "video-a",
            ),
        )
    }

    @Test
    fun noRetainedPlayerNeedsNoPause() {
        assertFalse(
            returnedFeedPlayerNeedsPause(
                playerMediaId = null,
                selectedMediaId = null,
            ),
        )
    }
}
