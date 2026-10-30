package app.subshell.prototype.data.account

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ISSUE32_REDDIT_REFUSED_ACCESS_IS_ONE_STATE_V1 (#32, owner: "steg 2 og 3 er
// go"; keep the sign-in): Reddit removes API access from unregistered apps
// from 2027-01-12. When the token endpoint refuses the app's Client ID itself
// (invalid_client, unauthorized_client, or a 401 that is not invalid_grant),
// the account is not the problem: its sign-in is kept, the app shows one
// notice and what is stored on the device, and no Reddit call is made until
// a check is allowed (once per process start, or Try again). A token that
// works again, or another Client ID, ends it.

// The state lives here so the ui can follow it without seeing data.auth
// (UI_AND_CACHE_NEVER_SEE_AUTH); OAuthSessionManager decides and records it.

/** What the app knows about Reddit refusing its Client ID. */
internal data class RedditAccessRefusalState(
    val sinceEpochMs: Long,
    /** SHA-256 of the refused Client ID (oauthClientIdFingerprint); never the ID. */
    val clientFingerprint: String,
)

/** The provider code a refused read carries in its FailureDetails. */
internal const val CLIENT_ACCESS_REFUSED_PROVIDER_CODE = "oauth_client_access_refused"

internal object RedditAccessRefusal {
    private val mutableState = MutableStateFlow<RedditAccessRefusalState?>(null)
    val state: StateFlow<RedditAccessRefusalState?> = mutableState.asStateFlow()

    @Volatile
    private var file: File? = null

    // A check may go to Reddit once per process; Try again allows one more.
    @Volatile
    private var checkAllowed = true

    /** Reads the stored state once per process. */
    fun install(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val target = File(File(context.applicationContext.noBackupFilesDir, "auth-state"), FILE_NAME)
            file = target
            mutableState.value = runCatching { decode(target.readText()) }.getOrNull()
        }
    }

    /** True while Reddit refuses this Client ID and no check is allowed now. */
    fun blocks(clientFingerprint: String): Boolean {
        val refusal = mutableState.value ?: return false
        if (refusal.clientFingerprint != clientFingerprint) return false
        synchronized(this) {
            if (!checkAllowed) return true
            checkAllowed = false
            return false
        }
    }

    /** True while Reddit refuses this Client ID (whether or not a check is allowed). */
    fun refuses(clientFingerprint: String): Boolean =
        mutableState.value?.clientFingerprint == clientFingerprint

    fun markRefused(clientFingerprint: String, atEpochMs: Long) = synchronized(this) {
        // This answer was the check; the next one waits for Try again or a new process.
        checkAllowed = false
        if (clientFingerprint.isBlank()) return@synchronized
        if (mutableState.value?.clientFingerprint == clientFingerprint) return@synchronized
        val refusal = RedditAccessRefusalState(atEpochMs, clientFingerprint)
        mutableState.value = refusal
        write(refusal)
    }

    /**
     * A token request worked: Reddit lets the app in again, with this Client ID
     * or with another one the reader switched to (the refusal then no longer applies).
     */
    fun markAllowed() = synchronized(this) {
        mutableState.value ?: return@synchronized
        mutableState.value = null
        write(null)
    }

    /** Try again: the next read may ask Reddit once. */
    fun allowCheck() {
        checkAllowed = true
    }

    internal fun resetForTest(state: RedditAccessRefusalState? = null) = synchronized(this) {
        file = null
        checkAllowed = true
        mutableState.value = state
    }

    private fun write(refusal: RedditAccessRefusalState?) {
        val target = file ?: return
        runCatching {
            if (refusal == null) {
                target.delete()
            } else {
                target.parentFile?.mkdirs()
                target.writeText("${refusal.sinceEpochMs}|${refusal.clientFingerprint}\n")
            }
        }
    }

    private fun decode(text: String): RedditAccessRefusalState? {
        val parts = text.trim().split('|')
        if (parts.size != 2 || parts[1].isBlank() || !parts[1].all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return null
        }
        return RedditAccessRefusalState(parts[0].toLong(), parts[1])
    }

    private const val FILE_NAME = "reddit-access-refused-v1.txt"
}
