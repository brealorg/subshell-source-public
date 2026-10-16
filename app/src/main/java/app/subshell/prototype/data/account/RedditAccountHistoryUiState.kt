package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.model.ProfileCommentFixture

enum class RedditAccountHistoryUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    STORAGE,
    END_REACHED,
}

data class RedditAccountHistoryUiState(
    val accountStableId: String? = null,
    val username: String? = null,
    val kind: RedditAccountHistoryKind? = null,
    val queryKey: RepositoryQueryKey? = null,
    val comments: List<ProfileCommentFixture> = emptyList(),
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
        require(accountStableId == null || accountStableId.isNotBlank()) {
            "Reddit account history account stable ID cannot be blank"
        }
        require(username == null || username.isNotBlank()) {
            "Reddit account history username cannot be blank"
        }
        require(
            listOf(accountStableId, username, kind, queryKey).all { it == null } ||
                listOf(accountStableId, username, kind, queryKey).none { it == null },
        ) {
            "Reddit account history identity must be either empty or complete"
        }
        require(contentRevision >= 0L) {
            "Reddit account history content revision cannot be negative"
        }
    }

    val refreshing: Boolean
        get() = inProgressOperation == RepositoryLoadOperation.REFRESH

    val appending: Boolean
        get() = inProgressOperation == RepositoryLoadOperation.APPEND

    val inProgress: Boolean
        get() = inProgressOperation != null

    fun matches(
        accountStableId: String,
        username: String,
        kind: RedditAccountHistoryKind,
    ): Boolean =
        this.accountStableId == accountStableId &&
            this.username == runCatching { normalizeRedditUsername(username) }.getOrNull() &&
            this.kind == kind

    fun matchesUser(
        accountStableId: String,
        username: String,
    ): Boolean =
        this.accountStableId == accountStableId &&
            this.username == runCatching { normalizeRedditUsername(username) }.getOrNull()
}
