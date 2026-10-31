package app.subshell.prototype.data.account

// REDDITCHECK01_CHECK_REDDIT_ACCESS_V1 (2026-10-31): when Reddit shuts a door
// (#281: /api/info, /by_id and /api/morechildren answered with a block page),
// finding out which reads still work took a temporary probe build. Diagnostics ›
// Check Reddit access asks Reddit once for each kind of read the app uses, with
// the active account, and keeps what came back for the diagnostic report.
// Reads only; nothing on Reddit changes.

/** One read the check asked Reddit for. */
data class RedditAccessProbe(
    /** A fixed short name ("me", "api_info"), never a URL or a name from Reddit. */
    val endpoint: String,
    val outcome: RedditAccessProbeOutcome,
    val statusCode: Int? = null,
    /** Reddit's reason token or the failure kind ("network_security_block", "timeout"). */
    val detail: String? = null,
    val elapsedMs: Long = 0L,
)

enum class RedditAccessProbeOutcome(val token: String) {
    ANSWERED("ok"),
    HTTP_FAILURE("http"),
    PROTOCOL_FAILURE("protocol"),
    TRANSPORT_FAILURE("transport"),

    /** Not asked: an earlier read that gives it its post or account name failed. */
    SKIPPED("skipped"),
    ;

    companion object {
        fun fromToken(token: String): RedditAccessProbeOutcome? = entries.firstOrNull { it.token == token }
    }
}

sealed interface RedditAccessCheckResult {
    data class Checked(val probes: List<RedditAccessProbe>) : RedditAccessCheckResult

    /** Not asked or not finished: signed out, no session, or the account changed. */
    data object NotChecked : RedditAccessCheckResult
}

internal fun interface RedditAccessChecker {
    suspend fun check(account: String, isCurrent: () -> Boolean): RedditAccessCheckResult
}
