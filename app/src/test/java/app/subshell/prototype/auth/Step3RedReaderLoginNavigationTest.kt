package app.subshell.prototype.auth

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth06b): the RedReader login window takes only the exact callback
 * (`redreader://rr_oauth_redir`, scheme and host) as the sign-in's answer;
 * a look-alike host or another scheme is never processed. Reddit's front page
 * ends (or resumes) the login, HTTPS pages of Reddit and its sign-in
 * providers load (SECAUDIT11), and anything else is blocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3RedReaderLoginNavigationTest {
    private fun decide(url: String) = loginNavigationFor(Uri.parse(url))

    @Test
    fun onlyTheExactRedReaderCallbackIsTheSignInsAnswer() {
        assertEquals(LoginNavigation.CALLBACK, decide("redreader://rr_oauth_redir?state=s&code=c#_"))

        assertEquals(LoginNavigation.BLOCK, decide("redreader://evil?state=s&code=c"))
        assertEquals(LoginNavigation.BLOCK, decide("redreader://rr_oauth_redir.evil.example/?code=c"))
        // SECAUDIT11: an https page outside Reddit and its sign-in providers is blocked too.
        assertEquals(LoginNavigation.BLOCK, decide("https://rr_oauth_redir/?state=s&code=c"))
    }

    @Test
    fun theFrontPageLeavesTheLoginHttpsLoadsAndEverythingElseIsBlocked() {
        assertEquals(LoginNavigation.LEAVE_FRONT_PAGE, decide("https://www.reddit.com/"))
        assertEquals(LoginNavigation.LOAD, decide("https://www.reddit.com/api/v1/authorize?client_id=x"))
        assertEquals(LoginNavigation.LOAD, decide("https://accounts.google.com/o/oauth2/auth"))

        assertEquals(LoginNavigation.BLOCK, decide("http://www.reddit.com/login"))
        assertEquals(LoginNavigation.BLOCK, decide("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(LoginNavigation.BLOCK, decide("javascript:alert(1)"))
    }
}
