package app.subshell.prototype.data.account

import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.data.error.FailureDetails

enum class CommunityMembershipAction {
    JOIN,
    LEAVE,
}

enum class CommunityMembershipPhase {
    IDLE,
    SENDING,
    AUTHORIZATION_REQUIRED,
    AUTHORIZING,
    AWAITING_CALLBACK,
    SUCCEEDED,
    FAILED,
}

enum class CommunityMembershipFailureKind {
    VALIDATION,
    ACCOUNT_CHANGED,
    CONFIGURATION,
    AUTHORIZATION,
    CALLBACK,
    BROWSER_LAUNCH,
    SESSION,
    HTTP,
    PROTOCOL,
    OUTCOME_UNKNOWN,
    NOT_CONFIRMED,
}

internal data class CommunityMembershipRequest(
    val accountStableId: String,
    val communityName: String,
    val action: CommunityMembershipAction,
)

data class CommunityMembershipUiState(
    val phase: CommunityMembershipPhase = CommunityMembershipPhase.IDLE,
    val accountStableId: String? = null,
    val communityName: String? = null,
    val action: CommunityMembershipAction? = null,
    val failureKind: CommunityMembershipFailureKind? = null,
    val failureRetryable: Boolean = false,
    val failureStatusCode: Int? = null,
    val failureDetails: FailureDetails? = null,
    val message: String? = null,
    val completionRevision: Long = 0L,
) {
    init {
        require(completionRevision >= 0L) {
            "Community membership completion revision cannot be negative"
        }
    }

    val inProgress: Boolean
        get() = phase in setOf(
            CommunityMembershipPhase.SENDING,
            CommunityMembershipPhase.AUTHORIZING,
            CommunityMembershipPhase.AWAITING_CALLBACK,
        )

    // LOW_DATA_MEMBERSHIP_MATCH_NEEDS_A_NAME_V1 (OCB-601): an unnamed state
    // (a failure for a blank name) matches no community, instead of every
    // community whose name does not normalize (null == null).
    fun matches(
        accountStableId: String,
        communityName: String,
    ): Boolean {
        val own = this.communityName ?: return false
        val asked = normalizeCommunityMembershipNameOrNull(communityName) ?: return false
        return this.accountStableId == accountStableId.trim() && own.equals(asked, ignoreCase = true)
    }
}

internal fun CommunityMembershipRequest.normalizedCommunityMembershipRequest():
    CommunityMembershipRequest {
    val account = accountStableId.trim()
    require(account.isNotEmpty()) {
        "Community membership account cannot be blank"
    }
    require(account.length <= COMMUNITY_MEMBERSHIP_ACCOUNT_MAX_CHARS) {
        "Community membership account is too long"
    }
    return copy(
        accountStableId = account,
        communityName = normalizeCommunityMembershipName(communityName),
    )
}

internal fun normalizeCommunityMembershipName(value: String): String =
    normalizeRedditCommunityName(value)

private fun normalizeCommunityMembershipNameOrNull(value: String): String? =
    runCatching { normalizeCommunityMembershipName(value) }.getOrNull()

private const val COMMUNITY_MEMBERSHIP_ACCOUNT_MAX_CHARS = 256
