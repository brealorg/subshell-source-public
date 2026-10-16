package app.subshell.prototype.auth

import android.content.Context
import android.net.Uri
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.RedditOAuth

internal class RedditCompatibilityConfigurationController(
    context: Context,
) {
    private val store = EncryptedAuthSessionStore(context.applicationContext)

    fun isConfigured(): Boolean =
        store.oauthClientSelection()?.mode == OAuthClientMode.REDREADER_COMPATIBILITY

    fun isValidClientId(value: String): Boolean =
        RedditOAuth.isValidInstalledClientId(value)

    val compatibilityRedirectUri: String
        get() = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI

    val compatibilityUserAgent: String
        get() = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT

    val redReaderGooglePlayUri: String
        get() = Uri.Builder()
            .scheme(HTTPS_SCHEME)
            .authority("play.google.com")
            .appendPath("store")
            .appendPath("apps")
            .appendPath("details")
            .appendQueryParameter(
                "id",
                REDREADER_PACKAGE_NAME,
            )
            .build()
            .toString()

    val redReaderFdroidUri: String
        get() = Uri.Builder()
            .scheme(HTTPS_SCHEME)
            .authority("f-droid.org")
            .appendPath("packages")
            .appendPath(REDREADER_PACKAGE_NAME)
            .build()
            .toString()

    fun save(clientId: String) {
        store.saveOAuthClientSelection(
            RedditOAuth.redReaderCompatibilitySelection(clientId),
        )
    }

    fun remove() {
        store.deleteOAuthClientSelection()
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val REDREADER_PACKAGE_NAME =
            "org.quantumbadger.redreader"
    }
}
