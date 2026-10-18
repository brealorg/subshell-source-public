package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName

// MODERATION02A4_MOD_LOG_UI_DOMAIN_V1
data class RedditModeratorLogEntry(
    val id: String,
    val communityName: String,
    val moderatorName: String,
    val action: String,
    val targetAuthor: String? = null,
    val targetFullname: String? = null,
    val targetTitle: String? = null,
    val targetPermalink: String? = null,
    val description: String? = null,
    val details: String? = null,
    val createdEpochSeconds: Long = 0L,
)

data class ModeratorLogUiState(
    val visible: Boolean = false,
    val accountStableId: String? = null,
    val communityName: String? = null,
    val entries: List<RedditModeratorLogEntry> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val nextCursor: String? = null,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val failureKind: RedditModerationUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureHttpStatusCode: Int? = null,
    // DIAG05_PLAIN_NOTICES_ARE_RECORDED_V1
    val failureDetails: FailureDetails? = null,
    val contentRevision: Long = 0L,
) {
    fun matches(accountStableId: String, rawCommunityName: String): Boolean {
        val communityName = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return this.accountStableId == accountStableId.trim() &&
            this.communityName == communityName
    }
}
