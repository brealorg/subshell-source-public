package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName
import java.net.URI

// MODERATION02A2_TYPED_REPORT_DETAILS_AND_UNKNOWN_STATE_V1
// These values are moderator-only, in-memory presentation data. Never persist
// report text or infer a reporter identity from anonymous user reports.
data class RedditModeratorReportReason(
    val reason: String,
    val count: Int? = null,
    val moderatorName: String? = null,
)

data class RedditModeratorReportDetails(
    val userReports: List<RedditModeratorReportReason>? = null,
    val moderatorReports: List<RedditModeratorReportReason>? = null,
    val totalCount: Int? = null,
    val ignored: Boolean? = null,
    val canModerate: Boolean? = null,
    val incomplete: Boolean = false,
)

data class ModeratorReportTarget(
    val fullname: String,
    val communityName: String? = null,
    // ISSUE281_READS_AVOID_API_INFO_V1: the post a comment is read from
    // (Reddit no longer answers /api/info); a comment without it is not found.
    // A post is its own.
    val postFullname: String? = fullname.takeIf { it.startsWith("t3_") },
) {
    init {
        require(fullname.matches(Regex("t[13]_[a-z0-9]{1,32}")))
        require(communityName == null || normalizeRedditCommunityName(communityName) == communityName)
        require(postFullname == null || postFullname.matches(Regex("t3_[a-z0-9]{1,32}")))
    }
}

// Exact IDs only. URLs supply identifiers, never a host to send a request to.
fun moderatorReportTargetOrNull(
    raw: String,
    communityHint: String? = null,
    postHint: String? = null,
): ModeratorReportTarget? {
    val input = raw.trim()
    if (input.isEmpty() || input.length > 2_000) return null
    val hint = communityHint?.let {
        runCatching { normalizeRedditCommunityName(it) }.getOrNull() ?: return null
    }
    val fullname = input.lowercase()
    if (fullname.matches(Regex("t[13]_[a-z0-9]{1,32}"))) {
        val post = if (fullname.startsWith("t3_")) {
            fullname
        } else {
            postHint?.trim()?.lowercase()?.let { if (it.startsWith("t3_")) it else "t3_$it" }
                ?.takeIf { it.matches(Regex("t3_[a-z0-9]{1,32}")) }
        }
        return ModeratorReportTarget(fullname, hint, post)
    }
    val uri = runCatching { URI(input) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()
    if (uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return null
    if (host != null) {
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        // ACCOUNT02_REPORT_TARGET_ANY_REDDIT_HOST_V1: every *.reddit.com host,
        // like the deep-link resolver; path words and ids ignore case.
        if (host != "reddit.com" && !host.endsWith(".reddit.com") && host != "redd.it") return null
    } else if (!input.startsWith("/") || input.startsWith("//") || uri.scheme != null) {
        return null
    }
    val parts = uri.rawPath.orEmpty().trim('/').split('/')
    val id = Regex("[a-z0-9]{1,32}")
    if (host == "redd.it") {
        val shortId = parts[0].lowercase()
        if (parts.size != 1 || !shortId.matches(id)) return null
        return ModeratorReportTarget("t3_$shortId", hint)
    }
    val hasCommunity = parts.size >= 2 && parts[0].equals("r", ignoreCase = true)
    val community = if (hasCommunity) {
        runCatching { normalizeRedditCommunityName(parts[1]) }.getOrNull() ?: return null
    } else hint
    if (hint != null && !community.equals(hint, ignoreCase = true)) return null
    val offset = if (hasCommunity) 2 else 0
    if (!parts.getOrNull(offset).equals("comments", ignoreCase = true)) return null
    val postId = parts.getOrNull(offset + 1)?.lowercase()?.takeIf { it.matches(id) } ?: return null
    if (parts.size !in (offset + 2)..(offset + 4)) return null
    val commentId = parts.getOrNull(offset + 3)?.lowercase()
    if (commentId != null && !commentId.matches(id)) return null
    return ModeratorReportTarget(
        fullname = if (commentId == null) "t3_$postId" else "t1_$commentId",
        communityName = community,
        postFullname = "t3_$postId",
    )
}

internal fun RedditModeratorAction.isReportControl(): Boolean =
    this == RedditModeratorAction.IGNORE_REPORTS || this == RedditModeratorAction.UNIGNORE_REPORTS

internal fun RedditModeratorAction.desiredReportsIgnored(): Boolean? = when (this) {
    RedditModeratorAction.IGNORE_REPORTS -> true
    RedditModeratorAction.UNIGNORE_REPORTS -> false
    else -> null
}

internal fun RedditModeratorQueueItem.reportTarget(): ModeratorReportTarget =
    ModeratorReportTarget(
        fullname = fullname,
        communityName = communityName,
        postFullname = if (fullname.startsWith("t3_")) {
            fullname
        } else {
            permalink?.let { link -> moderatorReportTargetOrNull(link)?.postFullname }
        },
    )

internal fun moderatorReportCountLabel(item: RedditModeratorQueueItem): String =
    when (val count = item.reportDetails.totalCount) {
        null -> "Report count unavailable"
        0 -> if (item.reportDetails.incomplete) "Report details incomplete" else "No reports returned"
        1 -> "1 report"
        else -> "$count reports"
    }

enum class ModeratorReportsNotice {
    INVALID_TARGET, NOT_FOUND, READ_FAILED, NO_PERMISSION, STATE_UNKNOWN,
    SETTINGS_CHANGED, BUSY, AUTHORIZATION_REQUIRED, WRITE_FAILED,
    VERIFIED, VERIFIED_AFTER_UNKNOWN, NOT_CONFIRMED, ACCOUNT_CHANGED,
}

data class ModeratorReportsUiState(
    val accountStableId: String? = null,
    val visible: Boolean = false,
    val target: ModeratorReportTarget? = null,
    val item: RedditModeratorQueueItem? = null,
    val loading: Boolean = false,
    val mutating: Boolean = false,
    val revision: Long = 0,
    val notice: ModeratorReportsNotice? = null,
    val recentTargets: List<ModeratorReportTarget> = emptyList(),
    // DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1: what Reddit answered for a failed read or write.
    val failureDetails: FailureDetails? = null,
) {
    val busy: Boolean get() = loading || mutating
}

data class ModeratorReportConfirmation(
    val accountStableId: String,
    val target: ModeratorReportTarget,
    val action: RedditModeratorAction,
    val revision: Long,
)

internal fun reportConfirmationOrNull(
    state: ModeratorReportsUiState,
    activeAccountStableId: String,
): ModeratorReportConfirmation? {
    if (!state.visible || state.busy || state.accountStableId != activeAccountStableId) return null
    val item = state.item ?: return null
    val target = state.target ?: return null
    if (item.fullname != target.fullname || (target.communityName != null && target.communityName != item.communityName)) return null
    if (!item.fullname.matches(Regex("t[13]_[a-z0-9]{1,32}"))) return null
    if ((item.kind == RedditModeratorQueueKind.POST) != item.fullname.startsWith("t3_")) return null
    if (item.reportDetails.canModerate != true) return null
    val ignored = item.reportDetails.ignored ?: return null
    return ModeratorReportConfirmation(
        accountStableId = activeAccountStableId,
        target = item.reportTarget(),
        action = if (ignored) RedditModeratorAction.UNIGNORE_REPORTS else RedditModeratorAction.IGNORE_REPORTS,
        revision = state.revision,
    )
}
