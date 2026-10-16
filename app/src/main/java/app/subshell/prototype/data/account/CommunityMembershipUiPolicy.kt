package app.subshell.prototype.data.account

internal fun communityMembershipActionForJoined(
    joined: Boolean,
): CommunityMembershipAction =
    if (joined) {
        CommunityMembershipAction.LEAVE
    } else {
        CommunityMembershipAction.JOIN
    }

internal fun CommunityMembershipUiState.projectedJoined(
    accountStableId: String,
    communityName: String,
    authoritativeJoined: Boolean,
): Boolean {
    if (
        phase != CommunityMembershipPhase.SUCCEEDED ||
        !matches(accountStableId, communityName)
    ) {
        return authoritativeJoined
    }

    return when (action) {
        CommunityMembershipAction.JOIN -> true
        CommunityMembershipAction.LEAVE -> false
        null -> authoritativeJoined
    }
}

internal fun CommunityMembershipUiState.inProgressFor(
    accountStableId: String,
    communityName: String,
): Boolean =
    inProgress &&
        matches(accountStableId, communityName)

internal fun CommunityMembershipUiState.blocksActionFor(
    accountStableId: String,
    communityName: String,
): Boolean {
    if (!matches(accountStableId, communityName)) return false

    return inProgress ||
        phase == CommunityMembershipPhase.SUCCEEDED ||
        (
            phase == CommunityMembershipPhase.FAILED &&
                failureKind ==
                CommunityMembershipFailureKind.OUTCOME_UNKNOWN
        )
}

internal fun CommunityMembershipUiState.failureMessageFor(
    accountStableId: String,
    communityName: String,
): String? {
    if (
        phase != CommunityMembershipPhase.FAILED ||
        !matches(accountStableId, communityName)
    ) {
        return null
    }

    message?.takeIf(String::isNotBlank)?.let {
        return it
    }

    return when (failureKind) {
        CommunityMembershipFailureKind.VALIDATION ->
            "This community membership request is invalid."

        CommunityMembershipFailureKind.ACCOUNT_CHANGED ->
            "The active Reddit account changed. Try again."

        CommunityMembershipFailureKind.CONFIGURATION ->
            "Community membership changes are unavailable for this account."

        CommunityMembershipFailureKind.AUTHORIZATION,
        CommunityMembershipFailureKind.CALLBACK,
        CommunityMembershipFailureKind.BROWSER_LAUNCH,
        ->
            "Reddit authorization did not complete."

        CommunityMembershipFailureKind.SESSION ->
            "Your Reddit session needs attention."

        CommunityMembershipFailureKind.HTTP ->
            if (failureStatusCode == 429) {
                "Reddit is rate limiting this action. Try again later."
            } else {
                failureStatusCode?.let { status ->
                    "Reddit rejected the membership change (HTTP $status)."
                } ?: "Reddit rejected the membership change."
            }

        CommunityMembershipFailureKind.PROTOCOL ->
            "Reddit returned an unexpected response."

        CommunityMembershipFailureKind.OUTCOME_UNKNOWN ->
            "Reddit may have received the membership change. " +
                "Refresh before trying again."

        CommunityMembershipFailureKind.NOT_CONFIRMED ->
            NOT_CONFIRMED_MEMBERSHIP_MESSAGE

        null -> null
    }
}

internal const val NOT_CONFIRMED_MEMBERSHIP_MESSAGE =
    "Reddit still doesn't show this change. It may not have gone through. " +
        "Check the community and try again."

internal const val NOT_CHECKED_MEMBERSHIP_MESSAGE =
    "subshell couldn't check with Reddit whether this change went through. " +
        "Refresh the community before trying again."
