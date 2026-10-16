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
 * STEP3 (auth01, auth03): in a debug build the sign-in callback
 * `subshell-auth-test://oauth/callback` reaches only the dedicated callback
 * activity, and only on that exact path. (The release manifest's production
 * App Link and its "not exported" callback stay with check-auth05e2:
 * Robolectric reads only the debug manifest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3AuthCallbackManifestTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun handlers(url: String): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(application.packageName)
        return application.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.targetActivity ?: it.activityInfo.name }
    }

    @Test
    fun theDebugCallbackReachesOnlyTheCallbackActivityOnItsExactPath() {
        assertEquals(
            listOf(AuthCallbackActivity::class.java.name),
            handlers("subshell-auth-test://oauth/callback?state=s&code=c"),
        )
        assertEquals(emptyList<String>(), handlers("subshell-auth-test://oauth/other?state=s&code=c"))
        assertEquals(emptyList<String>(), handlers("subshell-auth-test://elsewhere/callback?state=s&code=c"))
    }
}
