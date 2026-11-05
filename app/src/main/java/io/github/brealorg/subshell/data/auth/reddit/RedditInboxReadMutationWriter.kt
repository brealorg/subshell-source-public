package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.auth.AuthInboxReadCapability
import io.github.brealorg.subshell.data.auth.AuthInboxReadMutationResult
import io.github.brealorg.subshell.data.auth.AuthInboxReadMutationWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditInboxFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditInboxSnapshot
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.isMissingIncrementalScopeAuthorization
import io.github.brealorg.subshell.data.auth.OAuthEndpointResult
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.redditWriteHttpFailureDetails
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditInboxReadMutationNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.redditReadTargetFullnameRequestBatches
import io.github.brealorg.subshell.data.network.reddit.redditReadTargetFullnames
import io.github.brealorg.subshell.model.InboxItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call

// MOD01A221_INBOX_MUTATION_SCOPE_AUTH_CONTINUITY_V1
internal class RedditInboxReadMutationWriter(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val cacheStore: RoomSubshellCacheStore,
    private val capability: AuthInboxReadCapability,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthInboxReadMutationWriter {
    private val mutationMutex = Mutex()

    // SUBSHELL_INBOXM3A2R2C1_LOCAL_SEEN_SERVER_FAILURE_STABILITY_WRITER_V1
    // Local seen records that the user opened the item; server read state is separate.
    override suspend fun markRead(
        accountStableId: String,
        item: InboxItem,
    ): AuthInboxReadMutationResult = withContext(ioDispatcher) {
        mutationMutex.withInboxAccountLock(accountStableId) {
            val account = accountStableId.trim().takeIf(String::isNotEmpty)
                ?: return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.PROTOCOL)
            val targets = runCatching { item.redditReadTargetFullnames() }.getOrElse {
                return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.PROTOCOL)
            }
            val targetBatches = runCatching {
                redditReadTargetFullnameRequestBatches(targets)
            }.getOrElse {
                return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.PROTOCOL)
            }
            if (targetBatches.isEmpty()) {
                return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.PROTOCOL)
            }
            cacheStore.markLiveInboxSeen(account, item.id)
            val session = when (val result = capability.ensureAuthorized(account)) {
                is AuthSessionResult.Authenticated -> result.session
                AuthSessionResult.ReauthenticationRequired -> {
                    return@withInboxAccountLock AuthInboxReadMutationResult.AuthorizationRequired
                }
                is AuthSessionResult.EndpointFailure -> {
                    return@withInboxAccountLock AuthInboxReadMutationResult.Failed(
                        kind = AuthenticatedRedditInboxFailureKind.SESSION,
                        retryable = result.result.retryable(),
                    )
                }
                AuthSessionResult.StorageFailure -> {
                    return@withInboxAccountLock failed(
                        AuthenticatedRedditInboxFailureKind.STORAGE,
                        retryable = true,
                    )
                }
                is AuthSessionResult.AuthorizationDenied -> {
                    return@withInboxAccountLock if (result.isMissingIncrementalScopeAuthorization()) {
                        AuthInboxReadMutationResult.AuthorizationRequired
                    } else {
                        failed(AuthenticatedRedditInboxFailureKind.SESSION)
                    }
                }
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ClientConfigurationMismatch,
                -> {
                    return@withInboxAccountLock failed(AuthenticatedRedditInboxFailureKind.SESSION)
                }
            }
            // SUBSHELL_INBOXM3A2R2C3_REDDIT_25_ID_READ_BATCHING_V1
            val mutationSource = source(session.tokens.accessToken)
            android.util.Log.d(
                "subshell-inbox-read-batch",
                "item=${item.id} targets=${targets.size} batches=${targetBatches.size} " +
                    "max_batch=${targetBatches.maxOfOrNull { batch -> batch.size } ?: 0}",
            )
            for ((batchIndex, batch) in targetBatches.withIndex()) {
                android.util.Log.d(
                    "subshell-inbox-read-batch",
                    "item=${item.id} batch=${batchIndex + 1}/${targetBatches.size} ids=${batch.size}",
                )
                when (val result = mutationSource.markRead(batch)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.HttpFailure -> {
                        return@withInboxAccountLock failed(
                            kind = AuthenticatedRedditInboxFailureKind.HTTP,
                            retryable = result.retryable,
                            details = redditWriteHttpFailureDetails(
                                statusCode = result.statusCode,
                                retryable = result.retryable,
                                metadata = result.metadata,
                                providerReason = result.providerReason,
                            ),
                        )
                    }
                    is NetworkResult.TransportFailure -> {
                        return@withInboxAccountLock failed(
                            AuthenticatedRedditInboxFailureKind.TRANSPORT,
                            result.retryable,
                            result.failureDetailsOrNull(),
                        )
                    }
                    is NetworkResult.ProtocolFailure -> {
                        return@withInboxAccountLock failed(
                            AuthenticatedRedditInboxFailureKind.PROTOCOL,
                            details = result.failureDetailsOrNull(),
                        )
                    }
                }
            }
            cacheStore.markLiveInboxItemServerRead(account, item.id)
            appliedSnapshot(account)
        }
    }

    private fun source(accessToken: String): RedditInboxReadMutationNetworkDataSource =
        RedditInboxReadMutationNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(accessToken),
            ),
        )

    private fun appliedSnapshot(accountStableId: String): AuthInboxReadMutationResult {
        val snapshot = cacheStore.liveInboxSnapshot(accountStableId)
            ?: return failed(AuthenticatedRedditInboxFailureKind.STORAGE, retryable = true)
        return AuthInboxReadMutationResult.Applied(
            AuthenticatedRedditInboxSnapshot(
                items = snapshot.items,
                nextCursor = snapshot.nextCursor,
                locallySeenIds = snapshot.locallySeenIds,
            ),
        )
    }

    private fun failed(
        kind: AuthenticatedRedditInboxFailureKind,
        retryable: Boolean = false,
        details: FailureDetails? = null,
    ): AuthInboxReadMutationResult.Failed = AuthInboxReadMutationResult.Failed(
        kind = kind,
        retryable = retryable,
        details = details,
    )
}

private fun OAuthEndpointResult<*>.retryable(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}
