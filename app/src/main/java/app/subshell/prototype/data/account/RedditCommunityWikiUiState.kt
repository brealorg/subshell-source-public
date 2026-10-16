package app.subshell.prototype.data.account

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.CommunityWikiIndex
import app.subshell.prototype.model.CommunityWikiPage
import app.subshell.prototype.model.normalizeCommunityWikiPagePath

// WIKI01A2_COMMUNITY_WIKI_UI_STATE_V1
enum class RedditCommunityWikiUiFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
    UNAVAILABLE,
}

data class RedditCommunityWikiUiState(
    val accountStableId: String? = null,
    val communityName: String? = null,
    val index: CommunityWikiIndex? = null,
    val page: CommunityWikiPage? = null,
    val loading: Boolean = false,
    val loadingRequestKey: String? = null,
    val authorizing: Boolean = false,
    val authorizationRequired: Boolean = false,
    val failureKind: RedditCommunityWikiUiFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureDetails: FailureDetails? = null,
    val failureHttpStatusCode: Int? = null,
    val contentRevision: Long = 0L,
) {
    fun matches(
        accountStableId: String,
        rawCommunityName: String,
    ): Boolean {
        val normalizedCommunity = runCatching {
            normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return false
        return this.accountStableId == accountStableId.trim() &&
            communityName == normalizedCommunity
    }

    fun pageMatches(rawPagePath: String): Boolean {
        val normalizedPath = runCatching {
            normalizeCommunityWikiPagePath(rawPagePath)
        }.getOrNull() ?: return false
        return page?.path == normalizedPath
    }
}
