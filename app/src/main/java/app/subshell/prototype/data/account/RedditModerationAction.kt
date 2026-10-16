package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

enum class RedditModeratorAction {
    APPROVE,
    REMOVE,
    // MODERATION02A1_EXPLICIT_SPAM_ACTION_V1
    SPAM,
    // MODERATION02A2_REPORT_CONTROL_ACTIONS_V1
    IGNORE_REPORTS,
    UNIGNORE_REPORTS,
    // MODERATION02A3_CONTENT_CONTROL_ACTIONS_V1
    LOCK,
    UNLOCK,
    DISTINGUISH,
    UNDISTINGUISH,
}

enum class RedditModeratorActionPhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    FAILED,
    SUCCEEDED,
}

enum class RedditModeratorActionFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    BROWSER_LAUNCH,
    CALLBACK,
    AUTHORIZATION,
    SESSION,
    VALIDATION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    ALREADY_MUTATING,
    OUTCOME_UNKNOWN,
}

// MOD01A2_APPROVE_REMOVE_REQUEST_V1
data class RedditModeratorActionRequest(
    val accountStableId: String,
    val communityName: String,
    val thingFullname: String,
    val action: RedditModeratorAction,
    val removalReasonId: String? = null,
    val modNote: String? = null,
    // MODRESPONSE01A1_REMOVAL_MESSAGE_MODEL_V1: explicit, never implied by a reason.
    val removalMessage: RedditRemovalMessage? = null,
) {
    init {
        require(accountStableId.isNotBlank())
        require(normalizeRedditCommunityName(communityName) == communityName)
        require(thingFullname.matches(Regex("t[13]_[a-z0-9]+")))
        require(action == RedditModeratorAction.REMOVE || removalReasonId == null)
        require(action == RedditModeratorAction.REMOVE || modNote == null)
        require(action == RedditModeratorAction.REMOVE || removalMessage == null)
        require(removalReasonId == null || removalReasonId.isNotBlank())
        require(modNote == null || modNote.length <= 100)
    }
}

data class RedditModeratorActionUiState(
    val phase: RedditModeratorActionPhase = RedditModeratorActionPhase.IDLE,
    val request: RedditModeratorActionRequest? = null,
    val failureKind: RedditModeratorActionFailureKind? = null,
    val retryable: Boolean = false,
    val httpStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val completionRevision: Long = 0L,
    val removalReasonWarning: Boolean = false,
    // MODRESPONSE01A1_REMOVAL_MESSAGE_MODEL_V1: the response write is reported
    // apart from the removal so a failed message never hides as success.
    val removalMessageOutcome: RedditRemovalStepOutcome = RedditRemovalStepOutcome.NOT_REQUESTED,
) {
    // ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1: waiting for the browser
    // (AWAITING_CALLBACK) has no timeout, and with the plain ACTION_VIEW
    // fallback no result ever comes back. It no longer counts as in progress,
    // so moderator actions stay available; a new one drops the waiting
    // authorization (ModerationActionCoordinator.dropWaitingAuthorization).
    val inProgress: Boolean
        get() = phase in setOf(
            RedditModeratorActionPhase.AUTHORIZING,
            RedditModeratorActionPhase.SENDING,
        )

    fun inProgressFor(fullname: String): Boolean =
        inProgress && request?.thingFullname == fullname
}

// MODERATION02A3_VERIFIED_CONTENT_CONTROL_POLICY_V1
internal fun RedditModeratorAction.isContentControl(): Boolean =
    this == RedditModeratorAction.LOCK ||
        this == RedditModeratorAction.UNLOCK ||
        this == RedditModeratorAction.DISTINGUISH ||
        this == RedditModeratorAction.UNDISTINGUISH

internal fun RedditModeratorAction.removesFromModerationQueue(): Boolean =
    this == RedditModeratorAction.APPROVE ||
        this == RedditModeratorAction.REMOVE ||
        this == RedditModeratorAction.SPAM

internal fun RedditModeratorActionRequest.normalized(): RedditModeratorActionRequest = copy(
    accountStableId = accountStableId.trim(),
    communityName = normalizeRedditCommunityName(communityName),
    thingFullname = thingFullname.trim().lowercase(),
    removalReasonId = removalReasonId?.trim()?.takeIf(String::isNotEmpty),
    modNote = modNote?.trim()?.takeIf(String::isNotEmpty)?.take(100),
    removalMessage = removalMessage?.let { message ->
        message.copy(body = message.body.trim(), title = message.title.trim())
    },
)

// MODERATION02A1_ACCOUNT_AND_TARGET_PINNED_SPAM_CONFIRMATION_V1
internal fun moderatorSpamRequestOrNull(
    accountStableId: String,
    item: RedditModeratorQueueItem,
): RedditModeratorActionRequest? {
    val prefix = when (item.kind) {
        RedditModeratorQueueKind.POST -> "t3_"
        RedditModeratorQueueKind.COMMENT -> "t1_"
    }
    if (!item.fullname.startsWith(prefix)) return null
    return try {
        RedditModeratorActionRequest(
            accountStableId = accountStableId.trim(),
            communityName = item.communityName,
            thingFullname = item.fullname,
            action = RedditModeratorAction.SPAM,
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun canConfirmModeratorSpam(
    request: RedditModeratorActionRequest,
    activeAccountStableId: String,
    item: RedditModeratorQueueItem,
    actionInProgress: Boolean,
): Boolean =
    !actionInProgress &&
        request.action == RedditModeratorAction.SPAM &&
        request == moderatorSpamRequestOrNull(activeAccountStableId, item)
