package app.subshell.prototype.data.account

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditUsername

data class RedditSavedHistoryUiState(
    val accountStableId: String? = null,
    val username: String? = null,
    val queryKey: RepositoryQueryKey? = null,
    val items: List<RedditSavedHistoryUiItem> = emptyList(),
    val nextCursor: String? = null,
    val fromCache: Boolean = false,
    val loaded: Boolean = false,
    val inProgressOperation: RepositoryLoadOperation? = null,
    val failedOperation: RepositoryLoadOperation? = null,
    val failureKind: RedditAccountHistoryUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val contentRevision: Long = 0L,
) {
    init {
        require(
            listOf(accountStableId, username, queryKey).all { it == null } ||
                listOf(accountStableId, username, queryKey).none { it == null },
        ) {
            "Reddit saved history identity must be either empty or complete"
        }
        require(contentRevision >= 0L) {
            "Reddit saved history content revision cannot be negative"
        }
    }

    val inProgress: Boolean
        get() = inProgressOperation != null

    fun matches(
        accountStableId: String,
        username: String,
    ): Boolean =
        this.accountStableId == accountStableId.trim() &&
            this.username == runCatching {
                normalizeRedditUsername(username)
            }.getOrNull()
}
