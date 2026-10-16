package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailWriter
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditModmailWriter
import app.subshell.prototype.data.auth.reddit.RedditModerationLoader
import app.subshell.prototype.data.auth.reddit.RedditModerationLogLoader
import app.subshell.prototype.data.auth.reddit.RedditModeratorBannedUsersReader
import app.subshell.prototype.data.auth.reddit.RedditModeratorUserWriter
import app.subshell.prototype.data.auth.reddit.RedditModmailLoader
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthRuntimeSelection
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellNetworkTransport
import okhttp3.Call

/**
 * The moderation and modmail part of the auth runtime: the moderation
 * directory/queue loader, the mod log, user management (banned, muted,
 * approved users and their writers), the moderator team writers (invite,
 * permissions, removal) and the modmail loader and writer. Each is null
 * unless its own incremental selection and owner-bound capability exist.
 * Property names are the AuthApplicationRuntime fields they fill.
 */
internal class ModerationRuntimeParts(
    val redditModerationLoader: AuthenticatedRedditModerationLoader?,
    val redditModerationLogLoader: AuthenticatedRedditModerationLogLoader?,
    val redditBannedUsersReader: AuthenticatedRedditBannedUsersReader?,
    val redditModeratorUserWriter: AuthenticatedRedditModeratorUserWriter?,
    val redditMutedUsersReader: app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersReader?,
    val redditModeratorUserMuteWriter: app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMuteWriter?,
    val redditApprovedUsersReader: app.subshell.prototype.data.auth.AuthenticatedRedditApprovedUsersReader?,
    val redditModeratorInviteWriter: app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteWriter?,
    val redditModeratorPermissionWriter: app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionWriter?,
    val redditModeratorRemovalWriter: app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalWriter?,
    val redditModeratorUserApprovalWriter: app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserApprovalWriter?,
    val redditModmailLoader: AuthenticatedRedditModmailLoader?,
    val redditModmailWriter: AuthenticatedRedditModmailWriter?,
)

/**
 * Builds the moderation parts. The constructor arguments moved out of
 * AuthApplicationRuntimeFactory.create()'s AuthApplicationRuntime(...) call
 * verbatim; parameters carry the names those expressions read.
 */
internal fun moderationRuntimeParts(
    callFactory: Call.Factory,
    selected: RedditOAuthRuntimeSelection,
    coordinator: AuthAccountSessionCoordinator,
    moderationLogSelection: RedditOAuthRuntimeSelection?,
    moderationLogCapability: AuthCommentWriteCapability?,
    moderatorUserManagementSelection: RedditOAuthRuntimeSelection?,
    moderatorUserManagementCapability: AuthCommentWriteCapability?,
    moderatorTeamSelection: RedditOAuthRuntimeSelection?,
    moderatorTeamCapability: AuthCommentWriteCapability?,
    modmailReadSelection: RedditOAuthRuntimeSelection?,
    modmailReadCapability: AuthCommentWriteCapability?,
): ModerationRuntimeParts = ModerationRuntimeParts(
        redditModerationLoader = RedditModerationLoader(
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
            accountCoordinator = coordinator,
        ),
        redditModerationLogLoader =
            moderationLogSelection?.let { logSelection ->
                moderationLogCapability?.let { capability ->
                    RedditModerationLogLoader(
                        capability = capability,
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = logSelection.userAgent,
                    )
                }
            },
        redditBannedUsersReader =
            moderatorUserManagementSelection?.let { userSelection ->
                moderatorUserManagementCapability?.let { capability ->
                    RedditModeratorBannedUsersReader(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.sharedCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = userSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditModeratorUserWriter =
            moderatorUserManagementSelection?.let { userSelection ->
                moderatorUserManagementCapability?.let { capability ->
                    RedditModeratorUserWriter(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = userSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditMutedUsersReader =
            moderatorUserManagementSelection?.let { userSelection ->
                moderatorUserManagementCapability?.let { capability ->
                    app.subshell.prototype.data.auth.reddit.RedditModeratorMutedUsersReader(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.sharedCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = userSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditModeratorUserMuteWriter =
            moderatorUserManagementSelection?.let { userSelection ->
                moderatorUserManagementCapability?.let { capability ->
                    app.subshell.prototype.data.auth.reddit.RedditModeratorMuteWriter(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = userSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditApprovedUsersReader =
            moderatorUserManagementSelection?.let { userSelection ->
                moderatorUserManagementCapability?.let { capability ->
                    app.subshell.prototype.data.auth.reddit.RedditModeratorApprovedUsersReader(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.sharedCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = userSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditModeratorInviteWriter =
            moderatorTeamSelection?.let { teamSelection ->
                moderatorTeamCapability?.let { capability ->
                    app.subshell.prototype.data.auth.reddit.RedditModeratorInvitationWriter(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = teamSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditModeratorPermissionWriter =
            moderatorTeamSelection?.let { teamSelection ->
                moderatorTeamCapability?.let { capability ->
                    app.subshell.prototype.data.auth.reddit.RedditModeratorPermissionWriter(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = teamSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditModeratorRemovalWriter =
            moderatorTeamSelection?.let { teamSelection ->
                moderatorTeamCapability?.let { capability ->
                    app.subshell.prototype.data.auth.reddit.RedditModeratorRemovalWriter(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = teamSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditModeratorUserApprovalWriter =
            moderatorUserManagementSelection?.let { userSelection ->
                moderatorUserManagementCapability?.let { capability ->
                    app.subshell.prototype.data.auth.reddit.RedditModeratorApprovalWriter(
                        capability = capability,
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = userSelection.userAgent,
                        activeAccountStableId = { coordinator.activeAuthenticatedAccountId() },
                    )
                }
            },
        redditModmailLoader =
            modmailReadSelection?.let { modmailSelection ->
                modmailReadCapability?.let { capability ->
                    RedditModmailLoader(
                        capability = capability,
                        callFactory = callFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = modmailSelection.userAgent,
                    )
                }
            },
        redditModmailWriter =
            modmailReadSelection?.let { modmailSelection ->
                modmailReadCapability?.let { capability ->
                    ConfiguredRedditModmailWriter(
                        capability = capability,
                        // DATALAYER01: writes never go through the retrying client.
                        callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                        endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                        userAgent = modmailSelection.userAgent,
                    )
                }
            },
)
