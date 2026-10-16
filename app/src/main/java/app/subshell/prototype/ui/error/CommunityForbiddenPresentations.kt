package app.subshell.prototype.ui.error

import app.subshell.prototype.data.error.FailureDetails

// ISSUE211_FORBIDDEN_COMMUNITY_SAYS_WHY_V1 (#211): a community feed that
// Reddit refuses with 403 carries Reddit's reason (read by the HTTP client).
// The owner's r/armatureporn showed "Reddit rejected the feed request" while
// Reddit said "private"; the notice now says what applies. Retry does not
// help a private or Premium-only community, so neither offers it. A
// quarantined one offers "View anyway", which opts in only on that tap.

internal enum class CommunityForbiddenReason { PRIVATE, QUARANTINED, PREMIUM_ONLY, BANNED }

internal fun communityForbiddenReason(details: FailureDetails?): CommunityForbiddenReason? {
    val reason = details?.providerCode?.value ?: return null
    return when (details.httpStatusCode) {
        403 -> when (reason) {
            "private" -> CommunityForbiddenReason.PRIVATE
            "quarantined" -> CommunityForbiddenReason.QUARANTINED
            "gold_only", "gold_restricted", "premium_only" -> CommunityForbiddenReason.PREMIUM_ONLY
            else -> null
        }
        // Reddit answers a banned community with 404 and reason "banned".
        404 -> CommunityForbiddenReason.BANNED.takeIf { reason == "banned" }
        else -> null
    }
}

internal fun communityForbiddenPresentation(
    reason: CommunityForbiddenReason,
    communityLabel: String,
    showingCachedContent: Boolean,
): ErrorPresentation = ErrorPresentation(
    kind = if (showingCachedContent) ErrorPresentationKind.DEGRADED else ErrorPresentationKind.BLOCKING,
    title = when (reason) {
        CommunityForbiddenReason.PRIVATE -> "$communityLabel is private"
        CommunityForbiddenReason.QUARANTINED -> "$communityLabel is quarantined"
        CommunityForbiddenReason.PREMIUM_ONLY -> "$communityLabel is for Reddit Premium members"
        CommunityForbiddenReason.BANNED -> "$communityLabel is banned"
    },
    message = when (reason) {
        CommunityForbiddenReason.PRIVATE ->
            "Only members the moderators have approved can see its posts."
        CommunityForbiddenReason.QUARANTINED ->
            "Reddit has quarantined this community for content many find offensive or upsetting. " +
                "Its posts show only after you choose to view it."
        CommunityForbiddenReason.PREMIUM_ONLY ->
            "Only Reddit Premium members can see its posts."
        CommunityForbiddenReason.BANNED ->
            "Reddit has banned this community, so its posts can't be shown."
    },
    primaryAction = if (reason == CommunityForbiddenReason.QUARANTINED) {
        // The tray's and the notice's one action button; it runs the opt-in.
        ErrorAction(ErrorActionKind.RETRY, "View anyway")
    } else {
        null
    },
)

/**
 * ISSUE211_QUARANTINE_OPT_IN_RESULT_IS_SHOWN_V1: what "View anyway" says when
 * the opt-in did not go through. Null when it was sent or its answer was lost:
 * the reload that follows shows whether it took.
 */
internal fun quarantineOptInPresentation(
    result: app.subshell.prototype.data.account.QuarantineOptInResult,
    communityLabel: String,
): ErrorPresentation? = when (result) {
    app.subshell.prototype.data.account.QuarantineOptInResult.Sent,
    app.subshell.prototype.data.account.QuarantineOptInResult.OutcomeUnknown,
    -> null
    app.subshell.prototype.data.account.QuarantineOptInResult.MissingPermission -> ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = "Couldn't open $communityLabel",
        message = "Reddit asks for permission to manage your communities before showing a " +
            "quarantined one, and this account's sign-in doesn't include it.",
    )
    is app.subshell.prototype.data.account.QuarantineOptInResult.Refused -> ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = "Couldn't open $communityLabel",
        // Reddit's source refuses an account without a verified email with 403.
        message = if (result.httpStatusCode == 403) {
            "Reddit shows quarantined communities only to accounts with a verified email address."
        } else {
            "Reddit didn't accept the request to view this quarantined community."
        },
    )
}
