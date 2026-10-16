package app.subshell.prototype.data.error

import app.subshell.prototype.backup.BackupFailureOperation
import app.subshell.prototype.backup.toBackupFailureDetails
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.RemoteMediaKind
import app.subshell.prototype.ui.error.backupErrorPresentation
import app.subshell.prototype.ui.error.remoteMediaErrorPresentation
import app.subshell.prototype.media.VideoPlaybackPhase
import app.subshell.prototype.media.VideoPlaybackUiState
import app.subshell.prototype.ui.error.videoPlaybackErrorPresentation
import app.subshell.prototype.ui.error.videoPlaybackFailureDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Error01a3MediaBackupErrorPolicyTest {
    @Test
    fun remoteImageTransportFailureIsReadableAndRetryable() {
        val details = FailureDetails(
            retryable = true,
            transportKind = NetworkTransportFailureKind.DNS,
        )
        val presentation = remoteMediaErrorPresentation(RemoteMediaKind.IMAGE, details)

        assertEquals(ErrorPresentationKind.INLINE, presentation.kind)
        assertEquals("Couldn't load image", presentation.title)
        assertTrue(presentation.message.contains("couldn't reach"))
        assertEquals("Network: DNS", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun videoPlaybackErrorKeepsReadableMediaCode() {
        val details = videoPlaybackFailureDetails(2002)
        // LOWUI4_VIDEO_FAILURE_KEEPS_ITS_DETAILS_V1 (OCB-169): the live
        // presentation carries the same details for the diagnostics.
        val presentation = videoPlaybackErrorPresentation(
            VideoPlaybackUiState(mediaId = "t3_video", phase = VideoPlaybackPhase.ERROR, errorCode = 2002),
        )

        assertTrue(details.hasMeaningfulExpandedDetails())
        assertEquals("Network connection timeout", details.mediaCode?.value)
        assertEquals("Network connection timeout", presentation.failureDetails?.mediaCode?.value)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun backupThrowableMessageNeverBecomesUserFacingDetail() {
        val secret = "access_token=secret-value raw internal path /data/user/0/private"
        val details = RuntimeException(secret).toBackupFailureDetails()
        val presentation = backupErrorPresentation(BackupFailureOperation.IMPORT, details)
        val visible = listOf(
            presentation.title,
            presentation.message,
            presentation.technicalDetail.orEmpty(),
        ).joinToString(" ")

        assertEquals(LocalFailureKind.UNKNOWN, details.localKind)
        assertFalse(visible.contains("secret-value"))
        assertFalse(visible.contains("/data/user/0/private"))
        assertTrue(presentation.message.contains("failed"))
    }

    @Test
    fun backupSecurityExceptionBecomesPermissionFailure() {
        val details = SecurityException("provider detail").toBackupFailureDetails()
        val presentation = backupErrorPresentation(BackupFailureOperation.CONNECT_FOLDER, details)

        assertEquals(LocalFailureKind.PERMISSION, details.localKind)
        assertTrue(presentation.message.contains("didn't grant"))
        assertEquals("Local: Permission", presentation.technicalDetail)
    }
}
