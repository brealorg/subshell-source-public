package app.subshell.prototype.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import java.security.MessageDigest
import app.subshell.prototype.ExternalLaunchToken
import app.subshell.prototype.MainActivity
import app.subshell.prototype.data.auth.AuthCallbackFailureKind
import app.subshell.prototype.data.auth.AuthCallbackResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthCallbackProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callbackUri = intent?.data
        lifecycleScope.launch {
            val result = callbackUri?.let { processAuthCallbackOffMain(applicationContext, it) }
            // SECAUDIT08_CALLBACK_OPENS_SUBSHELLS_OWN_TASK_V1 (security audit #8): a
            // callback from the browser starts in the browser's task; the result
            // goes to the MainActivity in Subshell's own task, as an external
            // link does (OCB-577), not to a second one beside the browser.
            val main = Intent(this@AuthCallbackActivity, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            externalCallbackResultToken(result)?.let(main::putAuthCallbackResult)
            startActivity(main)
            finish()
        }
    }
}

// LOW4_CALLBACK_PROCESSED_OFF_MAIN_V1 (OCB-478): the callback's Keystore reads,
// deletes and encrypted write run on IO; the result is delivered on Main.
internal suspend fun processAuthCallbackOffMain(
    context: Context,
    callbackUri: Uri,
    store: () -> EncryptedAuthSessionStore = {
        EncryptedAuthSessionStore(context.applicationContext)
    },
): AuthCallbackResult = withContext(Dispatchers.IO) {
    OAuthCallbackProcessor(store = store()).process(callbackUri)
}

internal fun AuthCallbackResult?.toResultToken(): String = when (this) {
    is AuthCallbackResult.Accepted -> if (denied) "denied" else "accepted"
    is AuthCallbackResult.Rejected -> "rejected:${kind.name}"
    null -> "missing_uri"
}

/**
 * SECAUDIT07_STRAY_CALLBACK_KEEPS_THE_SIGN_IN_V1 (security audit #7): anyone
 * can open the callback address without the sign-in's state. A callback for
 * another address or with another state is not this sign-in's answer, so it
 * is dropped and the sign-in keeps waiting for Reddit's own.
 */
internal fun externalCallbackResultToken(result: AuthCallbackResult?): String? = when {
    result is AuthCallbackResult.Rejected &&
        (
            result.kind == AuthCallbackFailureKind.STATE_MISMATCH ||
                result.kind == AuthCallbackFailureKind.REDIRECT_URI_MISMATCH
            ) -> null
    else -> result.toResultToken()
}

internal const val AUTH_CALLBACK_RESULT_EXTRA = "app.subshell.prototype.auth.CALLBACK_RESULT"
internal const val AUTH_CALLBACK_PROCESS_TOKEN_EXTRA = "app.subshell.prototype.auth.CALLBACK_PROCESS_TOKEN"

/**
 * SECAUDIT07_CALLBACK_RESULT_CARRIES_THE_PROCESS_TOKEN_V1: MainActivity is
 * exported, so another app could hand it a sign-in result ("cancelled") and
 * end a sign-in in progress. The callback and login windows add the
 * process-local token another app cannot know; a result without it is dropped.
 */
internal fun Intent.putAuthCallbackResult(resultToken: String): Intent =
    putExtra(AUTH_CALLBACK_RESULT_EXTRA, resultToken)
        .putExtra(AUTH_CALLBACK_PROCESS_TOKEN_EXTRA, ExternalLaunchToken.value)

internal fun takeAuthCallbackResult(intent: Intent?): String? {
    val candidate = intent ?: return null
    val result = candidate.getStringExtra(AUTH_CALLBACK_RESULT_EXTRA)?.takeIf(String::isNotBlank)
    val processToken = candidate.getStringExtra(AUTH_CALLBACK_PROCESS_TOKEN_EXTRA)
    candidate.removeExtra(AUTH_CALLBACK_RESULT_EXTRA)
    candidate.removeExtra(AUTH_CALLBACK_PROCESS_TOKEN_EXTRA)
    if (result == null || processToken == null) return null
    val fromThisProcess = MessageDigest.isEqual(
        processToken.toByteArray(Charsets.UTF_8),
        ExternalLaunchToken.value.toByteArray(Charsets.UTF_8),
    )
    return result.takeIf { fromThisProcess }
}
