package app.subshell.prototype.data.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.CommunityInfoSnapshot

// COMMUNITYINFO01A2_AUTHENTICATED_COMMUNITY_INFO_DOMAIN_V1
internal enum class AuthenticatedRedditCommunityInfoFailureKind {
    CLIENT_CONFIGURATION,
    SESSION,
    HTTP,
    TRANSPORT,
    PROTOCOL,
}

internal enum class AuthenticatedRedditCommunityInfoSection {
    SIDEBAR,
    RULES,
    MODERATORS,
}

@ConsistentCopyVisibility
internal data class AuthenticatedRedditCommunityInfoTarget private constructor(
    val communityName: String,
) {
    init {
        require(communityName.isNotBlank()) {
            "Community info target cannot be blank"
        }
    }

    val requestKey: String
        get() = "community-info:$communityName"

    companion object {
        fun community(rawCommunityName: String): AuthenticatedRedditCommunityInfoTarget =
            AuthenticatedRedditCommunityInfoTarget(
                communityName = normalizeRedditCommunityName(rawCommunityName),
            )
    }
}

internal data class AuthenticatedRedditCommunityInfoSectionFailure(
    val section: AuthenticatedRedditCommunityInfoSection,
    val kind: AuthenticatedRedditCommunityInfoFailureKind,
    val retryable: Boolean,
    val details: FailureDetails? = null,
    val httpStatusCode: Int? = null,
)

internal sealed interface AuthenticatedRedditCommunityInfoResult {
    data class Loaded(
        val snapshot: CommunityInfoSnapshot,
        val sectionFailures: List<AuthenticatedRedditCommunityInfoSectionFailure> = emptyList(),
    ) : AuthenticatedRedditCommunityInfoResult

    data class Failed(
        val kind: AuthenticatedRedditCommunityInfoFailureKind,
        val retryable: Boolean,
        val details: FailureDetails? = null,
        val httpStatusCode: Int? = null,
    ) : AuthenticatedRedditCommunityInfoResult
}

internal interface AuthenticatedRedditCommunityInfoLoader {
    suspend fun load(
        accountStableId: String,
        target: AuthenticatedRedditCommunityInfoTarget,
    ): AuthenticatedRedditCommunityInfoResult
}
