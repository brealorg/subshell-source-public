package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditContentSafetyThingKind
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.normalizedRedditContentReportRequest
import app.subshell.prototype.data.account.normalizedRedditPostVisibilityRequest
import app.subshell.prototype.data.account.normalizedRedditUserBlockRequest
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteProviderFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditContentSafetyNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditContentSafetyNetworkResult
import app.subshell.prototype.data.network.reddit.RedditBlockedAccountsNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditBlockedAccountsNetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

internal sealed interface AuthenticatedBlockedAccountsResult {
    data class Loaded(
        val accountStableId: String,
        val items: List<RedditBlockedAccount>,
        val nextCursor: String?,
    ) : AuthenticatedBlockedAccountsResult

    data object AuthorizationRequired : AuthenticatedBlockedAccountsResult

    data class Failed(
        val kind: ContentSafetyFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedBlockedAccountsResult
}

internal sealed interface AuthenticatedContentSafetyResult {
    data class Updated(
        val accountStableId: String,
        val thingFullname: String? = null,
        val username: String? = null,
        val hidden: Boolean? = null,
        val blocked: Boolean? = null,
        val hiddenHistoryReconciliation: HiddenHistoryReconciliation =
            HiddenHistoryReconciliation.NOT_APPLICABLE,
    ) : AuthenticatedContentSafetyResult

    data object AuthorizationRequired : AuthenticatedContentSafetyResult

    data class Failed(
        val kind: ContentSafetyFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedContentSafetyResult
}

internal interface AuthenticatedContentSafetyWriter {
    suspend fun setPostHidden(
        request: RedditPostVisibilityRequest,
    ): AuthenticatedContentSafetyResult

    suspend fun report(
        request: RedditContentReportRequest,
    ): AuthenticatedContentSafetyResult

    suspend fun setUserBlocked(
        request: RedditUserBlockRequest,
    ): AuthenticatedContentSafetyResult =
        error("User blocking is not implemented by this writer")

    suspend fun loadBlockedAccounts(
        accountStableId: String,
        after: String? = null,
    ): AuthenticatedBlockedAccountsResult =
        error("Blocked-account loading is not implemented by this writer")
}

// SAFETY01A2_OWNER_BOUND_REPORT_SCOPE_NONRETRYING_WRITER_V1
// USERACTION01A1_OWNER_BOUND_BLOCK_UNBLOCK_WRITER_V1
internal class ConfiguredRedditContentSafetyWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val hiddenHistoryUpdater: AuthenticatedRedditAccountHistoryUpdater?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedContentSafetyWriter {
    private val mutationMutex = Mutex()
    private val blockedAccountsMutex = Mutex()

    override suspend fun setPostHidden(
        request: RedditPostVisibilityRequest,
    ): AuthenticatedContentSafetyResult = withContext(ioDispatcher) {
        var reconcileAs: HiddenHistoryTarget? = null
        mutationMutex.withLock {
            val normalized = try {
                request.normalizedRedditPostVisibilityRequest()
            } catch (_: IllegalArgumentException) {
                return@withLock failed(ContentSafetyFailureKind.VALIDATION)
            }
            execute(
                accountStableId = normalized.accountStableId,
                duplicateProne = false,
                mutation = { source, _ ->
                    source.setPostHidden(normalized)
                },
                onAcknowledged = { username ->
                    reconcileAs = HiddenHistoryTarget(normalized.accountStableId, username)
                    AuthenticatedContentSafetyResult.Updated(
                        accountStableId = normalized.accountStableId,
                        thingFullname = normalized.postFullname,
                        hidden = normalized.hidden,
                    )
                },
            )
        }.withHiddenHistoryReconciled(reconcileAs)
    }

    override suspend fun report(
        request: RedditContentReportRequest,
    ): AuthenticatedContentSafetyResult = withContext(ioDispatcher) {
        var reconcileAs: HiddenHistoryTarget? = null
        mutationMutex.withLock {
            val normalized = try {
                request.normalizedRedditContentReportRequest()
            } catch (_: IllegalArgumentException) {
                return@withLock failed(ContentSafetyFailureKind.VALIDATION)
            }
            execute(
                accountStableId = normalized.accountStableId,
                duplicateProne = true,
                mutation = { source, _ ->
                    source.report(normalized)
                },
                onAcknowledged = { username ->
                    if (normalized.thingKind == RedditContentSafetyThingKind.POST) {
                        reconcileAs = HiddenHistoryTarget(normalized.accountStableId, username)
                    }
                    AuthenticatedContentSafetyResult.Updated(
                        accountStableId = normalized.accountStableId,
                        thingFullname = normalized.thingFullname,
                        hidden = if (
                            normalized.thingKind == RedditContentSafetyThingKind.POST
                        ) true else null,
                    )
                },
            )
        }.withHiddenHistoryReconciled(reconcileAs)
    }

    override suspend fun setUserBlocked(
        request: RedditUserBlockRequest,
    ): AuthenticatedContentSafetyResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val normalized = try {
                request.normalizedRedditUserBlockRequest()
            } catch (_: IllegalArgumentException) {
                return@withLock failed(ContentSafetyFailureKind.VALIDATION)
            }
            execute(
                accountStableId = normalized.accountStableId,
                duplicateProne = false,
                forbiddenRequiresAuthorization = false,
                mutation = { source, session ->
                    val identity = requireNotNull(session.identity) {
                        "Authenticated Reddit identity is required for user blocking"
                    }
                    require(
                        !identity.username.equals(normalized.username, ignoreCase = true),
                    ) {
                        "The active Reddit account cannot block itself"
                    }
                    source.setUserBlocked(
                        request = normalized,
                        currentUserFullname = identity.providerAccountId,
                    )
                },
                onAcknowledged = { _ ->
                    AuthenticatedContentSafetyResult.Updated(
                        accountStableId = normalized.accountStableId,
                        username = normalized.username,
                        blocked = normalized.blocked,
                    )
                },
            )
        }
    }

    // USERACTION01A2_OWNER_BOUND_BLOCKED_ACCOUNTS_READER_V1
    override suspend fun loadBlockedAccounts(
        accountStableId: String,
        after: String?,
    ): AuthenticatedBlockedAccountsResult = withContext(ioDispatcher) {
        blockedAccountsMutex.withLock {
            val normalizedAccountId = accountStableId.trim()
            if (normalizedAccountId.isEmpty()) {
                return@withLock AuthenticatedBlockedAccountsResult.Failed(
                    kind = ContentSafetyFailureKind.VALIDATION,
                )
            }
            val session = when (
                val authorization = capability.ensureAuthorized(normalizedAccountId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session

                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock AuthenticatedBlockedAccountsResult.AuthorizationRequired

                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock AuthenticatedBlockedAccountsResult.Failed(
                        ContentSafetyFailureKind.CONFIGURATION,
                    )

                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withLock AuthenticatedBlockedAccountsResult.Failed(
                    ContentSafetyFailureKind.SESSION,
                    retryable = true,
                )

                is AuthSessionResult.EndpointFailure ->
                    return@withLock AuthenticatedBlockedAccountsResult.Failed(
                        kind = ContentSafetyFailureKind.SESSION,
                        retryable = authorization.result.isRetryableEndpointFailure(),
                        failureDetails = authorization.result.toFailureDetails(),
                    )
            }

            val httpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = RedditBlockedAccountsNetworkDataSource(httpClient).load(after)
            ) {
                is RedditBlockedAccountsNetworkResult.Loaded ->
                    AuthenticatedBlockedAccountsResult.Loaded(
                        accountStableId = normalizedAccountId,
                        items = result.page.items,
                        nextCursor = result.page.nextCursor,
                    )

                is RedditBlockedAccountsNetworkResult.HttpFailure ->
                    if (result.statusCode == 401) {
                        AuthenticatedBlockedAccountsResult.AuthorizationRequired
                    } else {
                        AuthenticatedBlockedAccountsResult.Failed(
                            kind = ContentSafetyFailureKind.HTTP,
                            retryable = result.retryable,
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                statusCode = result.statusCode,
                                retryable = result.retryable,
                                metadata = result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )
                    }

                is RedditBlockedAccountsNetworkResult.TransportFailure ->
                    AuthenticatedBlockedAccountsResult.Failed(
                        kind = ContentSafetyFailureKind.TRANSPORT,
                        retryable = result.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            kind = result.kind,
                            retryable = result.retryable,
                        ),
                    )

                is RedditBlockedAccountsNetworkResult.ProtocolFailure ->
                    AuthenticatedBlockedAccountsResult.Failed(
                        kind = ContentSafetyFailureKind.PROTOCOL,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )

                RedditBlockedAccountsNetworkResult.MalformedPayload ->
                    AuthenticatedBlockedAccountsResult.Failed(
                        ContentSafetyFailureKind.PROTOCOL,
                    )
            }
        }
    }

    private suspend fun execute(
        accountStableId: String,
        duplicateProne: Boolean,
        forbiddenRequiresAuthorization: Boolean = true,
        mutation: suspend (
            RedditContentSafetyNetworkDataSource,
            StoredOAuthSession,
        ) -> RedditContentSafetyNetworkResult,
        onAcknowledged: suspend (String?) ->
            AuthenticatedContentSafetyResult.Updated,
    ): AuthenticatedContentSafetyResult {
        val session = when (
            val authorization = capability.ensureAuthorized(accountStableId)
        ) {
            is AuthSessionResult.Authenticated -> authorization.session

            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> return AuthenticatedContentSafetyResult.AuthorizationRequired

            AuthSessionResult.ClientConfigurationMismatch ->
                return failed(ContentSafetyFailureKind.CONFIGURATION)

            AuthSessionResult.StorageFailure ->
                return failed(
                    ContentSafetyFailureKind.SESSION,
                    retryable = true,
                )

            AuthSessionResult.NoPendingCallback ->
                return failed(ContentSafetyFailureKind.SESSION)

            is AuthSessionResult.EndpointFailure ->
                return failed(
                    ContentSafetyFailureKind.SESSION,
                    retryable = authorization.result.isRetryableEndpointFailure(),
                    failureDetails = authorization.result.toFailureDetails(),
                )
        }

        val httpClient = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = userAgent,
            bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
        )
        val networkResult = try {
            mutation(RedditContentSafetyNetworkDataSource(httpClient), session)
        } catch (_: IllegalArgumentException) {
            return failed(ContentSafetyFailureKind.VALIDATION)
        }
        return when (val result = networkResult) {
            is RedditContentSafetyNetworkResult.Acknowledged ->
                onAcknowledged(
                    session.identity
                        ?.username
                        ?.trim()
                        ?.takeIf(String::isNotEmpty),
                )

            is RedditContentSafetyNetworkResult.ApiFailure -> {
                val first = result.errors.firstOrNull()
                failed(
                    kind = ContentSafetyFailureKind.API,
                    failureDetails = redditWriteProviderFailureDetails(
                        code = first?.code,
                        message = first?.message,
                        retryable = false,
                    ),
                )
            }

            is RedditContentSafetyNetworkResult.HttpFailure ->
                when {
                    result.statusCode == 401 ||
                        (result.statusCode == 403 && forbiddenRequiresAuthorization) ->
                        AuthenticatedContentSafetyResult.AuthorizationRequired

                    result.statusCode == 403 ->
                        failed(
                            kind = ContentSafetyFailureKind.PROVIDER_RESTRICTION,
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                statusCode = result.statusCode,
                                retryable = false,
                                metadata = result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )

                    result.statusCode == 429 ->
                        failed(
                            kind = ContentSafetyFailureKind.HTTP,
                            retryable = true,
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                statusCode = result.statusCode,
                                retryable = true,
                                metadata = result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )

                    duplicateProne &&
                        (
                            result.statusCode == 408 ||
                                result.statusCode == 421 ||
                                result.statusCode == 425 ||
                                result.statusCode in 500..599
                        ) ->
                        failed(
                            kind = ContentSafetyFailureKind.OUTCOME_UNKNOWN,
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                statusCode = result.statusCode,
                                retryable = false,
                                metadata = result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )

                    else ->
                        failed(
                            kind = ContentSafetyFailureKind.HTTP,
                            retryable = if (duplicateProne) {
                                false
                            } else {
                                result.retryable
                            },
                            statusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                statusCode = result.statusCode,
                                retryable = if (duplicateProne) {
                                    false
                                } else {
                                    result.retryable
                                },
                                metadata = result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )
                }

            is RedditContentSafetyNetworkResult.TransportFailure ->
                mapTransportFailure(
                    result = result,
                    duplicateProne = duplicateProne,
                )

            is RedditContentSafetyNetworkResult.ProtocolFailure ->
                if (duplicateProne) {
                    failed(
                        ContentSafetyFailureKind.OUTCOME_UNKNOWN,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
                } else {
                    failed(
                        ContentSafetyFailureKind.PROTOCOL,
                        failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
                }

            RedditContentSafetyNetworkResult.MalformedPayload ->
                failed(ContentSafetyFailureKind.OUTCOME_UNKNOWN)
        }
    }

    private fun mapTransportFailure(
        result: RedditContentSafetyNetworkResult.TransportFailure,
        duplicateProne: Boolean,
    ): AuthenticatedContentSafetyResult.Failed =
        when (result.kind) {
            NetworkTransportFailureKind.DNS,
            NetworkTransportFailureKind.CONNECT,
            -> failed(
                kind = ContentSafetyFailureKind.TRANSPORT,
                retryable = true,
                failureDetails = redditWriteTransportFailureDetails(
                    kind = result.kind,
                    retryable = true,
                ),
            )

            NetworkTransportFailureKind.TLS ->
                failed(
                    kind = ContentSafetyFailureKind.TRANSPORT,
                    failureDetails = redditWriteTransportFailureDetails(
                        kind = result.kind,
                        retryable = false,
                    ),
                )

            NetworkTransportFailureKind.TIMEOUT,
            NetworkTransportFailureKind.IO,
            -> if (duplicateProne) {
                failed(
                    kind = ContentSafetyFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = redditWriteTransportFailureDetails(
                        kind = result.kind,
                        retryable = false,
                    ),
                )
            } else {
                failed(
                    kind = ContentSafetyFailureKind.TRANSPORT,
                    retryable = true,
                    failureDetails = redditWriteTransportFailureDetails(
                        kind = result.kind,
                        retryable = true,
                    ),
                )
            }
        }

    // LOW3_HIDDEN_HISTORY_REFRESH_OUTSIDE_WRITE_LOCK_V1 (OCB-498): the hidden
    // history page is read after mutationMutex is released, so the next hide,
    // report or block does not wait for this refresh.
    private class HiddenHistoryTarget(val accountStableId: String, val username: String?)

    private suspend fun AuthenticatedContentSafetyResult.withHiddenHistoryReconciled(
        target: HiddenHistoryTarget?,
    ): AuthenticatedContentSafetyResult =
        if (this is AuthenticatedContentSafetyResult.Updated && target != null) {
            copy(
                hiddenHistoryReconciliation = reconcileHiddenHistory(
                    accountStableId = target.accountStableId,
                    username = target.username,
                ),
            )
        } else {
            this
        }

    private suspend fun reconcileHiddenHistory(
        accountStableId: String,
        username: String?,
    ): HiddenHistoryReconciliation {
        val updater = hiddenHistoryUpdater
            ?: return HiddenHistoryReconciliation.UNAVAILABLE
        val normalizedUsername = username
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return HiddenHistoryReconciliation.UNAVAILABLE
        return when (
            updater.synchronize(
                accountStableId = accountStableId,
                username = normalizedUsername,
                kind = RedditAccountHistoryKind.HIDDEN,
                operation = RepositoryLoadOperation.REFRESH,
            )
        ) {
            is AuthenticatedRedditAccountHistoryUpdateResult.Applied ->
                HiddenHistoryReconciliation.REFRESHED

            AuthenticatedRedditAccountHistoryUpdateResult.AuthorizationRequired ->
                HiddenHistoryReconciliation.AUTHORIZATION_REQUIRED

            is AuthenticatedRedditAccountHistoryUpdateResult.Failed ->
                HiddenHistoryReconciliation.FAILED
        }
    }

    private fun failed(
        kind: ContentSafetyFailureKind,
        retryable: Boolean = false,
        statusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ): AuthenticatedContentSafetyResult.Failed =
        AuthenticatedContentSafetyResult.Failed(
            kind = kind,
            retryable = retryable,
            statusCode = statusCode,
            failureDetails = failureDetails,
        )
}

private fun OAuthEndpointResult<*>.isRetryableEndpointFailure(): Boolean =
    when (this) {
        is OAuthEndpointResult.HttpFailure -> retryable
        is OAuthEndpointResult.TransportFailure -> retryable
        is OAuthEndpointResult.ProtocolFailure -> false
        is OAuthEndpointResult.Success -> false
    }
