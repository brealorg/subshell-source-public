package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.CommunityInfoSnapshot

// COMMUNITYINFO01A2_LATEST_REQUEST_UI_STATE_V1
enum class RedditCommunityInfoUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

enum class RedditCommunityInfoSection {
    SIDEBAR,
    RULES,
    MODERATORS,
}

data class RedditCommunityInfoUiState(
    val accountStableId: String? = null,
    val communityName: String? = null,
    val snapshot: CommunityInfoSnapshot? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val unavailableSections: Set<RedditCommunityInfoSection> = emptySet(),
    val failureKind: RedditCommunityInfoUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val failureHttpStatusCode: Int? = null,
    val contentRevision: Long = 0L,
) {
    init {
        require(accountStableId == null || accountStableId.isNotBlank())
        require(communityName == null || communityName.isNotBlank())
        require((accountStableId == null) == (communityName == null))
        require(failureHttpStatusCode == null || failureHttpStatusCode in 100..599)
        require(contentRevision >= 0L)
    }

    fun matches(accountStableId: String, communityName: String): Boolean =
        this.accountStableId == accountStableId &&
            this.communityName.equals(communityName, ignoreCase = true)
}
