package app.subshell.prototype.auth

import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AUTH_TAB_REJECTED_TOKEN_PREFIX

/**
 * SUBSHELL_AUTHCALLBACK06_AUTH_TAB_DISMISS_DISPATCH_V1
 * What an auth tab result hands to the callback routing: a callback URI is
 * processed into its result token; a tab the user closed
 * ([AuthTabIntent.RESULT_CANCELED]) becomes the cancelled token, so the
 * current owner leaves AWAITING_CALLBACK the way a cancelled RedReader
 * compatibility login does. AUTHCALLBACK11: any other result without a URI
 * (verification failed or timed out, unknown) becomes a rejected token, so
 * the owner shows a failure it can retry instead of waiting forever. Owners
 * check for a stored callback before reading the token, so a callback that
 * arrived another way still completes.
 */
internal suspend fun dispatchAuthTabResult(
    resultCode: Int,
    resultUri: Uri?,
    processCallback: suspend (Uri) -> String,
    deliver: (String) -> Unit,
) {
    val resultToken = when {
        resultUri != null -> processCallback(resultUri)
        resultCode == AuthTabIntent.RESULT_CANCELED -> AUTH_CALLBACK_CANCELLED_TOKEN
        resultCode == AuthTabIntent.RESULT_VERIFICATION_FAILED ->
            AUTH_TAB_REJECTED_TOKEN_PREFIX + "VERIFICATION_FAILED"
        resultCode == AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT ->
            AUTH_TAB_REJECTED_TOKEN_PREFIX + "VERIFICATION_TIMED_OUT"
        else -> AUTH_TAB_REJECTED_TOKEN_PREFIX + "NO_CALLBACK"
    }
    deliver(resultToken)
}
