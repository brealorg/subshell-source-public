package app.subshell.prototype.ui.components

import android.util.DisplayMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE85_LOCAL_MEDIA_DECODED_TO_SCREEN_SIZE_V1 (#85): a local photo was
 * decoded at full resolution (50 MP ≈ 200 MB), over the 100 MB a canvas
 * draws, and crashed; it is now decoded down to the screen size.
 */
class Issue85LocalMediaSampleTest {
    @Test
    fun aFiftyMegapixelPhotoIsDecodedWellUnderTheCanvasLimit() {
        val sample = localMediaSampleSize(width = 8160, height = 6120, maxDimension = 2400)
        assertEquals(4, sample)
        val bytes = (8160L / sample) * (6120L / sample) * 4L
        assertTrue(bytes < 100L * 1024 * 1024)
    }

    @Test
    fun aTwoHundredMegapixelPhotoAtTheLargestScreenStillFits() {
        val sample = localMediaSampleSize(width = 16320, height = 12240, maxDimension = 4096)
        assertTrue(16320 / sample <= 4096)
        assertTrue((16320L / sample) * (12240L / sample) * 4L < 100L * 1024 * 1024)
    }

    @Test
    fun smallImagesAndUnknownSizesAreNotReduced() {
        assertEquals(1, localMediaSampleSize(width = 1080, height = 720, maxDimension = 2400))
        assertEquals(1, localMediaSampleSize(width = 0, height = 0, maxDimension = 2400))
    }

    @Test
    fun theTargetIsTheScreensLongestSideCappedAt4096() {
        val phone = DisplayMetrics().apply { widthPixels = 1080; heightPixels = 2400 }
        assertEquals(2400, localMediaMaxDimension(phone))
        val huge = DisplayMetrics().apply { widthPixels = 7680; heightPixels = 4320 }
        assertEquals(4096, localMediaMaxDimension(huge))
    }
}
