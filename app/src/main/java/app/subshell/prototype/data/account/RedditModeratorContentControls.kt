package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails

// MODERATION02A3_TYPED_LOCK_DISTINGUISH_STATE_V1
data class RedditModeratorContentState(
    val locked: Boolean? = null,
    val distinguished: Boolean? = null,
    val canModerate: Boolean? = null,
)

enum class ModeratorContentControlsNotice {
    NOT_FOUND,
    READ_FAILED,
    NO_PERMISSION,
    STATE_UNKNOWN,
    STATE_CHANGED,
    BUSY,
    AUTHORIZATION_REQUIRED,
    WRITE_FAILED,
    VERIFIED,
    VERIFIED_AFTER_UNKNOWN,
    NOT_CONFIRMED,
    ACCOUNT_CHANGED,
}

data class ModeratorContentControlsUiState(
    val accountStableId: String? = null,
    val visible: Boolean = false,
    val target: ModeratorReportTarget? = null,
    val item: RedditModeratorQueueItem? = null,
    val loading: Boolean = false,
    val mutating: Boolean = false,
    val revision: Long = 0L,
    val notice: ModeratorContentControlsNotice? = null,
    // DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1: what Reddit answered for a failed read or write.
    val failureDetails: FailureDetails? = null,
) {
    val busy: Boolean
        get() = loading || mutating
}

data class ModeratorContentControlConfirmation(
    val accountStableId: String,
    val actorUsername: String,
    val target: ModeratorReportTarget,
    val action: RedditModeratorAction,
    val revision: Long,
)

internal fun RedditModeratorAction.desiredLockedState(): Boolean? = when (this) {
    RedditModeratorAction.LOCK -> true
    RedditModeratorAction.UNLOCK -> false
    else -> null
}

internal fun RedditModeratorAction.desiredDistinguishedState(): Boolean? = when (this) {
    RedditModeratorAction.DISTINGUISH -> true
    RedditModeratorAction.UNDISTINGUISH -> false
    else -> null
}

internal fun ModeratorContentControlConfirmation.desiredStateMatches(
    item: RedditModeratorQueueItem,
): Boolean = when {
    action.desiredLockedState() != null ->
        item.contentState.locked == action.desiredLockedState()
    action.desiredDistinguishedState() != null ->
        item.contentState.distinguished == action.desiredDistinguishedState()
    else -> false
}

internal fun contentControlConfirmationOrNull(
    state: ModeratorContentControlsUiState,
    activeAccountStableId: String,
    activeUsername: String,
    action: RedditModeratorAction,
): ModeratorContentControlConfirmation? {
    if (!action.isContentControl()) return null
    if (
        !state.visible ||
        state.busy ||
        state.accountStableId != activeAccountStableId
    ) {
        return null
    }
    val item = state.item ?: return null
    val target = state.target ?: return null
    if (
        item.fullname != target.fullname ||
        target.communityName == null ||
        target.communityName != item.communityName ||
        item.reportDetails.canModerate != true ||
        item.contentState.canModerate != true
    ) {
        return null
    }
    val actor = activeUsername.trim().removePrefix("u/")
    if (actor.isBlank()) return null
    when (action) {
        RedditModeratorAction.LOCK ->
            if (item.contentState.locked != false) return null
        RedditModeratorAction.UNLOCK ->
            if (item.contentState.locked != true) return null
        RedditModeratorAction.DISTINGUISH -> {
            if (item.contentState.distinguished != false) return null
            if (!item.author.equals(actor, ignoreCase = true)) return null
        }
        RedditModeratorAction.UNDISTINGUISH -> {
            if (item.contentState.distinguished != true) return null
            if (!item.author.equals(actor, ignoreCase = true)) return null
        }
        else -> return null
    }
    return ModeratorContentControlConfirmation(
        accountStableId = activeAccountStableId,
        actorUsername = actor,
        target = target,
        action = action,
        revision = state.revision,
    )
}
