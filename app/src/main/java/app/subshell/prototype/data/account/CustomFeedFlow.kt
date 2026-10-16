package app.subshell.prototype.data.account

import app.subshell.prototype.model.Post
import app.subshell.prototype.data.error.FailureDetails

data class CustomFeedSummary(
    val path: String,
    val displayName: String,
    val description: String,
    val visibility: String,
    val subredditNames: List<String>,
    // REDDITFAV04_CUSTOM_FEED_FAVORITES_V1: Reddit's is_favorited for the
    // account's own feed (device-verified 2026-10-05).
    val redditFavorite: Boolean = false,
)

enum class CustomFeedVisibility(
    val apiValue: String,
    val displayLabel: String,
) {
    PRIVATE("private", "Private"),
    PUBLIC("public", "Public"),
    HIDDEN("hidden", "Hidden"),
}

data class CustomFeedCreateRequest(
    val accountStableId: String,
    val displayName: String,
    val subredditNames: List<String>,
    val visibility: CustomFeedVisibility = CustomFeedVisibility.PRIVATE,
)

data class CustomFeedUpdateRequest(
    val accountStableId: String,
    val path: String,
    val displayName: String,
    val subredditNames: List<String>,
    val visibility: CustomFeedVisibility,
)

data class CustomFeedDeleteRequest(
    val accountStableId: String,
    val path: String,
    val displayName: String,
)

enum class CustomFeedManagePhase {
    IDLE,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SAVING,
    DELETING,
    SUCCEEDED,
    FAILED,
}

enum class CustomFeedManageFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    AUTHORIZATION,
    CALLBACK,
    SESSION,
    HTTP,
    OUTCOME_UNKNOWN,
    PROTOCOL,
    VALIDATION,
}

enum class CustomFeedPostsFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

data class CustomFeedPostsUiState(
    val accountStableId: String = "",
    val feedPath: String = "",
    val posts: List<Post> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val appending: Boolean = false,
    val failureKind: CustomFeedPostsFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureStatusCode: Int? = null,
    // FEEDS01_CUSTOM_FEED_APPEND_FAILURE_V1: true when the failure belongs to
    // an append, so a retry continues from the same cursor instead of reloading.
    val failedAppend: Boolean = false,
    val contentRevision: Long = 0L,
) {
    fun matches(accountStableId: String, feedPath: String): Boolean =
        this.accountStableId == accountStableId && this.feedPath == feedPath
}

internal fun customFeedCountLabel(count: Int): String =
    "$count ${if (count == 1) "feed" else "feeds"}"

internal fun customFeedPathSegments(rawPath: String): List<String>? {
    val segments = rawPath.trim().trim('/').split('/').filter(String::isNotBlank)
    if (segments.size != 4) return null
    if (!segments[0].equals("user", ignoreCase = true)) return null
    if (!segments[2].equals("m", ignoreCase = true)) return null
    val owner = segments[1]
    val slug = segments[3]
    fun valid(segment: String): Boolean =
        segment.length in 1..100 && segment.all { character ->
            character.isLetterOrDigit() || character == '_' || character == '-'
        }
    if (!valid(owner) || !valid(slug)) return null
    return listOf("user", owner, "m", slug)
}

internal fun customFeedApiPathSegments(rawPath: String): List<String>? =
    customFeedPathSegments(rawPath)?.let { segments -> listOf("api", "multi") + segments }

enum class CustomFeedFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

enum class CustomFeedCreatePhase {
    IDLE,
    AUTHORIZING,
    AWAITING_CALLBACK,
    CREATING,
    SUCCEEDED,
    FAILED,
}

enum class CustomFeedCreateFailureKind {
    CONFIGURATION,
    ACCOUNT_CHANGED,
    AUTHORIZATION,
    CALLBACK,
    SESSION,
    NAME_CONFLICT,
    HTTP,
    OUTCOME_UNKNOWN,
    PROTOCOL,
    VALIDATION,
}

data class CustomFeedsUiState(
    val accountStableId: String = "",
    val feeds: List<CustomFeedSummary> = emptyList(),
    val loading: Boolean = false,
    val failureKind: CustomFeedFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureStatusCode: Int? = null,
    val contentRevision: Long = 0L,
    val createPhase: CustomFeedCreatePhase = CustomFeedCreatePhase.IDLE,
    val createFailureKind: CustomFeedCreateFailureKind? = null,
    val createMessage: String? = null,
    val createFailureDetails: FailureDetails? = null,
    val createCompletionRevision: Long = 0L,
    val managePhase: CustomFeedManagePhase = CustomFeedManagePhase.IDLE,
    val manageFailureKind: CustomFeedManageFailureKind? = null,
    val manageMessage: String? = null,
    val manageFailureDetails: FailureDetails? = null,
    val manageCompletionRevision: Long = 0L,
) {
    val createInProgress: Boolean
        get() = createPhase in setOf(
            CustomFeedCreatePhase.AUTHORIZING,
            CustomFeedCreatePhase.AWAITING_CALLBACK,
            CustomFeedCreatePhase.CREATING,
        )

    val manageInProgress: Boolean
        get() = managePhase in setOf(
            CustomFeedManagePhase.AUTHORIZING,
            CustomFeedManagePhase.AWAITING_CALLBACK,
            CustomFeedManagePhase.SAVING,
            CustomFeedManagePhase.DELETING,
        )
}
