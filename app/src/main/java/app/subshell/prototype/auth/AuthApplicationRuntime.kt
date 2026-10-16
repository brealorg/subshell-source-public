package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.reddit.AuthenticatedCrosspostSourceLoader

import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatStartWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatLiveReader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatTypingWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyWriter
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import android.content.Context
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwnerResolver
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizer
import app.subshell.prototype.data.auth.ConfiguredAuthCapabilityAuthorizer
import app.subshell.prototype.data.auth.OwnerBoundAuthCommentWriteCapability
import app.subshell.prototype.data.auth.OwnerContextBoundAuthCommentWriteCapability
import app.subshell.prototype.data.auth.PostManageAuthorizationContext
import app.subshell.prototype.data.auth.AuthConnectFlow
import app.subshell.prototype.data.auth.ConfiguredAuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsLoader
import app.subshell.prototype.data.auth.ConfiguredAuthConnectFlowController
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryLoader
import app.subshell.prototype.data.auth.ConfiguredAuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthInboxReadMutationWriter
import app.subshell.prototype.data.auth.ConfiguredAuthInboxReadCapability
import app.subshell.prototype.data.auth.OwnerBoundAuthInboxReadCapability
import app.subshell.prototype.data.auth.AndroidKeystoreAesGcmCipher
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthSessionManager
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.auth.UnavailableAuthConnectFlow
import app.subshell.prototype.data.auth.AUTH06C_LEGACY_HOME_BEST_QUERY_KEY
import app.subshell.prototype.data.auth.auth06cHomeBestQueryKey
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitter
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditor
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditor
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostDeleter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManager
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditRemovalReasonLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostFollowWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerCommunitySearchLoader
import app.subshell.prototype.data.auth.reddit.RedditAccountIdentityClient
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthCapability
import app.subshell.prototype.data.auth.reddit.RedditBootstrapper
import app.subshell.prototype.data.auth.reddit.RedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailWriter
import app.subshell.prototype.data.auth.reddit.RedditProfileCommentsLoader
import app.subshell.prototype.data.createFixtureFreeLocalSubshellRepository
import app.subshell.prototype.data.cache.AccountCacheRemover
import app.subshell.prototype.data.cache.DeletedPostCacheRemover
import app.subshell.prototype.data.cache.processRuntimeCacheStore
import app.subshell.prototype.data.itemsOrEmpty
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.DirectChatReactionPresentationCache
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellNetworkTransport
import app.subshell.prototype.data.auth.CustomFeedReader
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.ConfiguredAuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.OwnerBoundAuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.CustomFeedCreator
import app.subshell.prototype.data.auth.oauthScopeSet
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommunityMembershipWriter
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatRedactionWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionRelationsProbeReader
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairManager

internal data class AuthApplicationRuntime(
    val accountSessionDirectory: AccountSessionDirectory?,
    // HARNESS03_CUSTOM_FEED_SEAM_V1: interfaces; production wires Configured*.
    val customFeedReader: CustomFeedReader?,
    val customFeedWriteCapability: AuthCustomFeedWriteCapability?,
    val customFeedWriteClientMode: OAuthClientMode?,
    val customFeedCreator: CustomFeedCreator?,
    val inboxReadCapability: AuthInboxReadCapability?,
    val inboxReadClientMode: OAuthClientMode?,
    val inboxReadMutationWriter: AuthInboxReadMutationWriter?,
    val connectFlow: AuthConnectFlow,
    val commentWriteClientMode: OAuthClientMode?,
    val composerFlairClientMode: OAuthClientMode?,
    val commentSubmitter: AuthenticatedCommentSubmitter?,
    val textPostSubmitter: AuthenticatedTextPostSubmitter?,
    val composerPolicyLoader: AuthenticatedComposerPolicyLoader?,
    val crosspostSourceLoader: AuthenticatedCrosspostSourceLoader? = null,
    val composerCommunitySearchLoader: AuthenticatedComposerCommunitySearchLoader?,
    val redditFeedUpdater: AuthenticatedRedditFeedUpdater?,
    val redditInboxLoader: AuthenticatedRedditInboxLoader?,
    val redditCommentLoader: AuthenticatedRedditCommentLoader?,
    val redditSubscriptionsLoader: AuthenticatedRedditSubscriptionsLoader?,
    val redditCommunityDiscoveryLoader: AuthenticatedRedditCommunityDiscoveryLoader?,
    val redditCommunityInfoLoader: AuthenticatedRedditCommunityInfoLoader? = null,
    // WIKI01A1_RUNTIME_COMMUNITY_WIKI_READ_V1
    val communityWikiReadAuthorizer: AuthContextualCapabilityAuthorizer? = null,
    val communityWikiReadClientMode: OAuthClientMode? = null,
    val redditCommunityWikiLoader: AuthenticatedRedditCommunityWikiLoader? = null,
    val redditModerationLoader: AuthenticatedRedditModerationLoader? = null,
    val moderationLogCapability: AuthCommentWriteCapability? = null,
    val moderationLogClientMode: OAuthClientMode? = null,
    val redditModerationLogLoader: AuthenticatedRedditModerationLogLoader? = null,
    val moderatorUserManagementCapability: AuthCommentWriteCapability? = null,
    val moderatorUserManagementClientMode: OAuthClientMode? = null,
    val redditBannedUsersReader: AuthenticatedRedditBannedUsersReader? = null,
    val redditModeratorUserWriter: AuthenticatedRedditModeratorUserWriter? = null,
    val redditMutedUsersReader:
        app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersReader? = null,
    val redditModeratorUserMuteWriter:
        app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMuteWriter? = null,
    val redditApprovedUsersReader:
        app.subshell.prototype.data.auth.AuthenticatedRedditApprovedUsersReader? = null,
    val moderatorTeamCapability: AuthCommentWriteCapability? = null,
    val moderatorTeamClientMode: OAuthClientMode? = null,
    val redditModeratorInviteWriter:
        app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteWriter? = null,
    val redditModeratorPermissionWriter:
        app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionWriter? = null,
    val redditModeratorRemovalWriter:
        app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalWriter? = null,
    val redditModeratorUserApprovalWriter:
        app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserApprovalWriter? = null,
    val modmailReadCapability: AuthCommentWriteCapability? = null,
    val modmailReadClientMode: OAuthClientMode? = null,
    val redditModmailLoader: AuthenticatedRedditModmailLoader? = null,
    val redditModmailWriter: AuthenticatedRedditModmailWriter? = null,
    val textPostWriteClientMode: OAuthClientMode? = null,
    val commentEditClientMode: OAuthClientMode? = null,
    val commentEditor: AuthenticatedCommentEditor? = null,
    val commentDeleter: AuthenticatedCommentDeleter? = null,
    val postEditor: AuthenticatedPostEditor? = null,
    val postDeleter: AuthenticatedPostDeleter? = null,
    val postManager: AuthenticatedPostManager? = null,
    val postModerationClientMode: OAuthClientMode? = null,
    val moderationActionCapability: AuthCommentWriteCapability? = null,
    val moderationActionClientMode: OAuthClientMode? = null,
    val moderationActionWriter: AuthenticatedRedditModerationWriter? = null,
    val removalReasonCapability: AuthCommentWriteCapability? = null,
    val removalReasonClientMode: OAuthClientMode? = null,
    val removalReasonLoader: AuthenticatedRedditRemovalReasonLoader? = null,
    val commentEditAuthorizationCapability: AuthCommentWriteCapability? = null,
    val commentDeleteAuthorizationCapability: AuthCommentWriteCapability? = null,
    val commentWriteAuthorizationCapability: AuthCommentWriteCapability? = null,
    val textPostWriteAuthorizationCapability: AuthCommentWriteCapability? = null,
    val postEditAuthorizationCapability: AuthCommentWriteCapability? = null,
    val postDeleteAuthorizationCapability: AuthCommentWriteCapability? = null,
    val composerFlairAuthorizationCapability: AuthCommentWriteCapability? = null,
    val composerPostRequirementsAuthorizationCapability: AuthCommentWriteCapability? = null,
    val postManageEditAuthorizationCapability: AuthCommentWriteCapability? = null,
    val postManageFlairAuthorizationCapability: AuthCommentWriteCapability? = null,
    val postManageModerationAuthorizationCapability: AuthCommentWriteCapability? = null,
    val communityMembershipCapability: AuthCustomFeedWriteCapability? = null,
    val communityMembershipClientMode: OAuthClientMode? = null,
    val communityMembershipWriter: AuthenticatedCommunityMembershipWriter? = null,
    val redditFavoriteWriter: app.subshell.prototype.data.auth.reddit.RedditFavoriteCommunityWriter? = null,
    val redditFavoriteCapability: AuthCustomFeedWriteCapability? = null,
    val redditFavoriteClientMode: OAuthClientMode? = null,
    val postVoteCapability: AuthCommentWriteCapability? = null,
    val postVoteClientMode: OAuthClientMode? = null,
    val postVoteWriter: AuthenticatedPostVoteWriter? = null,
    val postSaveCapability: AuthCommentWriteCapability? = null,
    val postSaveClientMode: OAuthClientMode? = null,
    val postSaveWriter: AuthenticatedPostSaveWriter? = null,
    val postFollowCapability: AuthCommentWriteCapability? = null,
    val postFollowClientMode: OAuthClientMode? = null,
    val postFollowWriter: AuthenticatedPostFollowWriter? = null,
    val contentSafetyCapability: AuthCommentWriteCapability? = null,
    val contentSafetyClientMode: OAuthClientMode? = null,
    val contentSafetyWriter: AuthenticatedContentSafetyWriter? = null,
    val profileHistoryCapability: AuthProfileHistoryCapability? = null,
    val profileHistoryClientMode: OAuthClientMode? = null,
    val profileHistoryAuthorizer: AuthContextualCapabilityAuthorizer? = null,
    val redditProfileCommentsLoader: AuthenticatedRedditProfileCommentsLoader? = null,
    val redditAccountHistoryCommentLoader: AuthenticatedRedditAccountHistoryCommentLoader? = null,
    val redditAccountHistoryUpdater: AuthenticatedRedditAccountHistoryUpdater? = null,
    val redditSavedHistoryLoader: AuthenticatedRedditSavedHistoryLoader? = null,
    val messageReplyWriter: AuthenticatedRedditMessageReplyWriter? = null,
    val directChatLiveReader: AuthenticatedRedditDirectChatLiveReader? = null,
    // SUBSHELL_MESSAGES02D2B_OUTBOUND_TYPING_RUNTIME_V1
    val directChatTypingWriter: AuthenticatedRedditDirectChatTypingWriter? = null,
    // SUBSHELL_MESSAGES02D3B_DELETE_OWN_DIRECT_CHAT_MESSAGE_V1
    val directChatRedactionWriter: AuthenticatedRedditDirectChatRedactionWriter? = null,
    // SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1
    val directChatReactionWriter: AuthenticatedRedditDirectChatReactionWriter? = null,
    // SUBSHELL_MESSAGES02E2A_R3D_PERSISTED_REACTION_PRESENTATION_CACHE_V1
    val directChatReactionPresentationCache: DirectChatReactionPresentationCache? = null,
    // MED4A_LOGOUT_REMOVES_ACCOUNT_CACHE_V1 (OCB-527): logout drops the
    // account's rows from the offline cache.
    val accountCacheRemover: AccountCacheRemover? = null,
    val deletedPostCacheRemover: DeletedPostCacheRemover? = null,
    // SUBSHELL_MESSAGES02E2A_R3A_REACTION_RELATIONS_READ_PROBE_V1
    val directChatReactionRelationsProbeReader: AuthenticatedRedditDirectChatReactionRelationsProbeReader? = null,
    // SUBSHELL_MESSAGES02D5A_REAL_NEW_DIRECT_CHAT_V1
    val directChatStartWriter: AuthenticatedRedditDirectChatStartWriter? = null,
    // USERSTATUS01A1_OWN_ACCOUNT_PREFERENCE_RUNTIME_V1
    val onlineVisibilityService: app.subshell.prototype.data.account.RedditOnlineVisibilityService? = null,
    // ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1
    val communityAccessService: app.subshell.prototype.data.account.RedditCommunityAccessService? = null,
    val redditUserFlairManager: AuthenticatedRedditUserFlairManager? = null,
    val authorizationOwnerResolver: AuthCapabilityAuthorizationOwnerResolver? = null,
    // LOW11_CALLBACK_ROUTING_READS_THE_STORE_OFF_MAIN_V1 (OCB-404): where the
    // callback routing reads the encrypted records; the test runtime routes
    // inline.
    val callbackRoutingDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
)

internal object AuthApplicationRuntimeFactory {
    // HARNESS04_AUTH_RUNTIME_CIPHER_SEAM_V1: production always uses the
    // Android Keystore cipher; JVM tests pass a test cipher to run the real
    // wiring under Robolectric.
    fun create(
        context: Context,
        cipher: AuthCipher = AndroidKeystoreAesGcmCipher(),
    ): AuthApplicationRuntime {
        val appContext = context.applicationContext
        val textPostSubmitAttemptStore = TextPostSubmitAttemptStore(
            appContext.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE),
        )
        val store = EncryptedAuthSessionStore(appContext, cipher)
        // LOW9_CLIENT_SELECTION_READ_IS_RETRIED_V1 (OCB-397): one failed read
        // (a transient Keystore decrypt error) left the whole process without
        // sign-in. The read is tried up to three times before giving up.
        val storedSelectionResult = readWithRetries(read = store::oauthClientSelection)
        if (storedSelectionResult.isFailure) {
            return AuthApplicationRuntime(
                accountSessionDirectory = null,
                customFeedReader = null,
                customFeedWriteCapability = null,
                customFeedWriteClientMode = null,
                customFeedCreator = null,
                inboxReadCapability = null,
                inboxReadClientMode = null,
                inboxReadMutationWriter = null,
                connectFlow = UnavailableAuthConnectFlow(PROVIDER_LABEL),
                commentWriteClientMode = null,
                composerFlairClientMode = null,
                commentSubmitter = null,
                textPostSubmitter = null,
                composerPolicyLoader = null,
                composerCommunitySearchLoader = null,
                redditFeedUpdater = null,
                redditInboxLoader = null,
                redditCommentLoader = null,
                redditSubscriptionsLoader = null,
                redditCommunityDiscoveryLoader = null,
            )
        }
        val storedSelection = storedSelectionResult.getOrNull()
        val nativeUserAgent =
            "android:${BuildConfig.APPLICATION_ID}:${BuildConfig.VERSION_NAME} (subshell)"
        val selected = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
            )
        }.getOrNull() ?: return AuthApplicationRuntime(
            accountSessionDirectory = null,
            customFeedReader = null,
            customFeedWriteCapability = null,
            customFeedWriteClientMode = null,
            customFeedCreator = null,
            inboxReadCapability = null,
            inboxReadClientMode = null,
            inboxReadMutationWriter = null,
            connectFlow = UnavailableAuthConnectFlow(PROVIDER_LABEL),
            commentWriteClientMode = null,
            composerFlairClientMode = null,
            commentSubmitter = null,
            textPostSubmitter = null,
            composerPolicyLoader = null,
            composerCommunitySearchLoader = null,
            redditFeedUpdater = null,
            redditInboxLoader = null,
            redditCommentLoader = null,
            redditSubscriptionsLoader = null,
            redditCommunityDiscoveryLoader = null,
        )

        val configuration = selected.configuration
        // AUTH10_REQUEST_CURRENT_SCOPES_V1 (#59): no scopes are frozen here any
        // more. They were read once, from the account active when the runtime was
        // built, and then both requested and required by every capability, so an
        // incremental grant could replace a token with fewer scopes and another
        // account was judged by this one's grants. Each authorization now adds
        // the target account's current scopes when it begins
        // (requestingCurrentScopesOf); what a capability requires is its own set.
        val preservedCapabilityScopes = emptySet<String>()
        val commentWriteSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.COMMENT_WRITE,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val commentEditSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.COMMENT_EDIT,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val composerFlairSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.COMPOSER_FLAIR,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val postModerationSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.POST_MODERATION,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        // MOD01A21_REMOVAL_REASON_MODCONTRIBUTORS_SELECTION_V1
        val removalReasonSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.POST_MODERATION,
                preservedScopes = preservedCapabilityScopes + "modcontributors",
            )
        }.getOrNull()
        val postVoteSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.POST_VOTE,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val postSaveSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.POST_SAVE,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val postFollowSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.POST_FOLLOW,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val contentSafetySelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.CONTENT_SAFETY,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val customFeedWriteSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.CUSTOM_FEED_WRITE,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val inboxReadSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.INBOX_READ,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        // MODMAIL01A1_INCREMENTAL_SELECTION_V1
        val modmailReadSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.MODMAIL_READ,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        // MODERATION02A4_INCREMENTAL_SELECTION_V1
        val moderationLogSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.MODERATION_LOG,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        // MODERATION02A5A1_INCREMENTAL_SELECTION_V1
        val moderatorUserManagementSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.MODERATION_USERS,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        // MODERATION02A5A5_INCREMENTAL_MODOTHERS_SELECTION_V1
        val moderatorTeamSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.MODERATION_TEAM,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val profileHistorySelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.PROFILE_HISTORY,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val communityWikiReadSelection = runCatching {
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = storedSelection,
                nativeConfigured = BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED,
                nativeClientId = BuildConfig.AUTH04_REDDIT_CLIENT_ID,
                nativeRedirectUri = BuildConfig.AUTH04_REDDIT_REDIRECT_URI,
                nativeUserAgent = nativeUserAgent,
                capability = RedditOAuthCapability.COMMUNITY_WIKI_READ,
                preservedScopes = preservedCapabilityScopes,
            )
        }.getOrNull()
        val callFactory = SubshellNetworkTransport.sharedCallFactory
        val tokenClient = OAuthTokenEndpointClient(
            callFactory = callFactory,
            configuration = configuration,
            userAgent = selected.userAgent,
        )
        val liveCacheStore = processRuntimeCacheStore(appContext)
        val compatibilityCacheStore = liveCacheStore.takeIf {
            configuration.clientMode == app.subshell.prototype.data.auth.OAuthClientMode.REDREADER_COMPATIBILITY
        }
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = RedditAccountIdentityClient(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                userAgent = selected.userAgent,
            ),
            redditBootstrapper = if (
                configuration.clientMode == app.subshell.prototype.data.auth.OAuthClientMode.REDREADER_COMPATIBILITY
            ) {
                RedditBootstrapper(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = selected.userAgent,
                    configuration = configuration,
                    cacheStore = requireNotNull(compatibilityCacheStore),
                )
            } else {
                null
            },
            localFixtureUsernames = createFixtureFreeLocalSubshellRepository()
                .accounts()
                .itemsOrEmpty(),
        )
        val profileHistoryCapability =
            profileHistorySelection?.let { historySelection ->
                ConfiguredAuthProfileHistoryCapability(
                    configuration = historySelection.configuration,
                    accountCoordinator = coordinator,
                )
            }
        val profileHistoryAuthorizer =
            profileHistorySelection?.let { historySelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = historySelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = historySelection.configuration,
                        userAgent = historySelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = historySelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        coordinator.startDirectoryHydration { directory ->
            compatibilityCacheStore?.let { cacheStore ->
                val active = directory.accounts.first { account ->
                    account.stableId == directory.activeAccountId
                }
                // LOW11_LEGACY_HOME_COPY_IS_SAFE_V1: the unscoped legacy Home feed
                // belongs to whichever account existed before feeds were
                // account-scoped; it is copied only while one authenticated
                // account is known (OCB-579: a second account got the first
                // one's feed), and a cache error is not a crash (OCB-398).
                val authenticatedAccounts = directory.accounts.count { account ->
                    account.source == AccountSessionSource.AUTHENTICATED
                }
                if (active.source == AccountSessionSource.AUTHENTICATED && authenticatedAccounts == 1) {
                    try {
                        cacheStore.copyPostFeedIfAbsent(
                            sourceQueryKey = AUTH06C_LEGACY_HOME_BEST_QUERY_KEY,
                            destinationQueryKey = auth06cHomeBestQueryKey(active.stableId),
                        )
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The account starts without the old Home feed.
                    }
                }
            }
        }
        val communityWikiReadAuthorizer =
            communityWikiReadSelection?.let { wikiSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = wikiSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = wikiSelection.configuration,
                        userAgent = wikiSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = wikiSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val inboxReadPredecessorCapability =
            inboxReadSelection?.let { inboxSelection ->
                ConfiguredAuthInboxReadCapability(
                    configuration = inboxSelection.configuration,
                    store = store,
                    accountCoordinator = coordinator,
                )
            }
        val inboxReadAuthorizer = inboxReadSelection?.let { inboxSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = inboxSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = inboxSelection.configuration,
                    userAgent = inboxSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = inboxSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val inboxReadCapability =
            if (
                inboxReadPredecessorCapability != null &&
                inboxReadAuthorizer != null
            ) {
                OwnerBoundAuthInboxReadCapability(
                    delegate = inboxReadPredecessorCapability,
                    authorizer = inboxReadAuthorizer,
                )
            } else {
                // Unreachable with a predecessor: both come from one selection.
                null
            }
        val modmailReadAuthorizer = modmailReadSelection?.let { modmailSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = modmailSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = modmailSelection.configuration,
                    userAgent = modmailSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = modmailSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val modmailReadCapability = modmailReadAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.MODMAIL_READ,
            )
        }
        val moderationLogAuthorizer =
            moderationLogSelection?.let { logSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = logSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = logSelection.configuration,
                        userAgent = logSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = logSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val moderationLogCapability =
            moderationLogAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.MODERATION_LOG,
                )
            }
        val moderatorUserManagementAuthorizer =
            moderatorUserManagementSelection?.let { userSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = userSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = userSelection.configuration,
                        userAgent = userSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = userSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val moderatorUserManagementCapability =
            moderatorUserManagementAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.MODERATION_USERS,
                )
            }
        val moderatorTeamAuthorizer =
            moderatorTeamSelection?.let { teamSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = teamSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = teamSelection.configuration,
                        userAgent = teamSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = teamSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val moderatorTeamCapability =
            moderatorTeamAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.MODERATION_TEAM,
                )
            }
        // DEAD01_ONE_CONNECT_SESSION_MANAGER_V1 (OCB-399): the core runtime's own
        // OAuthSessionManager was never read; the connect flow has its own.
        val connectSelection =
            RedditOAuth.dailyDriverAccountSelection(selected)
        val connectSessionManager = OAuthSessionManager(
            configuration = connectSelection.configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = callFactory,
                configuration = connectSelection.configuration,
                userAgent = connectSelection.userAgent,
            ),
        )
        val textPostWriteSelection = commentWriteSelection?.let { writeSelection ->
            writeSelection.copy(
                configuration = writeSelection.configuration.copy(
                    scopes = RedditOAuth.liveTextPostWriteScopes,
                ),
            )
        }
        val commentWriteCapability = commentWriteSelection?.let { writeSelection ->
            ConfiguredAuthCommentWriteCapability(
                configuration = writeSelection.configuration,
                accountCoordinator = coordinator,
            )
        }
        val commentWriteAuthorizer = commentWriteSelection?.let { writeSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = writeSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = writeSelection.configuration,
                    userAgent = writeSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = writeSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val commentWriteAuthorizationCapability =
            commentWriteAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.COMMENT_WRITE,
                )
            }
        val composerPostRequirementsAuthorizationCapability =
            commentWriteAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.COMPOSER_REQUIREMENTS,
                )
            }
        val commentEditCapability = commentEditSelection?.let { editSelection ->
            ConfiguredAuthCommentWriteCapability(
                configuration = editSelection.configuration,
                accountCoordinator = coordinator,
            )
        }
        val commentEditAuthorizer = commentEditSelection?.let { editSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = editSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = editSelection.configuration,
                    userAgent = editSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = editSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val commentEditAuthorizationCapability = commentEditAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.COMMENT_EDIT,
            )
        }
        val commentDeleteAuthorizationCapability = commentEditAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.COMMENT_DELETE,
            )
        }
        val postEditAuthorizationCapability = commentEditAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.POST_EDIT,
            )
        }
        val postDeleteAuthorizationCapability = commentEditAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.POST_DELETE,
            )
        }
        val postModerationAuthorizer =
            postModerationSelection?.let { moderationSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = moderationSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = moderationSelection.configuration,
                        userAgent = moderationSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = moderationSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val removalReasonAuthorizer =
            removalReasonSelection?.let { reasonSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = reasonSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = reasonSelection.configuration,
                        userAgent = reasonSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = reasonSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val postVoteAuthorizer = postVoteSelection?.let { voteSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = voteSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = voteSelection.configuration,
                    userAgent = voteSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = voteSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val postVoteCapability = postVoteAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.POST_VOTE,
            )
        }
        val postSaveAuthorizer = postSaveSelection?.let { saveSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = saveSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = saveSelection.configuration,
                    userAgent = saveSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = saveSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val postSaveCapability = postSaveAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.POST_SAVE,
            )
        }
        val postFollowAuthorizer = postFollowSelection?.let { followSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = followSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = followSelection.configuration,
                    userAgent = followSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = followSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val postFollowCapability = postFollowAuthorizer?.let { authorizer ->
            OwnerBoundAuthCommentWriteCapability(
                authorizer = authorizer,
                owner = AuthCapabilityAuthorizationOwner.POST_FOLLOW,
            )
        }
        val contentSafetyAuthorizer =
            contentSafetySelection?.let { safetySelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = safetySelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = safetySelection.configuration,
                        userAgent = safetySelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = safetySelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val contentSafetyCapability =
            contentSafetyAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.CONTENT_SAFETY,
                )
            }
        val textPostWriteCapability = textPostWriteSelection?.let { writeSelection ->
            ConfiguredAuthCommentWriteCapability(
                configuration = writeSelection.configuration,
                accountCoordinator = coordinator,
            )
        }
        val textPostWriteAuthorizer =
            textPostWriteSelection?.let { writeSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = writeSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = writeSelection.configuration,
                        userAgent = writeSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = writeSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val textPostWriteAuthorizationCapability =
            textPostWriteAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.TEXT_POST_WRITE,
                )
            }
        val composerFlairCapability = composerFlairSelection?.let { flairSelection ->
            ConfiguredAuthCommentWriteCapability(
                configuration = flairSelection.configuration,
                accountCoordinator = coordinator,
            )
        }
        val composerFlairAuthorizer = composerFlairSelection?.let { flairSelection ->
            ConfiguredAuthCapabilityAuthorizer(
                configuration = flairSelection.configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = callFactory,
                    configuration = flairSelection.configuration,
                    userAgent = flairSelection.userAgent,
                ),
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = flairSelection.userAgent,
                ),
                accountCoordinator = coordinator,
            )
        }
        val composerFlairAuthorizationCapability =
            composerFlairAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.COMPOSER_FLAIR,
                )
            }
        val postManageEditAuthorizationCapability =
            commentEditAuthorizer?.let { authorizer ->
                OwnerContextBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.POST_MANAGE,
                    contextKey = PostManageAuthorizationContext.EDIT.wireKey,
                )
            }
        val postManageFlairAuthorizationCapability =
            composerFlairAuthorizer?.let { authorizer ->
                OwnerContextBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.POST_MANAGE,
                    contextKey = PostManageAuthorizationContext.FLAIR.wireKey,
                )
            }
        val postManageModerationAuthorizationCapability =
            postModerationAuthorizer?.let { authorizer ->
                OwnerContextBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.POST_MANAGE,
                    contextKey = PostManageAuthorizationContext.MODERATION.wireKey,
                )
            }
        // MOD01A2_OWNER_BOUND_MODERATION_ACTION_CAPABILITY_V1
        val moderationActionCapability =
            postModerationAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.MODERATION_ACTION,
                )
            }
        val removalReasonCapability =
            removalReasonAuthorizer?.let { authorizer ->
                OwnerBoundAuthCommentWriteCapability(
                    authorizer = authorizer,
                    owner = AuthCapabilityAuthorizationOwner.MODERATION_REASON,
                )
            }
        val customFeedWritePredecessorCapability =
            customFeedWriteSelection?.let { writeSelection ->
                ConfiguredAuthCustomFeedWriteCapability(
                    configuration = writeSelection.configuration,
                    store = store,
                    accountCoordinator = coordinator,
                )
            }
        val customFeedWriteAuthorizer =
            customFeedWriteSelection?.let { writeSelection ->
                ConfiguredAuthCapabilityAuthorizer(
                    configuration = writeSelection.configuration,
                    store = store,
                    tokenClient = OAuthTokenEndpointClient(
                        callFactory = callFactory,
                        configuration = writeSelection.configuration,
                        userAgent = writeSelection.userAgent,
                    ),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = writeSelection.userAgent,
                    ),
                    accountCoordinator = coordinator,
                )
            }
        val customFeedWriteCapability =
            if (
                customFeedWritePredecessorCapability != null &&
                customFeedWriteAuthorizer != null
            ) {
                OwnerBoundAuthCustomFeedWriteCapability(
                    delegate = customFeedWritePredecessorCapability,
                    authorizer = customFeedWriteAuthorizer,
                )
            } else {
                // Unreachable with a predecessor: both come from one selection.
                null
            }

        val communityMembershipCapability =
            if (
                customFeedWritePredecessorCapability != null &&
                customFeedWriteAuthorizer != null
            ) {
                OwnerBoundAuthCustomFeedWriteCapability(
                    delegate = customFeedWritePredecessorCapability,
                    authorizer = customFeedWriteAuthorizer,
                    owner =
                        AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP,
                )
            } else {
                null
            }

        // REDDITFAV03_FAVORITE_AUTHORIZATION_V1: Reddit favorites ask for
        // `subscribe` under their own owner, so a callback finishes the
        // favorite change that asked for it.
        val redditFavoriteCapability =
            if (
                customFeedWritePredecessorCapability != null &&
                customFeedWriteAuthorizer != null
            ) {
                OwnerBoundAuthCustomFeedWriteCapability(
                    delegate = customFeedWritePredecessorCapability,
                    authorizer = customFeedWriteAuthorizer,
                    owner =
                        AuthCapabilityAuthorizationOwner.REDDIT_FAVORITE,
                )
            } else {
                null
            }

        val profileCommentsLoader =
            if (
                profileHistorySelection != null &&
                profileHistorySelection.configuration.clientMode ==
                    configuration.clientMode
            ) {
                RedditProfileCommentsLoader(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = profileHistorySelection.userAgent,
                    cacheStore = liveCacheStore,
                    capability = requireNotNull(profileHistoryCapability),
                )
            } else {
                null
            }
        // SAFETY01A2_SHARE_AUTHORITATIVE_HIDDEN_HISTORY_UPDATER_V1
        val accountHistoryUpdater =
            if (
                profileHistorySelection != null &&
                profileHistorySelection.configuration.clientMode ==
                    configuration.clientMode
            ) {
                RedditAccountHistoryUpdater(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = profileHistorySelection.userAgent,
                    cacheStore = liveCacheStore,
                    capability = requireNotNull(profileHistoryCapability),
                )
            } else {
                null
            }

        val directChat = directChatRuntimeParts(
            appContext = appContext,
            configuration = configuration,
            inboxReadSelection = inboxReadSelection,
            inboxReadCapability = inboxReadCapability,
            commentWriteCapability = commentWriteCapability,
        )
        val messageReplyWriter = directChat.messageReplyWriter
        val directChatLiveReader = directChat.liveReader
        val directChatTypingWriter = directChat.typingWriter
        val directChatRedactionWriter = directChat.redactionWriter
        val directChatReactionWriter = directChat.reactionWriter
        val directChatReactionPresentationCache = directChat.reactionPresentationCache
        val directChatReactionRelationsProbeReader = directChat.reactionRelationsProbeReader
        val directChatStartWriter = directChat.startWriter

        val moderation = moderationRuntimeParts(
            callFactory = callFactory,
            selected = selected,
            coordinator = coordinator,
            moderationLogSelection = moderationLogSelection,
            moderationLogCapability = moderationLogCapability,
            moderatorUserManagementSelection = moderatorUserManagementSelection,
            moderatorUserManagementCapability = moderatorUserManagementCapability,
            moderatorTeamSelection = moderatorTeamSelection,
            moderatorTeamCapability = moderatorTeamCapability,
            modmailReadSelection = modmailReadSelection,
            modmailReadCapability = modmailReadCapability,
        )

        val readLoaders = readLoaderRuntimeParts(
            appContext = appContext,
            selected = selected,
            configuration = configuration,
            inboxReadSelection = inboxReadSelection,
            profileHistorySelection = profileHistorySelection,
            communityWikiReadSelection = communityWikiReadSelection,
            callFactory = callFactory,
            liveCacheStore = liveCacheStore,
            compatibilityCacheStore = compatibilityCacheStore,
            coordinator = coordinator,
            profileHistoryCapability = profileHistoryCapability,
            communityWikiReadAuthorizer = communityWikiReadAuthorizer,
            inboxReadCapability = inboxReadCapability,
        )

        val postWrite = postWriteRuntimeParts(
            appContext = appContext,
            textPostSubmitAttemptStore = textPostSubmitAttemptStore,
            selected = selected,
            commentWriteSelection = commentWriteSelection,
            commentEditSelection = commentEditSelection,
            postVoteSelection = postVoteSelection,
            postSaveSelection = postSaveSelection,
            postFollowSelection = postFollowSelection,
            callFactory = callFactory,
            coordinator = coordinator,
            textPostWriteSelection = textPostWriteSelection,
            commentWriteCapability = commentWriteCapability,
            commentEditCapability = commentEditCapability,
            postVoteCapability = postVoteCapability,
            postSaveCapability = postSaveCapability,
            postFollowCapability = postFollowCapability,
            textPostWriteCapability = textPostWriteCapability,
        )

        val accountWrite = accountWriteRuntimeParts(
            selected = selected,
            configuration = configuration,
            composerFlairSelection = composerFlairSelection,
            postModerationSelection = postModerationSelection,
            removalReasonSelection = removalReasonSelection,
            contentSafetySelection = contentSafetySelection,
            customFeedWriteSelection = customFeedWriteSelection,
            inboxReadSelection = inboxReadSelection,
            callFactory = callFactory,
            liveCacheStore = liveCacheStore,
            coordinator = coordinator,
            inboxReadCapability = inboxReadCapability,
            contentSafetyCapability = contentSafetyCapability,
            composerFlairCapability = composerFlairCapability,
            moderationActionCapability = moderationActionCapability,
            removalReasonCapability = removalReasonCapability,
            communityMembershipCapability = communityMembershipCapability,
            redditFavoriteCapability = redditFavoriteCapability,
            accountHistoryUpdater = accountHistoryUpdater,
        )

        return AuthApplicationRuntime(
            accountSessionDirectory = coordinator,
            customFeedReader = accountWrite.customFeedReader,
            customFeedWriteCapability = customFeedWriteCapability,
            customFeedWriteClientMode = customFeedWriteSelection?.configuration?.clientMode,
            customFeedCreator = accountWrite.customFeedCreator,
            inboxReadCapability = inboxReadCapability,
            inboxReadClientMode = inboxReadSelection?.configuration?.clientMode,
            inboxReadMutationWriter = accountWrite.inboxReadMutationWriter,
            messageReplyWriter = messageReplyWriter,
            directChatLiveReader = directChatLiveReader,
            directChatTypingWriter = directChatTypingWriter,
            directChatRedactionWriter = directChatRedactionWriter,
            directChatReactionWriter = directChatReactionWriter,
            directChatReactionPresentationCache = directChatReactionPresentationCache,
            accountCacheRemover = liveCacheStore,
            deletedPostCacheRemover = liveCacheStore,
            directChatReactionRelationsProbeReader = directChatReactionRelationsProbeReader,
            directChatStartWriter = directChatStartWriter,
            connectFlow = ConfiguredAuthConnectFlowController(
                sessionManager = connectSessionManager,
                accountCoordinator = coordinator,
                store = store,
                providerLabel = selected.providerLabel,
                clientMode = connectSelection.configuration.clientMode,
            ),
            textPostWriteClientMode = textPostWriteSelection?.configuration?.clientMode,
            commentWriteClientMode = commentWriteSelection?.configuration?.clientMode,
            commentEditClientMode = commentEditSelection?.configuration?.clientMode,
            commentEditAuthorizationCapability = commentEditAuthorizationCapability,
            commentDeleteAuthorizationCapability = commentDeleteAuthorizationCapability,
            commentWriteAuthorizationCapability = commentWriteAuthorizationCapability,
            textPostWriteAuthorizationCapability = textPostWriteAuthorizationCapability,
            postEditAuthorizationCapability = postEditAuthorizationCapability,
            postDeleteAuthorizationCapability = postDeleteAuthorizationCapability,
            composerFlairAuthorizationCapability = composerFlairAuthorizationCapability,
            composerPostRequirementsAuthorizationCapability =
                composerPostRequirementsAuthorizationCapability,
            postManageEditAuthorizationCapability =
                postManageEditAuthorizationCapability,
            postManageFlairAuthorizationCapability =
                postManageFlairAuthorizationCapability,
            postManageModerationAuthorizationCapability =
                postManageModerationAuthorizationCapability,
            moderationActionCapability = moderationActionCapability,
            moderationActionClientMode =
                postModerationSelection?.configuration?.clientMode,
            moderationActionWriter = accountWrite.moderationActionWriter,
            removalReasonCapability = removalReasonCapability,
            removalReasonClientMode = removalReasonSelection?.configuration?.clientMode,
            removalReasonLoader = accountWrite.removalReasonLoader,
            communityMembershipCapability =
                communityMembershipCapability,
            communityMembershipClientMode =
                customFeedWriteSelection?.configuration?.clientMode,
            communityMembershipWriter = accountWrite.communityMembershipWriter,
            redditFavoriteWriter = accountWrite.redditFavoriteWriter,
            redditFavoriteCapability = redditFavoriteCapability,
            redditFavoriteClientMode =
                customFeedWriteSelection?.configuration?.clientMode,
            postVoteCapability = postVoteCapability,
            postVoteClientMode = postVoteSelection?.configuration?.clientMode,
            postVoteWriter = postWrite.postVoteWriter,
            postSaveCapability = postSaveCapability,
            postSaveClientMode = postSaveSelection?.configuration?.clientMode,
            postSaveWriter = postWrite.postSaveWriter,
            postFollowCapability = postFollowCapability,
            postFollowClientMode = postFollowSelection?.configuration?.clientMode,
            postFollowWriter = postWrite.postFollowWriter,
            contentSafetyCapability = contentSafetyCapability,
            contentSafetyClientMode =
                contentSafetySelection?.configuration?.clientMode,
            contentSafetyWriter = accountWrite.contentSafetyWriter,
            profileHistoryCapability = profileHistoryCapability,
            profileHistoryClientMode =
                profileHistorySelection?.configuration?.clientMode,
            profileHistoryAuthorizer = profileHistoryAuthorizer,
            onlineVisibilityService = accountWrite.onlineVisibilityService,
            communityAccessService = accountWrite.communityAccessService,
            redditUserFlairManager = accountWrite.redditUserFlairManager,
            authorizationOwnerResolver = AuthCapabilityAuthorizationOwnerResolver(store),
            commentEditor = postWrite.commentEditor,
            commentDeleter = postWrite.commentDeleter,
            postEditor = postWrite.postEditor,
            postDeleter = postWrite.postDeleter,
            postManager = postWrite.postManager,
            postModerationClientMode =
                postModerationSelection?.configuration?.clientMode,
            composerFlairClientMode = composerFlairSelection?.configuration?.clientMode,
            commentSubmitter = postWrite.commentSubmitter,
            textPostSubmitter = postWrite.textPostSubmitter,
            crosspostSourceLoader = postWrite.crosspostSourceLoader,
            composerPolicyLoader = postWrite.composerPolicyLoader,
            composerCommunitySearchLoader = postWrite.composerCommunitySearchLoader,
            redditFeedUpdater = readLoaders.redditFeedUpdater,
            redditInboxLoader = readLoaders.redditInboxLoader,
            redditProfileCommentsLoader = profileCommentsLoader,
            redditAccountHistoryCommentLoader = profileCommentsLoader,
            redditAccountHistoryUpdater = accountHistoryUpdater,
            redditSavedHistoryLoader = readLoaders.redditSavedHistoryLoader,
            redditCommentLoader = readLoaders.redditCommentLoader,
            redditSubscriptionsLoader = readLoaders.redditSubscriptionsLoader,
            redditCommunityDiscoveryLoader = readLoaders.redditCommunityDiscoveryLoader,
            communityWikiReadAuthorizer = communityWikiReadAuthorizer,
            communityWikiReadClientMode =
                communityWikiReadSelection?.configuration?.clientMode,
            redditCommunityWikiLoader = readLoaders.redditCommunityWikiLoader,
            redditCommunityInfoLoader = readLoaders.redditCommunityInfoLoader,
            // MOD01A1_RUNTIME_MODERATION_READ_WIRING_V1
            redditModerationLoader = moderation.redditModerationLoader,
            // MODERATION02A4_RUNTIME_MOD_LOG_READ_WIRING_V1
            moderationLogCapability = moderationLogCapability,
            moderationLogClientMode =
                moderationLogSelection?.configuration?.clientMode,
            redditModerationLogLoader = moderation.redditModerationLogLoader,
            // MODERATION02A5A1_RUNTIME_USER_MANAGEMENT_WIRING_V1
            moderatorUserManagementCapability = moderatorUserManagementCapability,
            moderatorUserManagementClientMode =
                moderatorUserManagementSelection?.configuration?.clientMode,
            redditBannedUsersReader = moderation.redditBannedUsersReader,
            redditModeratorUserWriter = moderation.redditModeratorUserWriter,
            redditMutedUsersReader = moderation.redditMutedUsersReader,
            redditModeratorUserMuteWriter = moderation.redditModeratorUserMuteWriter,
            redditApprovedUsersReader = moderation.redditApprovedUsersReader,
            // MODERATION02A5A5_RUNTIME_MODERATOR_INVITE_WIRING_V1
            moderatorTeamCapability = moderatorTeamCapability,
            moderatorTeamClientMode = moderatorTeamSelection?.configuration?.clientMode,
            redditModeratorInviteWriter = moderation.redditModeratorInviteWriter,
            // MODERATION02A5A6_RUNTIME_PERMISSION_WRITER_V1
            redditModeratorPermissionWriter = moderation.redditModeratorPermissionWriter,
            // MODERATION02A5A7_RUNTIME_REMOVAL_WRITER_V1
            redditModeratorRemovalWriter = moderation.redditModeratorRemovalWriter,
            redditModeratorUserApprovalWriter = moderation.redditModeratorUserApprovalWriter,
            // MODMAIL01A1_RUNTIME_READ_WIRING_V1
            modmailReadCapability = modmailReadCapability,
            modmailReadClientMode =
                modmailReadSelection?.configuration?.clientMode,
            redditModmailLoader = moderation.redditModmailLoader,
            // MODMAIL02_RUNTIME_REPLY_INTERNAL_NOTE_WRITER_V1
            redditModmailWriter = moderation.redditModmailWriter,
        )
    }

    private const val PROVIDER_LABEL = "Reddit"
}

internal fun <T> readWithRetries(
    attempts: Int = 3,
    pauseMillis: Long = 100L,
    read: () -> T,
): Result<T> {
    var last: Result<T> = runCatching(read)
    repeat(attempts - 1) {
        if (last.isSuccess) return last
        Thread.sleep(pauseMillis)
        last = runCatching(read)
    }
    return last
}
