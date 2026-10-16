package app.subshell.prototype.model

import androidx.compose.runtime.Immutable

// COMMUNITYINFO01A1_TYPED_COMMUNITY_CONTEXT_DOMAIN_V1
@Immutable
data class CommunityInfoSnapshot(
    val communityName: String,
    val sidebarMarkdown: String? = null,
    // REPORT01A1_REDDIT_REPORT_POLICY_CONTEXT_V1
    val freeFormReports: Boolean? = null,
    val rules: List<CommunityRule> = emptyList(),
    val moderators: List<CommunityModerator> = emptyList(),
)

@Immutable
data class CommunitySidebarInfo(
    val markdown: String? = null,
    val freeFormReports: Boolean? = null,
)

@Immutable
data class CommunityRule(
    val shortName: String,
    val description: String? = null,
    val kind: String? = null,
    val priority: Int = 0,
    val violationReason: String? = null,
)

@Immutable
data class CommunityModerator(
    val username: String,
    val permissions: List<String> = emptyList(),
)
