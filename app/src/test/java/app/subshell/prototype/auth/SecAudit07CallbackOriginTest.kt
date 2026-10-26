package app.subshell.prototype.auth

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.ExternalLaunchToken
import app.subshell.prototype.MainActivity
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthCallbackFailureKind
import app.subshell.prototype.data.auth.AuthCallbackResult
import app.subshell.prototype.data.auth.OAuthProvider
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
import org.robolectric.shadows.ShadowLooper

/**
 * SECAUDIT07/08 (security audit #7, #8): the exported MainActivity took a
 * sign-in result ("cancelled", "rejected:…") from any app, and a callback with
 * another state ended the sign-in in progress. A result now counts only with
 * the process-local token the callback and login windows add; a stray
 * callback is dropped and the sign-in keeps waiting. A callback from the
 * browser opens Subshell's own task instead of a second MainActivity there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit07CallbackOriginTest {
    @Test
    fun aResultFromAnotherAppIsDropped() {
        val forged = Intent()
            .putExtra(AUTH_CALLBACK_RESULT_EXTRA, AUTH_CALLBACK_CANCELLED_TOKEN)
        assertNull(takeAuthCallbackResult(forged))
        assertFalse(forged.hasExtra(AUTH_CALLBACK_RESULT_EXTRA))

        val guessed = Intent()
            .putExtra(AUTH_CALLBACK_RESULT_EXTRA, AUTH_CALLBACK_CANCELLED_TOKEN)
            .putExtra(AUTH_CALLBACK_PROCESS_TOKEN_EXTRA, "00000000-0000-0000-0000-000000000000")
        assertNull(takeAuthCallbackResult(guessed))
        assertFalse(guessed.hasExtra(AUTH_CALLBACK_PROCESS_TOKEN_EXTRA))
    }

    @Test
    fun aResultFromTheCallbackOrLoginWindowIsTakenOnce() {
        val own = Intent().putAuthCallbackResult("accepted")
        assertEquals(ExternalLaunchToken.value, own.getStringExtra(AUTH_CALLBACK_PROCESS_TOKEN_EXTRA))

        assertEquals("accepted", takeAuthCallbackResult(own))
        assertNull(takeAuthCallbackResult(own))
    }

    @Test
    fun aCallbackWithAnotherStateOrAddressIsNotTheSignInsAnswer() {
        assertNull(externalCallbackResultToken(AuthCallbackResult.Rejected(AuthCallbackFailureKind.STATE_MISMATCH)))
        assertNull(externalCallbackResultToken(AuthCallbackResult.Rejected(AuthCallbackFailureKind.REDIRECT_URI_MISMATCH)))

        assertEquals(
            "rejected:PENDING_AUTHORIZATION_EXPIRED",
            externalCallbackResultToken(AuthCallbackResult.Rejected(AuthCallbackFailureKind.PENDING_AUTHORIZATION_EXPIRED)),
        )
        assertEquals("accepted", externalCallbackResultToken(AuthCallbackResult.Accepted(OAuthProvider.REDDIT, denied = false)))
        assertEquals("missing_uri", externalCallbackResultToken(null))
    }

    @Test
    fun theBrowserCallbackOpensSubshellsOwnTask() {
        val callback = Robolectric.buildActivity(
            AuthCallbackActivity::class.java,
            Intent(Intent.ACTION_VIEW, Uri.parse("subshell-auth-test://oauth/callback?state=s&code=c")),
        ).setup().get()
        ShadowLooper.idleMainLooper()
        repeat(50) {
            if (shadowOf(callback).peekNextStartedActivity() != null) return@repeat
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }

        val main = shadowOf(callback).nextStartedActivity
        assertEquals(MainActivity::class.java.name, main.component?.className)
        assertTrue(main.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(ExternalLaunchToken.value, main.getStringExtra(AUTH_CALLBACK_PROCESS_TOKEN_EXTRA))

        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val info: ActivityInfo = application.packageManager.getActivityInfo(
            android.content.ComponentName(application, AuthCallbackActivity::class.java),
            PackageManager.GET_META_DATA,
        )
        assertTrue(info.taskAffinity.isNullOrEmpty())
    }
}
