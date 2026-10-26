package app.subshell.prototype.auth

import android.content.Intent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import app.subshell.prototype.data.auth.OAuthClientMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (auth07a): Add account and reconnect open the RedReader login window
 * with a fresh session, so Reddit does not offer the account it is already
 * signed in to: the launcher hands the window the fresh flag, and the window
 * clears Reddit's cookies and its cache before it loads the authorization
 * page. A capability authorization keeps the session the reader is signed in
 * with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3FreshSignInWindowTest {
    private object UnusedAuthTabLauncher : ActivityResultLauncher<Intent>() {
        override fun launch(input: Intent, options: ActivityOptionsCompat?) {
            error("the RedReader window does not use the Auth Tab")
        }
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = error("unused")
    }

    private fun launchedLoginWindow(freshAccountSession: Boolean): Intent {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        AuthBrowserLauncher.launch(
            context = activity,
            launcher = UnusedAuthTabLauncher,
            authorizationUrl = AUTHORIZATION_URL,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            freshAccountSession = freshAccountSession,
        )
        return shadowOf(activity).nextStartedActivity
    }

    private fun openLoginWindow(freshAccountSession: Boolean): WebView {
        CookieManager.getInstance().setCookie(REDDIT, "reddit_session=signed-in-account")
        val intent = RedReaderCompatibilityLoginActivity.createIntent(
            context = Robolectric.buildActivity(ComponentActivity::class.java).setup().get(),
            authorizationUrl = AUTHORIZATION_URL,
            freshAccountSession = freshAccountSession,
        )
        val window = Robolectric.buildActivity(RedReaderCompatibilityLoginActivity::class.java, intent).setup().get()
        // ISSUE242: the page sits in a frame that keeps it clear of the system bars.
        val frame = window.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        return frame.getChildAt(0) as WebView
    }

    @Test
    fun theLauncherHandsTheLoginWindowTheFreshSessionFlag() {
        val fresh = launchedLoginWindow(freshAccountSession = true)
        assertEquals(RedReaderCompatibilityLoginActivity::class.java.name, fresh.component?.className)
        assertTrue(fresh.getBooleanExtra(FRESH_EXTRA, false))
        assertEquals(AUTHORIZATION_URL, fresh.getStringExtra(URL_EXTRA))

        val reusable = launchedLoginWindow(freshAccountSession = false)
        assertFalse(reusable.getBooleanExtra(FRESH_EXTRA, true))
    }

    @Test
    fun aFreshSignInClearsRedditsCookiesAndTheCacheBeforeTheAuthorizationPage() {
        val webView = openLoginWindow(freshAccountSession = true)

        assertNull(CookieManager.getInstance().getCookie(REDDIT))
        assertTrue(shadowOf(webView).wasClearCacheCalled())
        assertTrue(shadowOf(webView).didClearCacheIncludeDiskFiles())
        assertEquals(AUTHORIZATION_URL, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun aReusableSignInKeepsTheSessionTheReaderIsSignedInWith() {
        val webView = openLoginWindow(freshAccountSession = false)

        assertEquals("reddit_session=signed-in-account", CookieManager.getInstance().getCookie(REDDIT))
        assertFalse(shadowOf(webView).wasClearCacheCalled())
        assertEquals(AUTHORIZATION_URL, shadowOf(webView).lastLoadedUrl)
    }

    private companion object {
        const val REDDIT = "https://www.reddit.com/"
        const val AUTHORIZATION_URL = "https://www.reddit.com/api/v1/authorize?client_id=harness&state=fresh"
        const val FRESH_EXTRA = "app.subshell.prototype.auth.REDREADER_FRESH_ACCOUNT_SESSION"
        const val URL_EXTRA = "app.subshell.prototype.auth.REDREADER_AUTHORIZATION_URL"
    }
}
