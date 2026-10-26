package app.subshell.prototype.auth

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECAUDIT11 (security audit #11, owner): the login window has no address
 * bar, so it loads only Reddit and the sign-in providers Reddit offers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit11LoginWindowHostsTest {
    private fun decide(url: String) = loginNavigationFor(Uri.parse(url))

    @Test
    fun redditAndItsSignInProvidersLoad() {
        listOf(
            "https://www.reddit.com/login/",
            "https://accounts.reddit.com/",
            "https://reddit.com/api/v1/authorize",
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://accounts.google.no/accounts/SetSID",
            "https://accounts.google.co.uk/accounts/SetSID",
            "https://www.google.com.au/",
            "https://accounts.youtube.com/accounts/SetSID",
            "https://appleid.apple.com/auth/authorize",
            "https://idmsa.apple.com/appleauth/",
        ).forEach { url -> assertEquals(url, LoginNavigation.LOAD, decide(url)) }
    }

    @Test
    fun everyOtherPageIsBlocked() {
        listOf(
            "https://reddit.com.evil.example/login",
            "https://evilreddit.com/login",
            "https://google.evil.example/",
            "https://notgoogle.com/",
            "https://google.com.evil.example/",
            "https://www.youtube.com/watch?v=x",
            "https://apple.com.evil.example/",
            "https://example.com/",
            "http://accounts.google.com/",
        ).forEach { url -> assertEquals(url, LoginNavigation.BLOCK, decide(url)) }
    }
}
