package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.data.error.FailureDetails

internal enum class AuthenticatedRedditModmailFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

internal sealed interface AuthenticatedRedditModmailResult {
    data class Loaded(
        val mailCommunities: Set<String>,
        val conversations: List<RedditModmailConversation>,
        val nextCursor: String?,
        // CORRECT01_MODMAIL_UNREAD_COUNTS_OPTIONAL_V1 (OCB-509): null when the
        // counts could not be read; the list still loads and keeps the last counts.
        val unreadCounts: RedditModmailUnreadCounts? = RedditModmailUnreadCounts(),
    ) : AuthenticatedRedditModmailResult

    data object AuthorizationRequired : AuthenticatedRedditModmailResult

    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditModmailResult
}

internal sealed interface AuthenticatedRedditModmailThreadResult {
    data class Loaded(
        val thread: RedditModmailThread,
    ) : AuthenticatedRedditModmailThreadResult

    data object AuthorizationRequired : AuthenticatedRedditModmailThreadResult

    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditModmailThreadResult
}

internal sealed interface AuthenticatedRedditModmailUnreadResult {
    data class Loaded(
        val conversations: List<RedditModmailConversation>,
    ) : AuthenticatedRedditModmailUnreadResult

    data object AuthorizationRequired : AuthenticatedRedditModmailUnreadResult

    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditModmailUnreadResult
}

internal sealed interface AuthenticatedRedditModmailUnreadCountsResult {
    data class Loaded(
        val counts: RedditModmailUnreadCounts,
    ) : AuthenticatedRedditModmailUnreadCountsResult

    data object AuthorizationRequired : AuthenticatedRedditModmailUnreadCountsResult

    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val details: FailureDetails? = null,
    ) : AuthenticatedRedditModmailUnreadCountsResult
}

internal interface AuthenticatedRedditModmailLoader {
    suspend fun load(
        accountStableId: String,
        after: String? = null,
    ): AuthenticatedRedditModmailResult

    suspend fun loadByMode(
        accountStableId: String,
        after: String? = null,
        listMode: RedditModmailListMode = RedditModmailListMode.ACTIVE,
    ): AuthenticatedRedditModmailResult = load(
        accountStableId = accountStableId,
        after = after,
    )

    suspend fun loadUnread(
        accountStableId: String,
    ): AuthenticatedRedditModmailUnreadResult

    suspend fun loadUnreadCounts(
        accountStableId: String,
    ): AuthenticatedRedditModmailUnreadCountsResult

    suspend fun loadThread(
        accountStableId: String,
        conversationId: String,
    ): AuthenticatedRedditModmailThreadResult
}
