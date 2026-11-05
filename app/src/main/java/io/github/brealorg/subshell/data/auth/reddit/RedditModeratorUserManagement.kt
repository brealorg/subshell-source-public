package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.auth.toFailureDetails
import io.github.brealorg.subshell.data.account.ModeratorUserManagementFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorUserApprovalRequest
import io.github.brealorg.subshell.data.account.RedditModeratorUserBanRequest
import io.github.brealorg.subshell.data.account.RedditModeratorUserMuteRequest
import io.github.brealorg.subshell.data.account.normalized
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditApprovedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditApprovedUsersResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditMutedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditMutedUsersResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserMutationResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserApprovalWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserMuteWriter
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditModeratorUserManagementNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditModeratorUserMutationNetworkResult
import io.github.brealorg.subshell.data.normalizeRedditCommunityName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call

// MODERATION02A5A1_OWNER_BOUND_BANNED_USERS_READER_V1
internal class RedditModeratorBannedUsersReader(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditBannedUsersReader {
    override suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String?,
        username: String?,
    ): AuthenticatedRedditBannedUsersResult = withContext(ioDispatcher) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }
            .getOrElse {
                return@withContext AuthenticatedRedditBannedUsersResult.Failed(
                    communityName = rawCommunityName.trim().lowercase(),
                    kind = ModeratorUserManagementFailureKind.VALIDATION,
                )
            }
        if (account.isEmpty() || activeAccountStableId()?.trim() != account) {
            return@withContext AuthenticatedRedditBannedUsersResult.Failed(
                communityName = community,
                kind = ModeratorUserManagementFailureKind.SESSION,
            )
        }

        when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> {
                if (activeAccountStableId()?.trim() != account) {
                    return@withContext AuthenticatedRedditBannedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.SESSION,
                    )
                }
                val client = SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                )
                when (
                    val result = RedditModeratorUserManagementNetworkDataSource(client)
                        .bannedUsersPage(community, after, username)
                ) {
                    is NetworkResult.Success -> AuthenticatedRedditBannedUsersResult.Loaded(
                        communityName = community,
                        users = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                    is NetworkResult.HttpFailure -> AuthenticatedRedditBannedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.HTTP,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                        httpStatusCode = result.statusCode,
                    )
                    is NetworkResult.TransportFailure -> AuthenticatedRedditBannedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                    is NetworkResult.ProtocolFailure -> AuthenticatedRedditBannedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
                }
            }
            is AuthSessionResult.AuthorizationDenied ->
                AuthenticatedRedditBannedUsersResult.AuthorizationRequired
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthenticatedRedditBannedUsersResult.Failed(
                communityName = community,
                kind = ModeratorUserManagementFailureKind.SESSION,
                retryable = true,
            )
            AuthSessionResult.ClientConfigurationMismatch ->
                AuthenticatedRedditBannedUsersResult.Failed(
                    communityName = community,
                    kind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                )
            is AuthSessionResult.EndpointFailure ->
                AuthenticatedRedditBannedUsersResult.Failed(
                    communityName = community,
                    kind = ModeratorUserManagementFailureKind.SESSION,
                    retryable = authorization.result.retryableForModeratorUsers(),
                    details = authorization.result.toFailureDetails(),
                )
        }
    }
}

// MODERATION02A5A2_OWNER_BOUND_MUTED_USERS_READER_V1
internal class RedditModeratorMutedUsersReader(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditMutedUsersReader {
    override suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String?,
        username: String?,
    ): AuthenticatedRedditMutedUsersResult = withContext(ioDispatcher) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }
            .getOrElse {
                return@withContext AuthenticatedRedditMutedUsersResult.Failed(
                    communityName = rawCommunityName.trim().lowercase(),
                    kind = ModeratorUserManagementFailureKind.VALIDATION,
                )
            }
        if (account.isEmpty() || activeAccountStableId()?.trim() != account) {
            return@withContext AuthenticatedRedditMutedUsersResult.Failed(
                communityName = community,
                kind = ModeratorUserManagementFailureKind.SESSION,
            )
        }

        when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> {
                if (activeAccountStableId()?.trim() != account) {
                    return@withContext AuthenticatedRedditMutedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.SESSION,
                    )
                }
                val client = SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                )
                when (
                    val result = RedditModeratorUserManagementNetworkDataSource(client)
                        .mutedUsersPage(community, after, username)
                ) {
                    is NetworkResult.Success -> AuthenticatedRedditMutedUsersResult.Loaded(
                        communityName = community,
                        users = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                    is NetworkResult.HttpFailure -> AuthenticatedRedditMutedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.HTTP,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                        httpStatusCode = result.statusCode,
                    )
                    is NetworkResult.TransportFailure -> AuthenticatedRedditMutedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                    )
                    is NetworkResult.ProtocolFailure -> AuthenticatedRedditMutedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.PROTOCOL,
                        details = result.failureDetailsOrNull(),
                    )
                }
            }
            is AuthSessionResult.AuthorizationDenied ->
                AuthenticatedRedditMutedUsersResult.AuthorizationRequired
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthenticatedRedditMutedUsersResult.Failed(
                communityName = community,
                kind = ModeratorUserManagementFailureKind.SESSION,
                retryable = true,
            )
            AuthSessionResult.ClientConfigurationMismatch ->
                AuthenticatedRedditMutedUsersResult.Failed(
                    communityName = community,
                    kind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                )
            is AuthSessionResult.EndpointFailure ->
                AuthenticatedRedditMutedUsersResult.Failed(
                    communityName = community,
                    kind = ModeratorUserManagementFailureKind.SESSION,
                    retryable = authorization.result.retryableForModeratorUsers(),
                    details = authorization.result.toFailureDetails(),
                )
        }
    }
}

// MODERATION02A5A3_OWNER_BOUND_APPROVED_USERS_READER_V1
internal class RedditModeratorApprovedUsersReader(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditApprovedUsersReader {
    override suspend fun load(
        accountStableId: String,
        rawCommunityName: String,
        after: String?,
        username: String?,
    ): AuthenticatedRedditApprovedUsersResult = withContext(ioDispatcher) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }
            .getOrElse {
                return@withContext AuthenticatedRedditApprovedUsersResult.Failed(
                    communityName = rawCommunityName.trim().lowercase(),
                    kind = ModeratorUserManagementFailureKind.VALIDATION,
                )
            }
        if (account.isEmpty() || activeAccountStableId()?.trim() != account) {
            return@withContext AuthenticatedRedditApprovedUsersResult.Failed(
                communityName = community,
                kind = ModeratorUserManagementFailureKind.SESSION,
            )
        }

        when (val authorization = capability.ensureAuthorized(account)) {
            is AuthSessionResult.Authenticated -> {
                if (activeAccountStableId()?.trim() != account) {
                    return@withContext AuthenticatedRedditApprovedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.SESSION,
                    )
                }
                val client = SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                )
                when (
                    val result = RedditModeratorUserManagementNetworkDataSource(client)
                        .approvedUsersPage(community, after, username)
                ) {
                    is NetworkResult.Success -> AuthenticatedRedditApprovedUsersResult.Loaded(
                        communityName = community,
                        users = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                    is NetworkResult.HttpFailure -> AuthenticatedRedditApprovedUsersResult.Failed(
                        communityName = community,
                        kind = ModeratorUserManagementFailureKind.HTTP,
                        retryable = result.retryable,
                        details = result.failureDetailsOrNull(),
                        httpStatusCode = result.statusCode,
                    )
                    is NetworkResult.TransportFailure ->
                        AuthenticatedRedditApprovedUsersResult.Failed(
                            communityName = community,
                            kind = ModeratorUserManagementFailureKind.TRANSPORT,
                            retryable = result.retryable,
                            details = result.failureDetailsOrNull(),
                        )
                    is NetworkResult.ProtocolFailure ->
                        AuthenticatedRedditApprovedUsersResult.Failed(
                            communityName = community,
                            kind = ModeratorUserManagementFailureKind.PROTOCOL,
                            details = result.failureDetailsOrNull(),
                        )
                }
            }
            is AuthSessionResult.AuthorizationDenied ->
                AuthenticatedRedditApprovedUsersResult.AuthorizationRequired
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthenticatedRedditApprovedUsersResult.Failed(
                communityName = community,
                kind = ModeratorUserManagementFailureKind.SESSION,
                retryable = true,
            )
            AuthSessionResult.ClientConfigurationMismatch ->
                AuthenticatedRedditApprovedUsersResult.Failed(
                    communityName = community,
                    kind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                )
            is AuthSessionResult.EndpointFailure ->
                AuthenticatedRedditApprovedUsersResult.Failed(
                    communityName = community,
                    kind = ModeratorUserManagementFailureKind.SESSION,
                    retryable = authorization.result.retryableForModeratorUsers(),
                    details = authorization.result.toFailureDetails(),
                )
        }
    }
}

// MODERATION02A5A1_NONRETRYING_BAN_UNBAN_WRITER_V1
internal class RedditModeratorUserWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModeratorUserWriter {
    override suspend fun mutate(
        request: RedditModeratorUserBanRequest,
    ): AuthenticatedRedditModeratorUserMutationResult = withContext(ioDispatcher) {
        val normalized = try {
            request.normalized()
        } catch (_: IllegalArgumentException) {
            return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.VALIDATION,
            )
        }
        if (activeAccountStableId()?.trim() != normalized.accountStableId) {
            return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.SESSION,
            )
        }

        when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
            is AuthSessionResult.Authenticated -> {
                if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                    return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                        kind = ModeratorUserManagementFailureKind.SESSION,
                    )
                }
                val client = SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                )
                when (
                    val result = RedditModeratorUserManagementNetworkDataSource(client)
                        .setBanned(normalized)
                ) {
                    RedditModeratorUserMutationNetworkResult.Acknowledged ->
                        AuthenticatedRedditModeratorUserMutationResult.Acknowledged
                    is RedditModeratorUserMutationNetworkResult.Failed ->
                        AuthenticatedRedditModeratorUserMutationResult.Failed(
                            kind = result.kind,
                            retryable = false,
                            details = result.failureDetails,
                            httpStatusCode = result.httpStatusCode,
                        )
                }
            }
            is AuthSessionResult.AuthorizationDenied ->
                AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.SESSION,
                retryable = true,
            )
            AuthSessionResult.ClientConfigurationMismatch ->
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    kind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                )
            is AuthSessionResult.EndpointFailure ->
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    kind = ModeratorUserManagementFailureKind.SESSION,
                    retryable = authorization.result.retryableForModeratorUsers(),
                    details = authorization.result.toFailureDetails(),
                )
        }
    }
}

// MODERATION02A5A2_NONRETRYING_MUTE_UNMUTE_WRITER_V1
internal class RedditModeratorMuteWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModeratorUserMuteWriter {
    override suspend fun mutate(
        request: RedditModeratorUserMuteRequest,
    ): AuthenticatedRedditModeratorUserMutationResult = withContext(ioDispatcher) {
        val normalized = try {
            request.normalized()
        } catch (_: IllegalArgumentException) {
            return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.VALIDATION,
            )
        }
        if (activeAccountStableId()?.trim() != normalized.accountStableId) {
            return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.SESSION,
            )
        }

        when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
            is AuthSessionResult.Authenticated -> {
                if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                    return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                        kind = ModeratorUserManagementFailureKind.SESSION,
                    )
                }
                val client = SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                )
                when (
                    val result = RedditModeratorUserManagementNetworkDataSource(client)
                        .setMuted(normalized)
                ) {
                    RedditModeratorUserMutationNetworkResult.Acknowledged ->
                        AuthenticatedRedditModeratorUserMutationResult.Acknowledged
                    is RedditModeratorUserMutationNetworkResult.Failed ->
                        AuthenticatedRedditModeratorUserMutationResult.Failed(
                            kind = result.kind,
                            retryable = false,
                            details = result.failureDetails,
                            httpStatusCode = result.httpStatusCode,
                        )
                }
            }
            is AuthSessionResult.AuthorizationDenied ->
                AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.SESSION,
                retryable = true,
            )
            AuthSessionResult.ClientConfigurationMismatch ->
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    kind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                )
            is AuthSessionResult.EndpointFailure ->
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    kind = ModeratorUserManagementFailureKind.SESSION,
                    retryable = authorization.result.retryableForModeratorUsers(),
                    details = authorization.result.toFailureDetails(),
                )
        }
    }
}

// MODERATION02A5A3_NONRETRYING_APPROVE_UNAPPROVE_WRITER_V1
internal class RedditModeratorApprovalWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val activeAccountStableId: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModeratorUserApprovalWriter {
    override suspend fun mutate(
        request: RedditModeratorUserApprovalRequest,
    ): AuthenticatedRedditModeratorUserMutationResult = withContext(ioDispatcher) {
        val normalized = try {
            request.normalized()
        } catch (_: IllegalArgumentException) {
            return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.VALIDATION,
            )
        }
        if (activeAccountStableId()?.trim() != normalized.accountStableId) {
            return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.SESSION,
            )
        }

        when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
            is AuthSessionResult.Authenticated -> {
                if (activeAccountStableId()?.trim() != normalized.accountStableId) {
                    return@withContext AuthenticatedRedditModeratorUserMutationResult.Failed(
                        kind = ModeratorUserManagementFailureKind.SESSION,
                    )
                }
                val client = SubshellHttpClient(
                    callFactory = callFactory,
                    endpoint = endpoint,
                    json = SubshellNetworkJson.providerJson,
                    userAgent = userAgent,
                    bearerTokenProvider = capability.bearerTokenProvider(authorization.session.tokens.accessToken),
                )
                when (
                    val result = RedditModeratorUserManagementNetworkDataSource(client)
                        .setApproved(normalized)
                ) {
                    RedditModeratorUserMutationNetworkResult.Acknowledged ->
                        AuthenticatedRedditModeratorUserMutationResult.Acknowledged
                    is RedditModeratorUserMutationNetworkResult.Failed ->
                        AuthenticatedRedditModeratorUserMutationResult.Failed(
                            kind = result.kind,
                            retryable = false,
                            details = result.failureDetails,
                            httpStatusCode = result.httpStatusCode,
                        )
                }
            }
            is AuthSessionResult.AuthorizationDenied ->
                AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> AuthenticatedRedditModeratorUserMutationResult.Failed(
                kind = ModeratorUserManagementFailureKind.SESSION,
                retryable = true,
            )
            AuthSessionResult.ClientConfigurationMismatch ->
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    kind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                )
            is AuthSessionResult.EndpointFailure ->
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    kind = ModeratorUserManagementFailureKind.SESSION,
                    retryable = authorization.result.retryableForModeratorUsers(),
                    details = authorization.result.toFailureDetails(),
                )
        }
    }
}

private fun OAuthEndpointResult<*>.retryableForModeratorUsers(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}
