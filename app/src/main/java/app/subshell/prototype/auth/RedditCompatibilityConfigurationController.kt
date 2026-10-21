package app.subshell.prototype.auth

import android.content.Context
import android.net.Uri
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.isValidOAuthRedirectUri
import app.subshell.prototype.data.auth.isValidOAuthUserAgent
import app.subshell.prototype.data.auth.reddit.RedditOAuth

internal class RedditCompatibilityConfigurationController(
    context: Context,
) {
    private val store = EncryptedAuthSessionStore(context.applicationContext)

    fun isConfigured(): Boolean =
        store.oauthClientSelection()?.mode == OAuthClientMode.REDREADER_COMPATIBILITY

    fun isValidClientId(value: String): Boolean =
        RedditOAuth.isValidInstalledClientId(clientIdFromPaste(value))

    val compatibilityRedirectUri: String
        get() = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI

    val compatibilityUserAgent: String
        get() = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT

    // ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1: what the dialog shows;
    // the client ID itself never leaves the store.
    fun apiValues(): RedditApiValues {
        val selection = store.oauthClientSelection()
            ?.takeIf { it.mode == OAuthClientMode.REDREADER_COMPATIBILITY }
        return RedditApiValues(
            clientIdConfigured = selection != null,
            redirectUri = selection?.redirectUri ?: compatibilityRedirectUri,
            userAgent = selection?.userAgent ?: compatibilityUserAgent,
        )
    }

    // ISSUE240_DIAGNOSTICS_FLAG_CHANGED_API_VALUES_V1
    fun apiValuesDiagnosticToken(): String =
        runCatching { if (apiValues().customized) "custom" else "default" }
            .getOrDefault("unknown")

    /** True when [value] is a client ID other than the stored one. */
    fun changesClientId(value: String): Boolean {
        val current = store.oauthClientSelection()
            ?.takeIf { it.mode == OAuthClientMode.REDREADER_COMPATIBILITY }
            ?.clientId
            ?: return false
        return clientIdFromPaste(value) != current
    }

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

    /**
     * Saves the client ID (a blank [clientId] keeps the stored one) with the
     * redirect URI and user agent; null or a shipped value means the default.
     */
    fun save(
        clientId: String,
        redirectUri: String? = null,
        userAgent: String? = null,
    ) {
        val value = clientIdFromPaste(clientId).ifEmpty {
            requireNotNull(store.oauthClientSelection()?.clientId) { "No client ID to keep" }
        }
        store.saveOAuthClientSelection(
            RedditOAuth.redReaderCompatibilitySelection(
                clientId = value,
                redirectUri = redirectUri,
                userAgent = userAgent,
            ),
        )
    }

    /** Back to the shipped redirect URI and user agent; the client ID stays. */
    fun resetApiValues() {
        save(clientId = "")
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

internal data class RedditApiValues(
    val clientIdConfigured: Boolean,
    val redirectUri: String,
    val userAgent: String,
) {
    val customized: Boolean
        get() = redirectUri != RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI ||
            userAgent != RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT
}

// ISSUE240: what the UI may know of the API values (ui never sees data.auth).
internal object RedditApiValueRules {
    val defaultRedirectUri: String = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI
    val defaultUserAgent: String = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT

    fun isValidRedirectUri(value: String): Boolean = isValidOAuthRedirectUri(value)

    fun isValidUserAgent(value: String): Boolean = isValidOAuthUserAgent(value)
}

/**
 * ISSUE240: Reddit's email names the value "App ID: …"; a pasted line keeps
 * only the value.
 */
internal fun clientIdFromPaste(value: String): String =
    value.trim()
        .replace(Regex("^(app|client)[ _-]?id\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
