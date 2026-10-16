package app.subshell.prototype.data.sync

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.cache.CachedPostPageWrite
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.network.NetworkPage
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.model.Post
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun interface PostFeedNetworkSource {
    suspend fun fetch(request: RepositoryPageRequest): NetworkResult<NetworkPage<Post>>
}

internal enum class PostFeedSyncPreconditionFailureKind {
    CURSOR_NOT_ALLOWED,
    REMOTE_CURSOR_UNAVAILABLE,
    REMOTE_CURSOR_MISMATCH,
}

internal sealed interface PostFeedSyncResult {
    data class Applied(
        val queryKey: RepositoryQueryKey,
        val operation: RepositoryLoadOperation,
        val networkItemCount: Int,
        val storedItemCount: Int,
        val generation: Int,
        val remoteNextCursor: String?,
        val terminal: Boolean,
        val receivedAtEpochMs: Long,
        val rateLimit: NetworkRateLimit,
    ) : PostFeedSyncResult

    data class PreconditionFailure(
        val kind: PostFeedSyncPreconditionFailureKind,
        val expectedRemoteCursor: String?,
        val requestedRemoteCursor: String?,
    ) : PostFeedSyncResult

    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val retryAfterMs: Long?,
        // ISSUE211_FORBIDDEN_REASON_IS_READ_V1: Reddit's reason for a 403.
        val providerReason: String? = null,
    ) : PostFeedSyncResult

    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : PostFeedSyncResult

    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : PostFeedSyncResult

    data object StorageFailure : PostFeedSyncResult
}

internal class OfflineFirstPostFeedSynchronizer(
    private val networkSource: PostFeedNetworkSource,
    private val cacheStore: RoomSubshellCacheStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // DEAD05_NO_PER_CALL_SYNC_MUTEX_V1 (OCB-642): every caller builds a new
    // synchronizer, so a per-instance mutex never ordered anything; ordering is
    // the store's job (CACHE01: the APPEND cursor is re-checked in the write
    // transaction).
    suspend fun synchronize(request: RepositoryPageRequest): PostFeedSyncResult =
        withContext(ioDispatcher) {
            val existingRemoteCursor =
                cacheStore.queryMetadata(request.queryKey)?.nextCursor
            validatePostFeedSyncPrecondition(
                request = request,
                existingRemoteCursor = existingRemoteCursor,
            )?.let { failure ->
                return@withContext failure
            }

            when (val networkResult = networkSource.fetch(request)) {
                is NetworkResult.Success -> applyNetworkPage(
                    request = request,
                    networkResult = networkResult,
                )
                is NetworkResult.HttpFailure -> PostFeedSyncResult.HttpFailure(
                    statusCode = networkResult.statusCode,
                    retryable = networkResult.retryable,
                    retryAfterMs = networkResult.metadata.retryAfterMs,
                    providerReason = networkResult.providerReason,
                )
                is NetworkResult.TransportFailure -> PostFeedSyncResult.TransportFailure(
                    kind = networkResult.kind,
                    retryable = networkResult.retryable,
                )
                is NetworkResult.ProtocolFailure -> PostFeedSyncResult.ProtocolFailure(
                    kind = networkResult.kind,
                    jsonPath = networkResult.jsonPath,
                )
            }
        }

    private fun applyNetworkPage(
        request: RepositoryPageRequest,
        networkResult: NetworkResult.Success<NetworkPage<Post>>,
    ): PostFeedSyncResult {
        // CACHE01_WRITE_RETURNS_STORED_METADATA_V1: the synchronizer is built
        // per call, so nothing here orders a bootstrap INITIAL against a feed
        // APPEND; the store re-checks the APPEND cursor in its write
        // transaction instead.
        val written = try {
            cacheStore.writeNetworkPostPage(
                request = request,
                page = networkResult.value,
                receivedAtEpochMs = networkResult.metadata.receivedAtEpochMs,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return PostFeedSyncResult.StorageFailure
        } ?: return PostFeedSyncResult.PreconditionFailure(
            kind = PostFeedSyncPreconditionFailureKind.REMOTE_CURSOR_MISMATCH,
            expectedRemoteCursor = runCatching { cacheStore.queryMetadata(request.queryKey)?.nextCursor }.getOrNull(),
            requestedRemoteCursor = request.cursor,
        )

        return written.toAppliedResult(
            request = request,
            networkItemCount = networkResult.value.items.size,
            receivedAtEpochMs = networkResult.metadata.receivedAtEpochMs,
            rateLimit = networkResult.metadata.rateLimit,
        )
    }
}

internal fun validatePostFeedSyncPrecondition(
    request: RepositoryPageRequest,
    existingRemoteCursor: String?,
): PostFeedSyncResult.PreconditionFailure? {
    val requestedCursor = request.cursor?.trim()?.takeIf(String::isNotEmpty)
    val expectedCursor = existingRemoteCursor?.trim()?.takeIf(String::isNotEmpty)

    return when (request.operation) {
        RepositoryLoadOperation.INITIAL,
        RepositoryLoadOperation.REFRESH,
        -> if (requestedCursor == null) {
            null
        } else {
            PostFeedSyncResult.PreconditionFailure(
                kind = PostFeedSyncPreconditionFailureKind.CURSOR_NOT_ALLOWED,
                expectedRemoteCursor = null,
                requestedRemoteCursor = requestedCursor,
            )
        }

        RepositoryLoadOperation.APPEND -> when {
            expectedCursor == null -> PostFeedSyncResult.PreconditionFailure(
                kind = PostFeedSyncPreconditionFailureKind.REMOTE_CURSOR_UNAVAILABLE,
                expectedRemoteCursor = null,
                requestedRemoteCursor = requestedCursor,
            )
            requestedCursor != expectedCursor -> PostFeedSyncResult.PreconditionFailure(
                kind = PostFeedSyncPreconditionFailureKind.REMOTE_CURSOR_MISMATCH,
                expectedRemoteCursor = expectedCursor,
                requestedRemoteCursor = requestedCursor,
            )
            else -> null
        }
    }
}

private fun CachedPostPageWrite.toAppliedResult(
    request: RepositoryPageRequest,
    networkItemCount: Int,
    receivedAtEpochMs: Long,
    rateLimit: NetworkRateLimit,
): PostFeedSyncResult.Applied = PostFeedSyncResult.Applied(
    queryKey = request.queryKey,
    operation = request.operation,
    networkItemCount = networkItemCount,
    storedItemCount = storedItemCount,
    generation = generation,
    remoteNextCursor = remoteNextCursor,
    terminal = terminal,
    receivedAtEpochMs = receivedAtEpochMs,
    rateLimit = rateLimit,
)
