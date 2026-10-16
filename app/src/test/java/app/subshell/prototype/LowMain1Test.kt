package app.subshell.prototype

import android.app.Application
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.applock.AppLockController
import app.subshell.prototype.applock.AppLockSettings
import app.subshell.prototype.share.ShareImportViewModel
import app.subshell.prototype.share.ShareIntoSubshellComponent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * MAIN1 (low MainActivity findings): a failed start leaves no exempt trip
 * behind (OCB-392), and a share that cannot be read is delivered as nothing
 * usable instead of crashing (OCB-393).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowMain1Test {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aFailedStartLeavesNoExemptTrip() {
        val lock = AppLockController(AppLockSettings(enabled = true, delayMinutes = 0)).apply { unlock() }
        lock.onExternalTripStarted(nowMs = 1_000L)
        lock.onExternalTripFailed()

        lock.onStopped(nowMs = 2_000L, changingConfigurations = false)

        assertTrue("Immediately locks on the next real trip", lock.locked)
    }

    /** A provider that throws where the share reader asks for the type. */
    class ThrowingProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String = throw IllegalStateException("broken provider")
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    @Test
    fun aShareThatCannotBeReadIsDeliveredAsNothingUsable() {
        Robolectric.buildContentProvider(ThrowingProvider::class.java).create("broken.app.files")
        val share = Intent(Intent.ACTION_SEND)
            .setComponent(ComponentName(application.packageName, ShareIntoSubshellComponent.ALIAS_CLASS))
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://broken.app.files/one.png"))
        val viewModel = ShareImportViewModel()

        viewModel.read(application, share, requestId = 7L)
        val deadline = System.currentTimeMillis() + 5_000L
        while (viewModel.launch == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10L)
        }

        val launch = viewModel.launch
        assertNotNull(launch)
        assertNull(launch!!.content)
        assertFalse(launch.requestId != 7L)
    }
}
