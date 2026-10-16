package app.subshell.prototype.auth

import app.subshell.prototype.data.account.REDDIT_MODERATION_AGGREGATE_QUEUE
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditModQueueResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationTargetResult
import app.subshell.prototype.model.Post
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_MODERATIONQUEUE01_COORDINATOR_V1
/**
 * Moderator directory and mod-queue reads, the exact-target resolution,
 * and the optimistic remove / rollback / reconcile the
 * ModerationActionCoordinator reports back into the queue. Moved verbatim
 * out of AuthConnectViewModel; the ViewModel keeps one-line forwarders and
 * passes the three queue callbacks where it passed its own.
 * Characterised by AuthConnectViewModelModerationQueueTest.
 */
internal class ModerationQueueCoordinator(
    private val moderationLoader: AuthenticatedRedditModerationLoader?,
    private val scope: CoroutineScope,
) {
    private val _redditModerationUiState = MutableStateFlow(RedditModerationUiState())
    private var redditModeratorDirectoryJob: Job? = null
    private var redditModQueueJob: Job? = null
    private var redditModerationRollbackItem: Pair<Int, RedditModeratorQueueItem>? = null

    val redditModerationUiState: StateFlow<RedditModerationUiState> =
        _redditModerationUiState.asStateFlow()

    // MOD01A1_VIEWMODEL_MODERATOR_DIRECTORY_AND_MODQUEUE_V1
    fun loadRedditModeratorCommunities(
        accountStableId: String,
        forceRefresh: Boolean = false,
    ) {
        val current = _redditModerationUiState.value
        if (
            current.matchesAccount(accountStableId) &&
            (current.directoryLoading || (current.directoryLoaded && !forceRefresh))
        ) return

        val loader = moderationLoader
        if (loader == null) {
            _redditModerationUiState.value = RedditModerationUiState(
                accountStableId = accountStableId,
                directoryFailureKind = RedditModerationUiFailureKind.CLIENT_CONFIGURATION,
                directoryFailureDetails = configurationFailureDetails(),
            )
            return
        }

        redditModeratorDirectoryJob?.cancel()
        val previous = current.takeIf { it.matchesAccount(accountStableId) }
        _redditModerationUiState.value = RedditModerationUiState(
            accountStableId = accountStableId,
            moderatedCommunities = previous?.moderatedCommunities.orEmpty(),
            directoryLoading = true,
            directoryLoaded = previous?.directoryLoaded == true,
            queueCommunityName = previous?.queueCommunityName,
            queueItems = previous?.queueItems.orEmpty(),
            queueLoaded = previous?.queueLoaded == true,
            queueNextCursor = previous?.queueNextCursor,
            contentRevision = previous?.contentRevision ?: 0L,
        )
        redditModeratorDirectoryJob = scope.launch {
            val result = try {
                loader.loadModeratorCommunities(accountStableId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModeratedCommunitiesResult.Failed(
                    kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                    retryable = true,
                )
            }
            if (!_redditModerationUiState.value.matchesAccount(accountStableId)) return@launch
            _redditModerationUiState.value = when (result) {
                is AuthenticatedRedditModeratedCommunitiesResult.Loaded ->
                    _redditModerationUiState.value.copy(
                        moderatedCommunities = result.communityNames,
                        directoryLoading = false,
                        directoryLoaded = true,
                        directoryFailureKind = null,
                        directoryFailureRetryable = false,
                        directoryFailureDetails = null,
                        directoryFailureHttpStatusCode = null,
                        contentRevision = _redditModerationUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditModeratedCommunitiesResult.Failed ->
                    _redditModerationUiState.value.copy(
                        directoryLoading = false,
                        directoryFailureKind = result.kind.toModerationUiFailureKind(),
                        directoryFailureRetryable = result.retryable,
                        directoryFailureDetails = result.details,
                        directoryFailureHttpStatusCode =
                            result.details?.httpStatusCode ?: result.httpStatusCode,
                    )
            }
        }
    }

    fun loadRedditModQueue(
        accountStableId: String,
        rawCommunityName: String,
        forceRefresh: Boolean = false,
    ) {
        val communityName = try {
            app.subshell.prototype.data.normalizeRedditCommunityName(rawCommunityName)
        } catch (_: IllegalArgumentException) {
            _redditModerationUiState.value = _redditModerationUiState.value.copy(
                queueFailureKind = RedditModerationUiFailureKind.PROTOCOL,
            )
            return
        }
        val current = _redditModerationUiState.value
        // LOW11_FORCED_QUEUE_LOAD_REPLACES_A_RUNNING_ONE_V1 (OCB-426): the read
        // back after a moderator write (forced) replaces a load that started
        // before the write and could still show the acted-on item.
        if (
            current.queueMatches(accountStableId, communityName) &&
            !forceRefresh &&
            (current.queueLoading || current.queueLoaded)
        ) return
        val loader = moderationLoader ?: return

        redditModQueueJob?.cancel()
        val preserve = current.takeIf { it.queueMatches(accountStableId, communityName) }
        // LOW8_MOD_QUEUE_STARTS_CLEAN_FOR_ANOTHER_ACCOUNT_V1 (OCB-590): the
        // moderator directory of another account is not carried over.
        val base = current.takeIf { it.matchesAccount(accountStableId) }
            ?: RedditModerationUiState(accountStableId = accountStableId)
        _redditModerationUiState.value = base.copy(
            accountStableId = accountStableId,
            queueCommunityName = communityName,
            queueItems = preserve?.queueItems.orEmpty(),
            queueLoading = true,
            queueLoaded = preserve?.queueLoaded == true,
            queueNextCursor = preserve?.queueNextCursor,
            queueFailureKind = null,
            queueFailureRetryable = false,
            queueFailureDetails = null,
            queueFailureHttpStatusCode = null,
        )
        redditModQueueJob = scope.launch {
            val result = try {
                loader.loadModQueue(accountStableId, communityName, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModQueueResult.Failed(
                    communityName = communityName,
                    kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                    retryable = true,
                )
            }
            if (!_redditModerationUiState.value.queueMatches(accountStableId, communityName)) {
                return@launch
            }
            _redditModerationUiState.value = when (result) {
                is AuthenticatedRedditModQueueResult.Loaded ->
                    _redditModerationUiState.value.copy(
                        queueItems = result.items,
                        queueLoading = false,
                        queueLoaded = true,
                        queueNextCursor = result.nextCursor,
                        queueFailureKind = null,
                        queueFailureRetryable = false,
                        queueFailureDetails = null,
                        queueFailureHttpStatusCode = null,
                        contentRevision = _redditModerationUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditModQueueResult.Failed ->
                    _redditModerationUiState.value.copy(
                        queueLoading = false,
                        queueFailureKind = result.kind.toModerationUiFailureKind(),
                        queueFailureRetryable = result.retryable,
                        queueFailureDetails = result.details,
                        queueFailureHttpStatusCode =
                            result.details?.httpStatusCode ?: result.httpStatusCode,
                    )
            }
        }
    }

    // MOD01A22_VIEWMODEL_MODERATION_TARGET_RESOLUTION_V1
    fun resolveRedditModerationTarget(
        accountStableId: String,
        postId: String,
        onResolved: (Post?) -> Unit,
    ) {
        val account = accountStableId.trim()
        val targetPostId = postId
            .trim()
            .removePrefix("t3_")
            .lowercase()
            .takeIf(String::isNotEmpty)
        val loader = moderationLoader
        if (account.isEmpty() || targetPostId == null || loader == null) {
            onResolved(null)
            return
        }
        scope.launch {
            val result = try {
                loader.resolveTargetPost(account, targetPostId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModerationTargetResult.Failed(
                    kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                    retryable = true,
                )
            }
            // LOW8_MOD_TARGET_ANSWER_STAYS_WITH_ITS_ACCOUNT_V1 (OCB-425): an answer
            // that comes back after an account switch opens nothing (as the inbox).
            if (!_redditModerationUiState.value.matchesAccount(account)) {
                onResolved(null)
                return@launch
            }
            when (result) {
                is AuthenticatedRedditModerationTargetResult.Loaded ->
                    onResolved(result.post)
                AuthenticatedRedditModerationTargetResult.NotFound,
                is AuthenticatedRedditModerationTargetResult.Failed,
                -> onResolved(null)
            }
        }
    }

    fun appendRedditModQueue(
        accountStableId: String,
        rawCommunityName: String,
    ) {
        val communityName = runCatching {
            app.subshell.prototype.data.normalizeRedditCommunityName(rawCommunityName)
        }.getOrNull() ?: return
        val current = _redditModerationUiState.value
        val after = current.queueNextCursor ?: return
        if (!current.queueMatches(accountStableId, communityName) || current.queueLoading) return
        val loader = moderationLoader ?: return

        redditModQueueJob?.cancel()
        _redditModerationUiState.value = current.copy(
            queueLoading = true,
            queueFailureKind = null,
            queueFailureRetryable = false,
            queueFailureDetails = null,
            queueFailureHttpStatusCode = null,
        )
        redditModQueueJob = scope.launch {
            val result = try {
                loader.loadModQueue(accountStableId, communityName, after)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModQueueResult.Failed(
                    communityName = communityName,
                    kind = AuthenticatedRedditModerationFailureKind.TRANSPORT,
                    retryable = true,
                )
            }
            if (!_redditModerationUiState.value.queueMatches(accountStableId, communityName)) {
                return@launch
            }
            _redditModerationUiState.value = when (result) {
                is AuthenticatedRedditModQueueResult.Loaded ->
                    _redditModerationUiState.value.copy(
                        queueItems = (_redditModerationUiState.value.queueItems + result.items)
                            .distinctBy { item -> item.fullname },
                        queueLoading = false,
                        queueLoaded = true,
                        queueNextCursor = result.nextCursor,
                        queueFailureKind = null,
                        queueFailureRetryable = false,
                        queueFailureDetails = null,
                        queueFailureHttpStatusCode = null,
                        contentRevision = _redditModerationUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditModQueueResult.Failed ->
                    _redditModerationUiState.value.copy(
                        queueLoading = false,
                        queueFailureKind = result.kind.toModerationUiFailureKind(),
                        queueFailureRetryable = result.retryable,
                        queueFailureDetails = result.details,
                        queueFailureHttpStatusCode =
                            result.details?.httpStatusCode ?: result.httpStatusCode,
                    )
            }
        }
    }

    fun optimisticallyRemoveModerationQueueItem(
        request: RedditModeratorActionRequest,
    ) {
        val current = _redditModerationUiState.value
        if (!current.queueContainsContext(request.accountStableId, request.communityName)) return
        val index = current.queueItems.indexOfFirst { item ->
            item.fullname == request.thingFullname
        }
        if (index < 0) return
        redditModerationRollbackItem = index to current.queueItems[index]
        _redditModerationUiState.value = current.copy(
            queueItems = current.queueItems.filterNot { item ->
                item.fullname == request.thingFullname
            },
            contentRevision = current.contentRevision + 1L,
        )
    }

    fun rollbackModerationQueueItem(
        request: RedditModeratorActionRequest,
    ) {
        val rollback = redditModerationRollbackItem ?: return
        val current = _redditModerationUiState.value
        if (!current.queueContainsContext(request.accountStableId, request.communityName)) {
            redditModerationRollbackItem = null
            return
        }
        if (current.queueItems.any { item -> item.fullname == rollback.second.fullname }) {
            redditModerationRollbackItem = null
            return
        }
        val restored = current.queueItems.toMutableList()
        restored.add(rollback.first.coerceIn(0, restored.size), rollback.second)
        redditModerationRollbackItem = null
        _redditModerationUiState.value = current.copy(
            queueItems = restored,
            contentRevision = current.contentRevision + 1L,
        )
    }

    fun reconcileModerationQueue(
        request: RedditModeratorActionRequest,
    ) {
        redditModerationRollbackItem = null
        val current = _redditModerationUiState.value
        // LOW11 (OCB-426): a queue for another community that is shown now is
        // not replaced by the acted-on community's queue.
        val shown = current.queueCommunityName
        if (
            current.matchesAccount(request.accountStableId) &&
            shown != null &&
            shown != REDDIT_MODERATION_AGGREGATE_QUEUE &&
            !shown.equals(request.communityName, ignoreCase = true)
        ) {
            return
        }
        val queueScope = if (
            current.matchesAccount(request.accountStableId) &&
            current.queueCommunityName == REDDIT_MODERATION_AGGREGATE_QUEUE
        ) {
            REDDIT_MODERATION_AGGREGATE_QUEUE
        } else {
            request.communityName
        }
        loadRedditModQueue(
            accountStableId = request.accountStableId,
            rawCommunityName = queueScope,
            forceRefresh = true,
        )
    }
}

private fun AuthenticatedRedditModerationFailureKind.toModerationUiFailureKind():
    RedditModerationUiFailureKind = when (this) {
    AuthenticatedRedditModerationFailureKind.CLIENT_CONFIGURATION ->
        RedditModerationUiFailureKind.CLIENT_CONFIGURATION
    AuthenticatedRedditModerationFailureKind.SESSION ->
        RedditModerationUiFailureKind.SESSION
    AuthenticatedRedditModerationFailureKind.HTTP ->
        RedditModerationUiFailureKind.HTTP
    AuthenticatedRedditModerationFailureKind.TRANSPORT ->
        RedditModerationUiFailureKind.TRANSPORT
    AuthenticatedRedditModerationFailureKind.PROTOCOL ->
        RedditModerationUiFailureKind.PROTOCOL
}
