package app.subshell.prototype

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (deeplink01): Reddit links on every Reddit host (and redd.it) are
 * offered to Subshell's own link activity and nothing else is. (MainActivity
 * taking the forwarded link in, cold and warm, is a boundary guard: starting
 * the whole activity in the unit suite ran the test JVM out of memory.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3RedditLinkDeliveryTest {
    @get:Rule
    val flake = RetryOnRobolectricRendererFlake()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun workManager() = ensureWorkManagerInitialized(application)

    private fun handlers(url: String): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(application.packageName)
        return application.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.name }
    }

    @Test
    fun everyRedditHostIsOfferedToTheLinkActivityAndNothingElseIs() {
        val link = ExternalLaunchActivity::class.java.name
        listOf("reddit.com", "www.reddit.com", "old.reddit.com", "new.reddit.com", "np.reddit.com", "m.reddit.com")
            .forEach { host ->
                assertEquals(host, listOf(link), handlers("https://$host/r/kotlin/"))
                assertEquals(host, listOf(link), handlers("https://$host/comments/abc/"))
                assertEquals(host, listOf(link), handlers("https://$host/user/someone/"))
            }
        assertEquals(listOf(link), handlers("https://redd.it/abc123"))

        assertEquals(emptyList<String>(), handlers("https://example.com/r/kotlin/"))
        assertEquals(emptyList<String>(), handlers("https://www.reddit.com/settings/"))
    }
}
