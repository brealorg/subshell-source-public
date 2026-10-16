package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.CommunityRule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import app.subshell.prototype.model.CommunitySidebarInfo

// COMMUNITYINFO01A1_TYPED_COMMUNITY_CONTEXT_TRANSPORT_V1
internal enum class RedditCommunityInfoResource {
    SIDEBAR,
    RULES,
    MODERATORS,
}

internal interface RedditCommunityInfoSource {
    suspend fun sidebar(communityName: String): NetworkResult<String?>

    // REPORT01A1_REUSE_COMMUNITYINFO_ABOUT_READ_V1
    suspend fun sidebarInfo(communityName: String): NetworkResult<CommunitySidebarInfo> =
        sidebar(communityName).map { markdown -> CommunitySidebarInfo(markdown = markdown) }

    suspend fun rules(communityName: String): NetworkResult<List<CommunityRule>>

    suspend fun moderators(communityName: String): NetworkResult<List<CommunityModerator>>
}

internal class RedditCommunityInfoNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditCommunityInfoSource {
    override suspend fun sidebar(communityName: String): NetworkResult<String?> =
        sidebarInfo(communityName).map(CommunitySidebarInfo::markdown)

    override suspend fun sidebarInfo(
        communityName: String,
    ): NetworkResult<CommunitySidebarInfo> =
        httpClient.getJson(
            request = redditCommunityInfoJsonRequest(
                communityName = communityName,
                resource = RedditCommunityInfoResource.SIDEBAR,
            ),
            deserializer = RedditCommunitySidebarDto.serializer(),
        ).map(RedditCommunitySidebarDto::toSidebarInfo)

    override suspend fun rules(communityName: String): NetworkResult<List<CommunityRule>> =
        httpClient.getJson(
            request = redditCommunityInfoJsonRequest(
                communityName = communityName,
                resource = RedditCommunityInfoResource.RULES,
            ),
            deserializer = RedditCommunityRulesDto.serializer(),
        ).map(RedditCommunityRulesDto::toCommunityRules)

    override suspend fun moderators(
        communityName: String,
    ): NetworkResult<List<CommunityModerator>> =
        httpClient.getJson(
            request = redditCommunityInfoJsonRequest(
                communityName = communityName,
                resource = RedditCommunityInfoResource.MODERATORS,
            ),
            deserializer = RedditCommunityModeratorsDto.serializer(),
        ).map(RedditCommunityModeratorsDto::toCommunityModerators)
}

internal fun redditCommunityInfoJsonRequest(
    communityName: String,
    resource: RedditCommunityInfoResource,
): JsonGetRequest {
    val normalized = normalizeRedditCommunityName(communityName)
    val pathSegments = when (resource) {
        RedditCommunityInfoResource.SIDEBAR ->
            listOf("r", normalized, "about")
        RedditCommunityInfoResource.RULES ->
            listOf("r", normalized, "about", "rules")
        RedditCommunityInfoResource.MODERATORS ->
            listOf("r", normalized, "about", "moderators")
    }
    return JsonGetRequest(
        pathSegments = pathSegments,
        queryParameters = listOf("raw_json" to "1"),
    )
}

@Serializable
internal data class RedditCommunitySidebarDto(
    val kind: String? = null,
    val data: RedditCommunitySidebarDataDto = RedditCommunitySidebarDataDto(),
)

@Serializable
internal data class RedditCommunitySidebarDataDto(
    val description: String? = null,
    @SerialName("free_form_reports") val freeFormReports: Boolean? = null,
)

@Serializable
internal data class RedditCommunityRulesDto(
    val rules: List<RedditCommunityRuleDto?> = emptyList(),
)

@Serializable
internal data class RedditCommunityRuleDto(
    val kind: String? = null,
    val description: String? = null,
    @SerialName("short_name") val shortName: String? = null,
    val priority: Int? = null,
    @SerialName("violation_reason") val violationReason: String? = null,
)

@Serializable
internal data class RedditCommunityModeratorsDto(
    val kind: String? = null,
    val data: RedditCommunityModeratorsDataDto = RedditCommunityModeratorsDataDto(),
)

@Serializable
internal data class RedditCommunityModeratorsDataDto(
    val children: List<RedditCommunityModeratorDto?> = emptyList(),
)

@Serializable
internal data class RedditCommunityModeratorDto(
    val name: String? = null,
    @SerialName("mod_permissions") val modPermissions: List<String?> = emptyList(),
)

internal fun RedditCommunitySidebarDto.toSidebarMarkdown(): String? =
    data.description
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(MAX_COMMUNITY_SIDEBAR_CHARS)

internal fun RedditCommunitySidebarDto.toSidebarInfo(): CommunitySidebarInfo =
    CommunitySidebarInfo(
        markdown = toSidebarMarkdown(),
        freeFormReports = data.freeFormReports,
    )

internal fun RedditCommunityRulesDto.toCommunityRules(): List<CommunityRule> =
    rules.asSequence()
        .mapNotNull { rule ->
            val shortName = rule
                ?.shortName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.take(MAX_COMMUNITY_RULE_NAME_CHARS)
                ?: return@mapNotNull null
            CommunityRule(
                shortName = shortName,
                description = rule.description
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.take(MAX_COMMUNITY_RULE_DESCRIPTION_CHARS),
                kind = rule.kind
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf(String::isNotEmpty)
                    ?.take(MAX_COMMUNITY_RULE_KIND_CHARS),
                priority = rule.priority
                    ?.coerceIn(MIN_COMMUNITY_RULE_PRIORITY, MAX_COMMUNITY_RULE_PRIORITY)
                    ?: 0,
                violationReason = rule.violationReason
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.take(MAX_COMMUNITY_RULE_VIOLATION_REASON_CHARS),
            )
        }
        .sortedWith(
            compareBy<CommunityRule>(CommunityRule::priority)
                .thenBy(String.CASE_INSENSITIVE_ORDER, CommunityRule::shortName),
        )
        .distinctBy { rule -> rule.shortName.lowercase() }
        .take(MAX_COMMUNITY_RULES)
        .toList()

internal fun RedditCommunityModeratorsDto.toCommunityModerators(): List<CommunityModerator> =
    data.children.asSequence()
        .mapNotNull { moderator ->
            val username = moderator
                ?.name
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.take(MAX_REDDIT_USERNAME_CHARS)
                ?: return@mapNotNull null
            CommunityModerator(
                username = username,
                permissions = moderator.modPermissions.asSequence()
                    .mapNotNull { permission ->
                        permission
                            ?.trim()
                            ?.lowercase()
                            ?.takeIf(String::isNotEmpty)
                            ?.take(MAX_MOD_PERMISSION_CHARS)
                    }
                    .distinct()
                    .sorted()
                    .take(MAX_MOD_PERMISSIONS_PER_USER)
                    .toList(),
            )
        }
        .distinctBy { moderator -> moderator.username.lowercase() }
        .take(MAX_COMMUNITY_MODERATORS)
        .toList()

private const val MAX_COMMUNITY_SIDEBAR_CHARS = 100_000
private const val MAX_COMMUNITY_RULES = 100
private const val MAX_COMMUNITY_RULE_NAME_CHARS = 512
private const val MAX_COMMUNITY_RULE_DESCRIPTION_CHARS = 10_000
private const val MAX_COMMUNITY_RULE_KIND_CHARS = 32
private const val MAX_COMMUNITY_RULE_VIOLATION_REASON_CHARS = 100
private const val MIN_COMMUNITY_RULE_PRIORITY = 0
private const val MAX_COMMUNITY_RULE_PRIORITY = 10_000
private const val MAX_COMMUNITY_MODERATORS = 500
private const val MAX_REDDIT_USERNAME_CHARS = 100
private const val MAX_MOD_PERMISSIONS_PER_USER = 64
private const val MAX_MOD_PERMISSION_CHARS = 64
