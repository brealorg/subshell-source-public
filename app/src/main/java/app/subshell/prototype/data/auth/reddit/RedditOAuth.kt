package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.StoredOAuthClientSelection
import okhttp3.HttpUrl

internal data class RedditOAuthRuntimeSelection(
    val configuration: OAuthProviderConfiguration,
    val userAgent: String,
    val providerLabel: String,
)

internal enum class RedditOAuthCapability {
    CORE_READ,
    COMMENT_WRITE,
    COMMENT_EDIT,
    COMPOSER_FLAIR,
    POST_MODERATION,
    POST_VOTE,
    POST_SAVE,
    POST_FOLLOW,
    CONTENT_SAFETY,
    CUSTOM_FEED_WRITE,
    INBOX_READ,
    MODMAIL_READ,
    MODERATION_LOG,
    MODERATION_USERS,
    MODERATION_TEAM,
    PROFILE_HISTORY,
    COMMUNITY_WIKI_READ,
}

internal object RedditOAuth {
    const val REDREADER_COMPATIBILITY_REDIRECT_URI = "redreader://rr_oauth_redir"
    const val REDREADER_COMPATIBILITY_USER_AGENT = "org.quantumbadger.redreader/1.25.1"

    val authorizationEndpoint: HttpUrl = redditUrl(
        host = WWW_REDDIT_HOST,
        pathSegments = listOf("api", "v1", "authorize.compact"),
    )
    val tokenEndpoint: HttpUrl = redditUrl(
        host = WWW_REDDIT_HOST,
        pathSegments = listOf("api", "v1", "access_token"),
    )
    val revokeEndpoint: HttpUrl = redditUrl(
        host = WWW_REDDIT_HOST,
        pathSegments = listOf("api", "v1", "revoke_token"),
    )
    val apiEndpoint: HttpUrl = redditUrl(host = OAUTH_REDDIT_HOST)

    val coreReadScopes = setOf(
        "identity",
        "mysubreddits",
        "read",
    )

    val liveCommentWriteScopes = setOf(
        "identity",
        "mysubreddits",
        "read",
        "submit",
    )

    val liveTextPostWriteScopes = liveCommentWriteScopes + "history"

    val liveCommentEditScopes = liveTextPostWriteScopes + "edit"

    val liveComposerFlairScopes = liveCommentWriteScopes + "flair"

    val livePostModerationScopes = coreReadScopes + "modposts"

    val livePostVoteScopes = coreReadScopes + "vote"

    val livePostSaveScopes = coreReadScopes + "save"
    val livePostFollowScopes = coreReadScopes + "subscribe"

    // SAFETY01A2_CONTENT_SAFETY_REPORT_SCOPE_V1
    // USERACTION01A1_CONTENT_SAFETY_BLOCK_SCOPE_SUCCESSOR_V1
    val liveContentSafetyScopes = coreReadScopes + setOf(
        "account",
        "history",
        "privatemessages",
        "report",
    )

    val liveCustomFeedWriteScopes = coreReadScopes + "subscribe"

    val liveInboxReadScopes = coreReadScopes + "privatemessages"

    // MODMAIL01A1_INCREMENTAL_MODMAIL_SCOPE_V1
    val liveModmailReadScopes = coreReadScopes + "modmail"

    // MODERATION02A4_INCREMENTAL_MODLOG_SCOPE_V1
    val liveModerationLogScopes = coreReadScopes + "modlog"

    // MODERATION02A5A1_INCREMENTAL_MODCONTRIBUTORS_SCOPE_V1
    val liveModeratorUserManagementScopes = coreReadScopes + "modcontributors"

    // MODERATION02A5A5_INCREMENTAL_MODOTHERS_SCOPE_V1
    val liveModeratorTeamScopes = coreReadScopes + "modothers"

    val liveProfileHistoryScopes = coreReadScopes + "history"

    // WIKI01A1_INCREMENTAL_WIKIREAD_SCOPE_V1
    val liveCommunityWikiReadScopes = coreReadScopes + "wikiread"

    // ISSUE237_EVERY_SCOPE_AT_SIGN_IN_V1: sign-in asks once for every scope a
    // capability uses, moderator scopes included for every account (whether an
    // account moderates cannot be read before it has a token). The capability
    // authorizations stay only as the fallback for a token without them.
    val dailyDriverScopes = setOf(
        "account",
        "edit",
        "flair",
        "history",
        "identity",
        "modcontributors",
        "modlog",
        "modmail",
        "modothers",
        "modposts",
        "mysubreddits",
        "privatemessages",
        "read",
        "report",
        "submit",
        "save",
        "subscribe",
        "vote",
        "wikiread",
    )

    fun dailyDriverAccountSelection(
        base: RedditOAuthRuntimeSelection,
    ): RedditOAuthRuntimeSelection =
        base.copy(
            configuration = base.configuration.copy(
                scopes = dailyDriverScopes,
            ),
        )

    fun scopesFor(capability: RedditOAuthCapability): Set<String> = when (capability) {
        RedditOAuthCapability.CORE_READ -> coreReadScopes
        RedditOAuthCapability.COMMENT_WRITE -> liveCommentWriteScopes
        RedditOAuthCapability.COMMENT_EDIT -> liveCommentEditScopes
        RedditOAuthCapability.COMPOSER_FLAIR -> liveComposerFlairScopes
        RedditOAuthCapability.POST_MODERATION -> livePostModerationScopes
        RedditOAuthCapability.POST_VOTE -> livePostVoteScopes
        RedditOAuthCapability.POST_SAVE -> livePostSaveScopes
        RedditOAuthCapability.POST_FOLLOW -> livePostFollowScopes
        RedditOAuthCapability.CONTENT_SAFETY -> liveContentSafetyScopes
        RedditOAuthCapability.CUSTOM_FEED_WRITE -> liveCustomFeedWriteScopes
        RedditOAuthCapability.INBOX_READ -> liveInboxReadScopes
        RedditOAuthCapability.MODMAIL_READ -> liveModmailReadScopes
        RedditOAuthCapability.MODERATION_LOG -> liveModerationLogScopes
        RedditOAuthCapability.MODERATION_USERS -> liveModeratorUserManagementScopes
        RedditOAuthCapability.MODERATION_TEAM -> liveModeratorTeamScopes
        RedditOAuthCapability.PROFILE_HISTORY -> liveProfileHistoryScopes
        RedditOAuthCapability.COMMUNITY_WIKI_READ -> liveCommunityWikiReadScopes
    }

    fun installedApplication(
        clientId: String,
        redirectUri: String,
        // DEAD05_NO_DEFAULT_READ_SCOPES_V1 (OCB-511): every caller passes its
        // scopes; the unused default suggested a base grant that never applied.
        scopes: Set<String>,
        clientMode: OAuthClientMode = OAuthClientMode.NATIVE_PRODUCTION,
    ): OAuthProviderConfiguration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint,
        revokeEndpoint = revokeEndpoint,
        clientId = clientId,
        clientSecret = "",
        redirectUri = redirectUri,
        scopes = scopes,
        permanentAccess = true,
        clientMode = clientMode,
    )

    // ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1: a value equal to the
    // shipped one is stored as null, so "custom" means really different.
    fun redReaderCompatibilitySelection(
        clientId: String,
        redirectUri: String? = null,
        userAgent: String? = null,
    ): StoredOAuthClientSelection =
        StoredOAuthClientSelection(
            provider = OAuthProvider.REDDIT,
            mode = OAuthClientMode.REDREADER_COMPATIBILITY,
            clientId = clientId.trim(),
            redirectUri = redirectUri?.trim()?.takeUnless { it == REDREADER_COMPATIBILITY_REDIRECT_URI },
            userAgent = userAgent?.trim()?.takeUnless { it == REDREADER_COMPATIBILITY_USER_AGENT },
        )

    fun nativeProductionSelection(): StoredOAuthClientSelection = StoredOAuthClientSelection(
        provider = OAuthProvider.REDDIT,
        mode = OAuthClientMode.NATIVE_PRODUCTION,
    )

    fun isValidInstalledClientId(value: String): Boolean = runCatching {
        redReaderCompatibilitySelection(value)
    }.isSuccess

    fun selectRuntimeConfiguration(
        storedSelection: StoredOAuthClientSelection?,
        nativeConfigured: Boolean,
        nativeClientId: String,
        nativeRedirectUri: String,
        nativeUserAgent: String,
        capability: RedditOAuthCapability = RedditOAuthCapability.CORE_READ,
        preservedScopes: Set<String> = emptySet(),
    ): RedditOAuthRuntimeSelection? {
        val selection = storedSelection ?: nativeProductionSelection()
        require(selection.provider == OAuthProvider.REDDIT) {
            "Stored OAuth selection belongs to a different provider"
        }
        return when (selection.mode) {
            OAuthClientMode.NATIVE_PRODUCTION -> {
                if (!nativeConfigured) return null
                RedditOAuthRuntimeSelection(
                    configuration = installedApplication(
                        clientId = nativeClientId.trim(),
                        redirectUri = nativeRedirectUri.trim(),
                        scopes = when (capability) {
                            // DEAD05 (OCB-511): CORE_READ keeps preserved scopes
                            // too, the same as REDREADER_COMPATIBILITY.
                            RedditOAuthCapability.CORE_READ -> coreReadScopes + preservedScopes
                            RedditOAuthCapability.COMMENT_WRITE ->
                                liveCommentWriteScopes + preservedScopes
                            RedditOAuthCapability.COMMENT_EDIT ->
                                liveCommentEditScopes + preservedScopes
                            RedditOAuthCapability.COMPOSER_FLAIR ->
                                liveComposerFlairScopes + preservedScopes
                            RedditOAuthCapability.POST_MODERATION ->
                                livePostModerationScopes + preservedScopes
                            RedditOAuthCapability.POST_VOTE ->
                                livePostVoteScopes + preservedScopes
                            RedditOAuthCapability.POST_SAVE ->
                                livePostSaveScopes + preservedScopes
                            RedditOAuthCapability.POST_FOLLOW ->
                                livePostFollowScopes + preservedScopes
                            RedditOAuthCapability.CONTENT_SAFETY ->
                                liveContentSafetyScopes + preservedScopes
                            RedditOAuthCapability.CUSTOM_FEED_WRITE ->
                                liveCustomFeedWriteScopes + preservedScopes
                            RedditOAuthCapability.INBOX_READ ->
                                liveInboxReadScopes + preservedScopes
                            RedditOAuthCapability.MODMAIL_READ ->
                                liveModmailReadScopes + preservedScopes
                            RedditOAuthCapability.MODERATION_LOG ->
                                liveModerationLogScopes + preservedScopes
                            RedditOAuthCapability.MODERATION_USERS ->
                                liveModeratorUserManagementScopes + preservedScopes
                            RedditOAuthCapability.MODERATION_TEAM ->
                                liveModeratorTeamScopes + preservedScopes
                            RedditOAuthCapability.PROFILE_HISTORY ->
                                liveProfileHistoryScopes + preservedScopes
                            RedditOAuthCapability.COMMUNITY_WIKI_READ ->
                                liveCommunityWikiReadScopes + preservedScopes
                        },
                        clientMode = OAuthClientMode.NATIVE_PRODUCTION,
                    ),
                    userAgent = nativeUserAgent,
                    providerLabel = "Reddit",
                )
            }
            OAuthClientMode.REDREADER_COMPATIBILITY -> RedditOAuthRuntimeSelection(
                configuration = installedApplication(
                    clientId = requireNotNull(selection.clientId),
                    redirectUri = selection.redirectUri ?: REDREADER_COMPATIBILITY_REDIRECT_URI,
                    scopes = scopesFor(capability) + preservedScopes,
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                ),
                userAgent = selection.userAgent ?: REDREADER_COMPATIBILITY_USER_AGENT,
                providerLabel = "Reddit · RedReader compatibility",
            )
        }
    }

    private fun redditUrl(
        host: String,
        pathSegments: List<String> = emptyList(),
    ): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(host)
        .apply { pathSegments.forEach(::addPathSegment) }
        .build()

    private const val WWW_REDDIT_HOST = "www.reddit.com"
    private const val OAUTH_REDDIT_HOST = "oauth.reddit.com"
}
