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
 * STEP3 (auth06b): the browser's RedReader callback reaches Subshell only
 * through its exported alias, which hands it to the callback activity; a
 * `redreader://` link to any other host is not offered to Subshell at all.
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
    fun theRedReaderCallbackIsTakenOnlyByItsAliasForTheCallbackActivity() {
        val callback = handlers("redreader://rr_oauth_redir?state=s&code=c")

        assertEquals(listOf(ALIAS), callback.map { it.name })
        assertEquals(AuthCallbackActivity::class.java.name, callback.single().targetActivity)
    }

    @Test
    fun aRedReaderLinkToAnotherHostIsNotOfferedToSubshell() {
        assertEquals(emptyList<String>(), handlers("redreader://somewhere-else?state=s&code=c").map { it.name })
        assertEquals(emptyList<String>(), handlers("redreader://rr_oauth_redir.evil.example/").map { it.name })
    }

    private companion object {
        const val ALIAS = "app.subshell.prototype.auth.AuthRedReaderCompatibilityCallback"
    }
}
