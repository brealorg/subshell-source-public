package app.subshell.prototype.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.subshell.prototype.MainActivity
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
            startActivity(
                Intent(this@AuthCallbackActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(AUTH_CALLBACK_RESULT_EXTRA, result.toResultToken()),
            )
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

internal const val AUTH_CALLBACK_RESULT_EXTRA = "app.subshell.prototype.auth.CALLBACK_RESULT"
