package app.subshell.prototype.auth

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import app.subshell.prototype.chat.redditChatSessionFingerprint
import app.subshell.prototype.data.auth.RedditWebSessionOwner
import app.subshell.prototype.data.auth.RedditWebSessionOwnership
import app.subshell.prototype.data.auth.redditWebSessionSurvivesRemoval
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * SECAUDIT02_WEB_SESSION_GOES_WITH_ITS_ACCOUNT_V1: the WebView side of
 * [RedditWebSessionOwnership]. The owner is device-local (its own preferences
 * file, never in a backup) and holds the account's stable ID and a SHA-256 of
 * the web login, never the cookie itself. [recordsOwner] is false for a native
 * Client ID, whose sign-in runs in the browser and never touches this store.
 */
internal class WebViewRedditSessionOwnership(
    context: Context,
    private val recordsOwner: Boolean,
) : RedditWebSessionOwnership {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun signedInAs(accountStableId: String) {
        if (!recordsOwner) return
        withContext(Dispatchers.Main.immediate) {
            val fingerprint = currentFingerprint()
            val editor = preferences.edit()
            if (fingerprint == null) {
                editor.clear()
            } else {
                editor
                    .putString(OWNER_KEY, accountStableId.trim())
                    .putString(FINGERPRINT_KEY, fingerprint)
            }
            editor.apply()
        }
    }

    override suspend fun accountRemoved(accountStableId: String) {
        withContext(Dispatchers.Main.immediate) {
            if (
                redditWebSessionSurvivesRemoval(
                    owner = storedOwner(),
                    currentFingerprint = currentFingerprint(),
                    removedAccountStableId = accountStableId,
                )
            ) {
                return@withContext
            }
            // Direct Chat reads the cookie before every token, so its cached
            // token is dropped by the same deletion.
            WebStorage.getInstance().deleteAllData()
            val cookieManager = CookieManager.getInstance()
            suspendCancellableCoroutine { continuation ->
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            preferences.edit().clear().apply()
        }
    }

    private fun currentFingerprint(): String? =
        CookieManager.getInstance().getCookie(REDDIT_ORIGIN)?.let(::redditChatSessionFingerprint)

    private fun storedOwner(): RedditWebSessionOwner? {
        val owner = preferences.getString(OWNER_KEY, null) ?: return null
        val fingerprint = preferences.getString(FINGERPRINT_KEY, null) ?: return null
        return RedditWebSessionOwner(owner, fingerprint)
    }

    private companion object {
        const val PREFERENCES = "subshell_reddit_web_session"
        const val OWNER_KEY = "owner_account_stable_id"
        const val FINGERPRINT_KEY = "session_sha256"
        const val REDDIT_ORIGIN = "https://www.reddit.com"
    }
}
