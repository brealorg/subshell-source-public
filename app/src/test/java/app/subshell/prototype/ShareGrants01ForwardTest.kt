package app.subshell.prototype

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SHAREGRANTS01_FORWARDED_SHARE_CARRIES_ITS_URIS_V1: a share forwarded to
 * MainActivity names its files in ClipData, with the read grant, instead of
 * relying on Android's implicit EXTRA_STREAM copy (gone in Android 18).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareGrants01ForwardTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val first = Uri.parse("content://sender.files/shared/one.jpg")
    private val second = Uri.parse("content://sender.files/shared/two.jpg")

    private fun ClipData.uris() = (0 until itemCount).map { getItemAt(it).uri }

    @Test
    fun oneSharedImageTravelsInClipDataWithTheReadGrant() {
        val forward = externalLaunchForward(
            context,
            Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, first),
        )
        assertEquals(listOf(first), forward.clipData!!.uris())
        assertTrue(forward.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun everyImageOfAMultipleShareTravelsInClipData() {
        val forward = externalLaunchForward(
            context,
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second)),
        )
        assertEquals(listOf(first, second), forward.clipData!!.uris())
    }

    @Test
    fun theSendersOwnClipDataIsKept() {
        val senders = ClipData.newRawUri("sender", second)
        val forward = externalLaunchForward(
            context,
            Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, first)
                .apply { clipData = senders },
        )
        assertEquals(listOf(second), forward.clipData!!.uris())
    }

    @Test
    fun aLinkOrTextShareGetsNoClipData() {
        val text = externalLaunchForward(
            context,
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://reddit.com/r/android"),
        )
        assertNull(text.clipData)
        val link = externalLaunchForward(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://reddit.com/r/android")))
        assertNull(link.clipData)
    }
}
