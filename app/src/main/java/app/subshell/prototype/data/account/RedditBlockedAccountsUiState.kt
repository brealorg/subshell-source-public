package app.subshell.prototype.data.account

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.error.FailureDetails

// USERACTION01A2_AUTHORITATIVE_BLOCKED_ACCOUNTS_DOMAIN_V1
data class RedditBlockedAccount(
    val username: String,
    val accountFullname: String,
    val relationshipId: String? = null,
    val blockedAtEpochSeconds: Long? = null,
)

data class RedditBlockedAccountsUiState(
    val accountStableId: String? = null,
    val items: List<RedditBlockedAccount> = emptyList(),
    val loaded: Boolean = false,
    val inProgress: Boolean = false,
    val inProgressOperation: RepositoryLoadOperation? = null,
    val nextCursor: String? = null,
    val failureKind: ContentSafetyFailureKind? = null,
    val retryable: Boolean = false,
    val statusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    // OCB414: the list read waits for the browser authorization it started.
    val authorizing: Boolean = false,
) {
    fun matches(accountStableId: String): Boolean =
        this.accountStableId == accountStableId.trim()
}
