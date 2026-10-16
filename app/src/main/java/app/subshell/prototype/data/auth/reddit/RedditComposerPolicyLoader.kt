package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.needsAuthorization
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostCommunity
import app.subshell.prototype.ui.CommentMediaPolicy
import app.subshell.prototype.ui.ComposerCommunityPolicy
import app.subshell.prototype.ui.commentMediaPolicyFromReddit
import app.subshell.prototype.ui.ComposerCrosspostDestination
import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.ui.ComposerFlairRichText
import app.subshell.prototype.ui.ComposerPostRequirementsStatus
import app.subshell.prototype.ui.ComposerPostType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call

internal sealed interface AuthenticatedComposerPolicyResult {
    data class Loaded(val policy: ComposerCommunityPolicy) : AuthenticatedComposerPolicyResult
    data object AuthorizationRequired : AuthenticatedComposerPolicyResult
    data object Unavailable : AuthenticatedComposerPolicyResult
}

internal fun interface AuthenticatedComposerPolicyLoader {
    suspend fun load(accountStableId: String, community: String): AuthenticatedComposerPolicyResult

    // COMPOSER10_COMMENT_MEDIA_RULES_V1: the community's comment media rules
    // (only r/<x>/about), or null when unknown.
    suspend fun loadCommentMedia(accountStableId: String, community: String): CommentMediaPolicy? = null
}

internal class ConfiguredRedditComposerPolicyLoader(
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
) : AuthenticatedComposerPolicyLoader {
    override suspend fun loadCommentMedia(
        accountStableId: String,
        community: String,
    ): CommentMediaPolicy? {
        val normalizedCommunity = runCatching {
            normalizeRedditTextPostCommunity(community)
        }.getOrNull() ?: return null
        val session = (
            accountCoordinator.ensureAccessTokenForAccount(
                accountStableId = accountStableId,
                requiredScopes = setOf("read"),
            ) as? AuthSessionResult.Authenticated
            )?.session ?: return null
        val about = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = json,
            userAgent = userAgent,
            bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        ).getJson(
            redditComposerAboutRequest(normalizedCommunity),
            RedditCommunityAboutResponse.serializer(),
        )
        return (about as? NetworkResult.Success)?.value?.data?.allowedMediaInComments
            ?.let(::commentMediaPolicyFromReddit)
    }

    override suspend fun load(
        accountStableId: String,
        community: String,
    ): AuthenticatedComposerPolicyResult {
        val normalizedCommunity = runCatching {
            normalizeRedditTextPostCommunity(community)
        }.getOrNull() ?: return AuthenticatedComposerPolicyResult.Unavailable
        val session = when (
            val result = accountCoordinator.ensureAccessTokenForAccount(
                accountStableId = accountStableId,
                requiredScopes = setOf("read"),
            )
        ) {
            is AuthSessionResult.Authenticated -> result.session
            else -> return if (result.needsAuthorization) {
                AuthenticatedComposerPolicyResult.AuthorizationRequired
            } else {
                AuthenticatedComposerPolicyResult.Unavailable
            }
        }
        val httpClient = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = json,
            userAgent = userAgent,
            bearerTokenProvider = accountCoordinator.sessionBoundBearerTokenProvider(session.tokens.accessToken),
        )
        val about = httpClient.getJson(
            redditComposerAboutRequest(normalizedCommunity),
            RedditCommunityAboutResponse.serializer(),
        )
        val aboutData = when (about) {
            is NetworkResult.Success -> about.value.data
            else -> return AuthenticatedComposerPolicyResult.Unavailable
        }
        val hasSubmitScope = session.tokens.grants(setOf("submit"))
        val requirements = if (hasSubmitScope) {
            httpClient.getJson(
                redditComposerPostRequirementsRequest(normalizedCommunity),
                RedditPostRequirementsResponse.serializer(),
            )
        } else {
            null
        }
        val requirementData = (requirements as? NetworkResult.Success)?.value
        val postRequirementsStatus = when {
            !hasSubmitScope -> ComposerPostRequirementsStatus.AUTHORIZATION_REQUIRED
            requirements is NetworkResult.Success -> ComposerPostRequirementsStatus.LOADED
            else -> ComposerPostRequirementsStatus.UNAVAILABLE
        }
        val flairRequired = resolveRedditComposerFlairRequired(
            requirements = requirementData,
            aboutRequired = aboutData.postFlairRequired,
        )
        val communitySupportsFlair = flairRequired || aboutData.linkFlairEnabled
        val hasFlairScope = session.tokens.grants(setOf("flair"))
        val flairResponse = if (communitySupportsFlair && hasFlairScope) {
            httpClient.getJson(
                redditComposerFlairRequest(normalizedCommunity),
                kotlinx.serialization.builtins.ListSerializer(
                    RedditPostFlairResponse.serializer(),
                ),
            )
        } else {
            null
        }
        val flairAuthorizationRequired = communitySupportsFlair && !hasFlairScope
        val flairOptionsLoaded = !communitySupportsFlair || flairResponse is NetworkResult.Success
        val flairs = (flairResponse as? NetworkResult.Success)
            ?.value
            ?.mapNotNull(RedditPostFlairResponse::toComposerFlair)
            .orEmpty()
        val allowed = buildSet {
            if (aboutData.submissionType != "link") add(ComposerPostType.TEXT)
            if (aboutData.submissionType != "self") add(ComposerPostType.LINK)
            if (
                aboutData.allowImages ||
                aboutData.allowGalleries == true ||
                aboutData.allowVideos ||
                aboutData.allowVideoGifs
            ) {
                add(ComposerPostType.MEDIA)
            }
        }.ifEmpty { setOf(ComposerPostType.TEXT) }
        val bodyPolicy = requirementData?.bodyRestrictionPolicy.orEmpty()
        return AuthenticatedComposerPolicyResult.Loaded(
            ComposerCommunityPolicy(
                community = normalizedCommunity,
                allowedPostTypes = allowed,
                titleMinLength = requirementData?.titleTextMinLength?.coerceAtLeast(1) ?: 1,
                titleMaxLength = requirementData?.titleTextMaxLength?.takeIf { it > 0 } ?: 300,
                bodyAllowed = bodyPolicy != "notAllowed",
                bodyRequired = bodyPolicy == "required",
                bodyMinLength = requirementData?.bodyTextMinLength?.coerceAtLeast(0) ?: 0,
                bodyMaxLength = requirementData?.bodyTextMaxLength?.takeIf { it > 0 } ?: 40_000,
                titleRequiredStrings = requirementData?.titleRequiredStrings.orEmpty(),
                titleBlockedStrings = requirementData?.titleBlacklistedStrings.orEmpty(),
                bodyRequiredStrings = requirementData?.bodyRequiredStrings.orEmpty(),
                bodyBlockedStrings = requirementData?.bodyBlacklistedStrings.orEmpty(),
                domainWhitelist = requirementData?.domainWhitelist.orEmpty(),
                domainBlacklist = requirementData?.domainBlacklist.orEmpty(),
                allowImages = aboutData.allowImages,
                allowGalleries = aboutData.allowGalleries ?: aboutData.allowImages,
                allowVideo = aboutData.allowVideos,
                allowVideoGifs = aboutData.allowVideoGifs,
                flairRequired = flairRequired,
                flairOptions = flairs,
                flairOptionsLoaded = flairOptionsLoaded,
                flairAuthorizationRequired = flairAuthorizationRequired,
                postRequirementsStatus = postRequirementsStatus,
                loadedFromReddit = true,
                crosspostDestination = aboutData.toCrosspostDestination(
                    accountStableId = accountStableId,
                    community = normalizedCommunity,
                ),
            ),
        )
    }
}


internal fun resolveRedditComposerFlairRequired(
    requirements: RedditPostRequirementsResponse?,
    aboutRequired: Boolean,
): Boolean = requirements?.isFlairRequired ?: aboutRequired

internal fun redditComposerAboutRequest(community: String): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf("r", normalizeRedditTextPostCommunity(community), "about"),
    queryParameters = listOf("raw_json" to "1"),
)

internal fun redditComposerPostRequirementsRequest(community: String): JsonGetRequest =
    JsonGetRequest(
        pathSegments = listOf(
            "api",
            "v1",
            normalizeRedditTextPostCommunity(community),
            "post_requirements",
        ),
    )

internal fun redditComposerFlairRequest(community: String): JsonGetRequest = JsonGetRequest(
    pathSegments = listOf(
        "r",
        normalizeRedditTextPostCommunity(community),
        "api",
        "link_flair_v2",
    ),
    queryParameters = listOf("raw_json" to "1"),
)

@Serializable
private data class RedditCommunityAboutResponse(
    val data: RedditCommunityAboutData = RedditCommunityAboutData(),
)

@Serializable
internal data class RedditCommunityAboutData(
    @SerialName("submission_type") val submissionType: String = "any",
    @SerialName("allow_images") val allowImages: Boolean = false,
    @SerialName("allow_galleries") val allowGalleries: Boolean? = null,
    @SerialName("allow_videos") val allowVideos: Boolean = false,
    @SerialName("allow_videogifs") val allowVideoGifs: Boolean = false,
    @SerialName("post_flair_required") val postFlairRequired: Boolean = false,
    @SerialName("link_flair_enabled") val linkFlairEnabled: Boolean = false,
    // CROSSPOST01A1C_NULLABLE_REDDIT_DESTINATION_EVIDENCE_V1
    // CROSSPOST01A1E2R1_PUBLIC_ABOUT_CROSSPOSTABLE_V1
    @SerialName("crosspostable") val crosspostable: Boolean? = null,
    @SerialName("allow_post_crossposts") val allowCrossposts: Boolean? = null,
    @SerialName("subreddit_type") val subredditType: String? = null,
    @SerialName("user_is_banned") val userIsBanned: Boolean? = null,
    @SerialName("user_can_submit") val userCanSubmit: Boolean? = null,
    @SerialName("user_is_contributor") val userIsContributor: Boolean? = null,
    @SerialName("user_is_moderator") val userIsModerator: Boolean? = null,
    @SerialName("over18") val over18: Boolean? = null,
    // COMPOSER10_COMMENT_MEDIA_RULES_V1
    @SerialName("allowed_media_in_comments") val allowedMediaInComments: List<String>? = null,
)

// CROSSPOST01A1C_POLICY_PROJECTION_V1
internal fun RedditCommunityAboutData.toCrosspostDestination(
    accountStableId: String,
    community: String,
): ComposerCrosspostDestination = ComposerCrosspostDestination(
    accountStableId = accountStableId,
    community = community,
    allowCrossposts = crosspostable ?: allowCrossposts,
    subredditType = subredditType,
    userIsBanned = userIsBanned,
    userCanSubmit = userCanSubmit,
    userIsContributor = userIsContributor,
    userIsModerator = userIsModerator,
    nsfw = over18,
)

// ISSUE56_POST_REQUIREMENTS_ACCEPT_NULL_V1: Reddit sends null for every rule a
// community has not set (device-verified on r/subshell). A non-null field made
// the whole answer MALFORMED_JSON, so no posting rule was ever enforced.
@Serializable
internal data class RedditPostRequirementsResponse(
    @SerialName("title_text_min_length") val titleTextMinLength: Int? = null,
    @SerialName("title_text_max_length") val titleTextMaxLength: Int? = null,
    @SerialName("body_text_min_length") val bodyTextMinLength: Int? = null,
    @SerialName("body_text_max_length") val bodyTextMaxLength: Int? = null,
    @SerialName("body_restriction_policy") val bodyRestrictionPolicy: String? = null,
    @SerialName("is_flair_required") val isFlairRequired: Boolean? = null,
    @SerialName("title_required_strings") val titleRequiredStrings: List<String>? = null,
    @SerialName("title_blacklisted_strings") val titleBlacklistedStrings: List<String>? = null,
    @SerialName("body_required_strings") val bodyRequiredStrings: List<String>? = null,
    @SerialName("body_blacklisted_strings") val bodyBlacklistedStrings: List<String>? = null,
    @SerialName("domain_whitelist") val domainWhitelist: List<String>? = null,
    @SerialName("domain_blacklist") val domainBlacklist: List<String>? = null,
)

@Serializable
internal data class RedditPostFlairResponse(
    val id: String? = null,
    val text: String = "",
    @SerialName("text_editable") val textEditable: Boolean = false,
    @SerialName("text_color") val textColor: String? = null,
    @SerialName("background_color") val backgroundColor: String? = null,
    val richtext: List<RedditPostFlairRichText> = emptyList(),
) {
    fun toComposerFlair(): ComposerFlair? = id
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { templateId ->
            ComposerFlair(
                templateId = templateId,
                text = text,
                editable = textEditable,
                textColor = textColor,
                backgroundColor = backgroundColor,
                richText = richtext.mapNotNull(RedditPostFlairRichText::toComposerRichText),
            )
        }
}

@Serializable
internal data class RedditPostFlairRichText(
    @SerialName("e") val elementType: String = "",
    @SerialName("t") val text: String? = null,
    @SerialName("a") val emojiRef: String? = null,
    @SerialName("u") val imageUrl: String? = null,
) {
    fun toComposerRichText(): ComposerFlairRichText? = elementType
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let { type ->
            ComposerFlairRichText(
                elementType = type,
                text = text,
                emojiRef = emojiRef,
                imageUrl = imageUrl,
            )
        }
}
