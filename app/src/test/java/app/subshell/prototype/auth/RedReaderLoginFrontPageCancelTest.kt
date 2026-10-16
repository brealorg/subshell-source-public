package app.subshell.prototype.auth

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTHCALLBACK07: in the RedReader compatibility login window, Reddit's own
 * close (X) navigates to https://www.reddit.com/ (read from the device's
 * WebView on 2026-09-26). Reaching the front page cancels the login; every
 * page the login and authorization flow uses must still load.
 * AUTHCALLBACK08: signed in (the `reddit_session` cookie, present only after
 * a login, read from the device on 2026-09-27), the front page reopens the
 * authorization once instead, for SSO/2FA logins that lose their way back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RedReaderLoginFrontPageCancelTest {
    @Test
    fun theFrontPageOnEveryRedditHostEndsTheLogin() {
        listOf(
            "https://www.reddit.com/",
            "https://www.reddit.com",
            "https://reddit.com/",
            "https://old.reddit.com/",
            "https://new.reddit.com/?utm_source=login",
            "https://sh.reddit.com/",
            "https://WWW.Reddit.com/",
        ).forEach { url ->
            assertTrue(url, isRedditFrontPage(Uri.parse(url)))
        }
    }

    @Test
    fun theLoginAndAuthorizationPagesStayOpen() {
        listOf(
            "https://www.reddit.com/api/v1/authorize.compact?client_id=x&response_type=code",
            "https://www.reddit.com/login/?dest=https%3A%2F%2Fwww.reddit.com%2Fapi%2Fv1%2Fauthorize",
            "https://www.reddit.com/account/login/",
            "https://www.reddit.com/register/",
            "https://www.reddit.com/password",
            "https://accounts.reddit.com/",
            "https://accounts.google.com/",
            "https://www.redditstatic.com/",
            "http://www.reddit.com/",
            "redreader://rr_oauth_redir?code=x&state=y",
        ).forEach { url ->
            assertFalse(url, isRedditFrontPage(Uri.parse(url)))
        }
    }

    @Test
    fun signedInTheFrontPageReopensTheAuthorizationOnce() {
        assertEquals(
            LoginFrontPageAction.RESUME_AUTHORIZATION,
            loginFrontPageAction(signedIn = true, authorizationResumes = 0, hasAuthorizationUrl = true),
        )
        assertEquals(
            LoginFrontPageAction.CANCEL,
            loginFrontPageAction(signedIn = true, authorizationResumes = 1, hasAuthorizationUrl = true),
        )
        assertEquals(
            LoginFrontPageAction.CANCEL,
            loginFrontPageAction(signedIn = true, authorizationResumes = 0, hasAuthorizationUrl = false),
        )
    }

    @Test
    fun signedOutTheFrontPageIsRedditsCloseAndCancels() {
        assertEquals(
            LoginFrontPageAction.CANCEL,
            loginFrontPageAction(signedIn = false, authorizationResumes = 0, hasAuthorizationUrl = true),
        )
    }

    @Test
    fun onlyTheRedditSessionCookieMeansSignedIn() {
        assertTrue(hasRedditSessionCookie("token_v2=a; reddit_session=b; loid=c"))
        assertTrue(hasRedditSessionCookie("reddit_session=b"))
        // The anonymous cookies of a signed-out window (device, 2026-09-27).
        assertFalse(
            hasRedditSessionCookie(
                "token_v2=a; csrf_token=b; session_tracker=c; edgebucket=d; loid=e; csv=f; g_state=g",
            ),
        )
        assertFalse(hasRedditSessionCookie("not_reddit_session=x"))
        assertFalse(hasRedditSessionCookie(null))
        assertFalse(hasRedditSessionCookie(""))
    }
}
