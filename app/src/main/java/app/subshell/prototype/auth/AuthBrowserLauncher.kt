package app.subshell.prototype.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.core.net.toUri
import app.subshell.prototype.data.auth.OAuthClientMode

internal object AuthBrowserLauncher {
    fun launch(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        authorizationUrl: String,
        clientMode: OAuthClientMode,
        freshAccountSession: Boolean = false,
    ) {
        val uri = authorizationUrl.toUri()
        require(uri.scheme == "https") { "OAuth authorization must launch over HTTPS" }
        if (clientMode == OAuthClientMode.REDREADER_COMPATIBILITY) {
            context.startActivity(
                RedReaderCompatibilityLoginActivity.createIntent(
                    context = context,
                    authorizationUrl = authorizationUrl,
                    freshAccountSession = freshAccountSession,
                ),
            )
            return
        }

        val callbackUri = requireNotNull(uri.getQueryParameter("redirect_uri")).toUri()
        require(callbackUri.scheme == "https") {
            "Native OAuth callback must use HTTPS"
        }
        val defaultProvider = CustomTabsClient.getPackageName(context, null)
        val authTabProvider = defaultProvider?.takeIf { provider ->
            CustomTabsClient.isAuthTabSupported(context, provider)
        }
        if (authTabProvider != null) {
            AuthTabIntent.Builder()
                // LOW9_FRESH_SESSION_IN_NATIVE_MODE_V1 (OCB-580): Add account and
                // reconnect open an ephemeral Auth Tab where the browser supports
                // it, so Reddit does not offer the account the browser is
                // already signed in to (the RedReader window clears its own).
                .setEphemeralBrowsingEnabled(
                    freshAccountSession &&
                        CustomTabsClient.isEphemeralBrowsingSupported(context, authTabProvider),
                )
                .build()
                .also { authTab ->
                    authTab.intent.setPackage(authTabProvider)
                }
                .launch(
                    launcher,
                    uri,
                    requireNotNull(callbackUri.host),
                    callbackUri.path.orEmpty(),
                )
            return
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(defaultProvider),
        )
    }
}
