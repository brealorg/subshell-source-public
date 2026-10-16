package app.subshell.prototype.backup

import androidx.fragment.app.FragmentActivity
import app.subshell.prototype.MainActivity
import app.subshell.prototype.data.error.FailureDetailItem
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.toSafeDetailItems
import app.subshell.prototype.ui.error.videoPlaybackFailureDetails
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (error01a3): a backup failure is kept as its kind — a file problem,
 * bad input or unusable storage each read as such and survive being stored
 * and read back; a video error code becomes a readable "Media code" detail;
 * MainActivity is a FragmentActivity (the folder picker and biometric prompt
 * need one).
 */
class Step3BackupAndMediaFailureKindsTest {
    @Test
    fun backupFailuresAreKeptByKindAndSurviveBeingStored() {
        val kinds = listOf(
            IOException("disk") to LocalFailureKind.IO,
            IllegalArgumentException("bad file") to LocalFailureKind.VALIDATION,
            IllegalStateException("closed") to LocalFailureKind.STORAGE,
        )
        kinds.forEach { (error, kind) ->
            val details = error.toBackupFailureDetails()
            assertEquals(error.toString(), kind, details.localKind)
            assertEquals(kind, storedBackupFailureDetails(details.localKind?.name)?.localKind)
        }
        assertNull(storedBackupFailureDetails("not a kind"))
        assertNull(storedBackupFailureDetails(null))
    }

    @Test
    fun aVideoErrorCodeBecomesAReadableMediaCodeDetail() {
        assertTrue(
            videoPlaybackFailureDetails(4003).toSafeDetailItems().contains(FailureDetailItem("Media code", "Decoding failed")),
        )
        assertTrue(
            videoPlaybackFailureDetails(2002).toSafeDetailItems()
                .contains(FailureDetailItem("Media code", "Network connection timeout")),
        )
    }

    @Test
    fun mainActivityIsAFragmentActivity() {
        assertTrue(FragmentActivity::class.java.isAssignableFrom(MainActivity::class.java))
    }
}
