package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails

enum class ContentSafetyOperation {
    SET_POST_HIDDEN,
    REPORT_CONTENT,
    SET_USER_BLOCKED,
}

enum class ContentSafetyPhase {
    IDLE,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SENDING,
    FAILED,
    SUCCEEDED,
}

enum class ContentSafetyFailureKind {
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
    API,
    PROVIDER_RESTRICTION,
    OUTCOME_UNKNOWN,
}

enum class HiddenHistoryReconciliation {
    NOT_APPLICABLE,
    REFRESHED,
    AUTHORIZATION_REQUIRED,
    FAILED,
    UNAVAILABLE,
}

data class ContentSafetyUiState(
    val phase: ContentSafetyPhase = ContentSafetyPhase.IDLE,
    val operation: ContentSafetyOperation? = null,
    val accountStableId: String? = null,
    val thingFullname: String? = null,
    val username: String? = null,
    val hidden: Boolean? = null,
    val blocked: Boolean? = null,
    val failureKind: ContentSafetyFailureKind? = null,
    val retryable: Boolean = false,
    val statusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val hiddenHistoryReconciliation: HiddenHistoryReconciliation =
        HiddenHistoryReconciliation.NOT_APPLICABLE,
    val blockedAccounts: RedditBlockedAccountsUiState = RedditBlockedAccountsUiState(),
    val completionRevision: Long = 0L,
) {
    val inProgress: Boolean
        get() = phase in setOf(
            ContentSafetyPhase.AUTHORIZING,
            ContentSafetyPhase.AWAITING_CALLBACK,
            ContentSafetyPhase.SENDING,
        )
}

// SAFETY01A2_TRANSIENT_REPORT_REASON_AND_AUTHORITATIVE_HIDDEN_RECONCILIATION_V1
internal sealed interface ContentSafetyPendingMutation {
    val accountStableId: String
    val thingFullname: String
    val username: String?
    val operation: ContentSafetyOperation

    data class PostVisibility(
        val request: RedditPostVisibilityRequest,
    ) : ContentSafetyPendingMutation {
        override val accountStableId: String
            get() = request.accountStableId
        override val thingFullname: String
            get() = request.postFullname
        override val username: String?
            get() = null
        override val operation: ContentSafetyOperation
            get() = ContentSafetyOperation.SET_POST_HIDDEN
    }

    data class Report(
        val request: RedditContentReportRequest,
    ) : ContentSafetyPendingMutation {
        override val accountStableId: String
            get() = request.accountStableId
        override val thingFullname: String
            get() = request.thingFullname
        override val username: String?
            get() = null
        override val operation: ContentSafetyOperation
            get() = ContentSafetyOperation.REPORT_CONTENT
    }

    // USERACTION01A1_TRANSIENT_USER_BLOCK_MUTATION_V1
    data class UserBlock(
        val request: RedditUserBlockRequest,
    ) : ContentSafetyPendingMutation {
        override val accountStableId: String
            get() = request.accountStableId
        override val thingFullname: String
            get() = "u/${request.username}"
        override val username: String
            get() = request.username
        override val operation: ContentSafetyOperation
            get() = ContentSafetyOperation.SET_USER_BLOCKED
    }
}

internal fun ContentSafetyPendingMutation.toUiState(
    phase: ContentSafetyPhase,
    previous: ContentSafetyUiState,
    failureKind: ContentSafetyFailureKind? = null,
    retryable: Boolean = false,
    statusCode: Int? = null,
    failureDetails: FailureDetails? = null,
    hiddenHistoryReconciliation: HiddenHistoryReconciliation =
        HiddenHistoryReconciliation.NOT_APPLICABLE,
): ContentSafetyUiState = ContentSafetyUiState(
    phase = phase,
    operation = operation,
    accountStableId = accountStableId,
    thingFullname = thingFullname,
    username = username,
    hidden = (this as? ContentSafetyPendingMutation.PostVisibility)?.request?.hidden,
    blocked = (this as? ContentSafetyPendingMutation.UserBlock)?.request?.blocked,
    failureKind = failureKind,
    retryable = retryable,
    statusCode = statusCode,
    failureDetails = failureDetails,
    hiddenHistoryReconciliation = hiddenHistoryReconciliation,
    blockedAccounts = previous.blockedAccounts,
    completionRevision = previous.completionRevision,
)
