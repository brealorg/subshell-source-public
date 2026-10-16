package app.subshell.prototype.ui

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

internal val REDDIT_LINK_HANDLING_HOSTS: Set<String> = linkedSetOf(
    "reddit.com",
    "www.reddit.com",
    "old.reddit.com",
    "new.reddit.com",
    "np.reddit.com",
    "m.reddit.com",
    "redd.it",
)

enum class RedditLinkHandlingStatus {
    UNSUPPORTED,
    DISABLED,
    PARTIAL,
    ENABLED,
}

data class RedditLinkHandlingState(
    val supported: Boolean = false,
    val linkHandlingAllowed: Boolean = false,
    val selectedHostCount: Int = 0,
    val supportedHostCount: Int = REDDIT_LINK_HANDLING_HOSTS.size,
    val status: RedditLinkHandlingStatus = RedditLinkHandlingStatus.UNSUPPORTED,
)

internal fun resolveRedditLinkHandlingState(
    supported: Boolean,
    linkHandlingAllowed: Boolean,
    hostToState: Map<String, Int>,
): RedditLinkHandlingState {
    if (!supported) return RedditLinkHandlingState()

    val selectedHosts = REDDIT_LINK_HANDLING_HOSTS.count { host ->
        when (hostToState[host]) {
            DomainVerificationUserState.DOMAIN_STATE_SELECTED,
            DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> true

            else -> false
        }
    }

    val status = when {
        !linkHandlingAllowed ->
            RedditLinkHandlingStatus.DISABLED

        selectedHosts == REDDIT_LINK_HANDLING_HOSTS.size ->
            RedditLinkHandlingStatus.ENABLED

        selectedHosts > 0 ->
            RedditLinkHandlingStatus.PARTIAL

        else ->
            RedditLinkHandlingStatus.DISABLED
    }

    return RedditLinkHandlingState(
        supported = true,
        linkHandlingAllowed = linkHandlingAllowed,
        selectedHostCount = selectedHosts,
        status = status,
    )
}

internal fun shouldShowRedditLinkHandlingPrompt(
    hasAuthenticatedAccounts: Boolean,
    promptSeen: Boolean,
    state: RedditLinkHandlingState,
): Boolean =
    hasAuthenticatedAccounts &&
        state.supported &&
        !promptSeen &&
        state.status != RedditLinkHandlingStatus.ENABLED

internal fun readRedditLinkHandlingState(
    context: Context,
): RedditLinkHandlingState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return RedditLinkHandlingState()
    }
    return readRedditLinkHandlingStateApi31(context)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun readRedditLinkHandlingStateApi31(
    context: Context,
): RedditLinkHandlingState = runCatching {
    val manager =
        context.getSystemService(
            DomainVerificationManager::class.java,
        )
    val userState =
        manager?.getDomainVerificationUserState(
            context.packageName,
        )
    resolveRedditLinkHandlingState(
        supported = userState != null,
        linkHandlingAllowed =
            userState?.isLinkHandlingAllowed == true,
        hostToState =
            userState?.hostToStateMap.orEmpty(),
    )
}.getOrElse {
    RedditLinkHandlingState(
        supported = true,
        linkHandlingAllowed = false,
        status = RedditLinkHandlingStatus.DISABLED,
    )
}

internal fun openRedditLinkHandlingSettings(
    context: Context,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return false
    }
    val packageUri =
        Uri.parse("package:${context.packageName}")
    val preferred =
        Intent(
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            packageUri,
        )
    val fallback =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            packageUri,
        )
    val intent = when {
        preferred.resolveActivity(context.packageManager) != null ->
            preferred

        fallback.resolveActivity(context.packageManager) != null ->
            fallback

        else ->
            return false
    }
    context.startActivity(intent)
    return true
}

internal const val REDDIT_EXTERNAL_POLL_REBOUND_TTL_MILLIS = 60_000L

internal fun shouldConsumeRedditExternalPollRebound(
    pendingPostId: String?,
    pendingStartedAtEpochMillis: Long,
    rawUri: String?,
    nowEpochMillis: Long,
): Boolean {
    val postId = pendingPostId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return false
    if (!redditTargetMatchesPost(postId, postId)) return false

    val ageMillis = nowEpochMillis - pendingStartedAtEpochMillis
    if (ageMillis !in 0L..REDDIT_EXTERNAL_POLL_REBOUND_TTL_MILLIS) {
        return false
    }

    val inboundPostId = resolveRedditTarget(rawUri)
        ?.postIdOrNull()
        ?: return false
    return redditTargetMatchesPost(postId, inboundPostId)
}

internal class RedditExternalPollHandoffStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun mark(
        postId: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!redditTargetMatchesPost(postId, postId)) {
            clear()
            return false
        }
        return preferences.edit()
            .putString(PENDING_POST_ID, postId.trim().lowercase())
            .putLong(PENDING_STARTED_AT, nowEpochMillis)
            .commit()
    }

    fun consumeIfMatching(
        rawUri: String?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val pendingPostId = preferences.getString(PENDING_POST_ID, null)
        val pendingStartedAt = preferences.getLong(PENDING_STARTED_AT, Long.MIN_VALUE)
        val ageMillis = nowEpochMillis - pendingStartedAt
        if (
            pendingPostId.isNullOrBlank() ||
            pendingStartedAt == Long.MIN_VALUE ||
            ageMillis !in 0L..REDDIT_EXTERNAL_POLL_REBOUND_TTL_MILLIS
        ) {
            clear()
            return false
        }

        val matches = shouldConsumeRedditExternalPollRebound(
            pendingPostId = pendingPostId,
            pendingStartedAtEpochMillis = pendingStartedAt,
            rawUri = rawUri,
            nowEpochMillis = nowEpochMillis,
        )
        if (matches) {
            clear()
        }
        return matches
    }

    fun clear() {
        preferences.edit()
            .remove(PENDING_POST_ID)
            .remove(PENDING_STARTED_AT)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "subshell_external_poll_handoff"
        const val PENDING_POST_ID = "pending_post_id"
        const val PENDING_STARTED_AT = "pending_started_at_epoch_millis"
    }
}

internal class RedditLinkHandlingPromptStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            DEVICE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun wasSeen(): Boolean =
        preferences.getBoolean(
            REDDIT_LINK_PROMPT_SEEN_V1,
            false,
        )

    fun markSeen() {
        preferences.edit()
            .putBoolean(
                REDDIT_LINK_PROMPT_SEEN_V1,
                true,
            )
            .apply()
    }

    internal companion object {
        const val DEVICE_PREFERENCES_NAME =
            "subshell_device_preferences"
        const val REDDIT_LINK_PROMPT_SEEN_V1 =
            "reddit_link_prompt_seen_v1"
    }
}
