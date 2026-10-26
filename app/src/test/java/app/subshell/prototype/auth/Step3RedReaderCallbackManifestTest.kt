package app.subshell.prototype.auth

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth06b), successor SECAUDIT07 (security audit #7): the RedReader
 * callback was taken by an exported alias, so any app or web link could
 * deliver one and end a sign-in in progress. The sign-in window takes the
 * redirect itself, so no `redreader://` link is offered to Subshell at all.
 * (That the callback activity itself is not exported in the main manifest
 * is a boundary guard: the debug manifest exports it for the test scheme.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3RedReaderCallbackManifestTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun handlers(url: String) = application.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(application.packageName),
        PackageManager.MATCH_DEFAULT_ONLY,
    ).map { it.activityInfo }

    @Test
    fun theRedReaderCallbackIsNotOfferedToSubshell() {
        assertEquals(emptyList<String>(), handlers("redreader://rr_oauth_redir?state=s&code=c").map { it.name })
    }

    @Test
    fun aRedReaderLinkToAnotherHostIsNotOfferedToSubshell() {
        assertEquals(emptyList<String>(), handlers("redreader://somewhere-else?state=s&code=c").map { it.name })
        assertEquals(emptyList<String>(), handlers("redreader://rr_oauth_redir.evil.example/").map { it.name })
    }
}
