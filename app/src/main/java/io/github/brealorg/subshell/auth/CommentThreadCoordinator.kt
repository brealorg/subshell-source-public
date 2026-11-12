package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.model.reannotateRedditMedia
import io.github.brealorg.subshell.data.account.CommentDeleteRequest
import io.github.brealorg.subshell.data.account.CommentEditRequest
import io.github.brealorg.subshell.data.account.CommentWriteRequest
import io.github.brealorg.subshell.data.account.RedditCommentUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.data.account.beginSortLoad
import io.github.brealorg.subshell.data.account.normalizeCommentEditFullname
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditFocusedCommentContinuityLoader
import io.github.brealorg.subshell.data.network.reddit.focusedCommentContextForTarget
import io.github.brealorg.subshell.data.network.reddit.mergeFocusedCommentContext
import io.github.brealorg.subshell.data.retainRedditSuggestedCommentSort
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// SUBSHELL_COMMENTTHREAD01_COORDINATOR_V1
/**
 * Comment-thread reads for the post screen: sort loads, the cached
 * first paint, focused-comment context, "load more" and full-discussion
 * hydration, plus the local edits the write coordinators report back
 * (edited body, deleted comment, submitted thread). Moved verbatim out of
 * AuthConnectViewModel; the ViewModel keeps one-line forwarders and owns
 * the callback routing that calls [applyEditedComment] and friends.
 */
internal class CommentThreadCoordinator(
    private val commentLoader: AuthenticatedRedditCommentLoader?,
    private val scope: CoroutineScope,
) {
    private val _redditCommentsUiState = MutableStateFlow(RedditCommentsUiState())
    private var redditCommentJob: Job? = null
    private var redditCommentMoreJob: Job? = null

    val redditCommentsUiState: StateFlow<RedditCommentsUiState> =
        _redditCommentsUiState.asStateFlow()

    // COMMENTSSOT01_THE_THREAD_FOLLOWS_ITS_CACHE_V1 (#244): the stored thread is
    // the source of truth (Google's offline-first pattern, as INBOXSSOT01).
    // The screen's state follows the stored thread of the account, post and
    // sort last loaded, so whatever reaches the cache (a refresh, "load more",
    // a posted reply, an edit, a delete) is what the thread shows.
    private var followedThread: Triple<String, String, RedditCommentSort>? = null
    private var followStoredThreadJob: Job? = null

    private fun followStoredThread(
        loader: AuthenticatedRedditCommentLoader,
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ) {
        val thread = Triple(accountStableId, postId, sort)
        if (followedThread == thread && followStoredThreadJob?.isActive == true) return
        followStoredThreadJob?.cancel()
        followedThread = thread
        followStoredThreadJob = scope.launch {
            try {
                loader.observeStoredThread(accountStableId, postId, sort).collect { stored ->
                    applyStoredThread(thread, stored)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed read leaves the thread as shown; the next load
                // follows the store again.
                followedThread = null
            }
        }
    }

    private fun stopFollowingStoredThread() {
        followStoredThreadJob?.cancel()
        followStoredThreadJob = null
        followedThread = null
    }

    private fun applyStoredThread(
        thread: Triple<String, String, RedditCommentSort>,
        stored: List<io.github.brealorg.subshell.model.Comment>,
    ) {
        val current = _redditCommentsUiState.value
        // Empty is a thread not stored (not read yet, or evicted to keep the
        // cache small), never a reason to blank the thread on screen.
        if (stored.isEmpty() || !current.matches(thread.first, thread.second, thread.third)) return
        if (current.comments == stored) return
        _redditCommentsUiState.value = current.copy(
            comments = stored,
            fullDiscussionLoaded = true,
            contentRevision = current.contentRevision + 1L,
        )
    }

    // COMMENTSSOT01_LOCAL_WRITES_REACH_THE_CACHE_V1: a confirmed edit, delete
    // or moderation is written to every stored sort of the thread, not only
    // to the thread on screen.
    private fun updateStoredThread(
        accountStableId: String,
        postId: String,
        transform: (List<io.github.brealorg.subshell.model.Comment>) -> List<io.github.brealorg.subshell.model.Comment>?,
    ) {
        val loader = commentLoader ?: return
        scope.launch {
            try {
                loader.updateStoredThread(accountStableId, postId, transform)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The screen already shows the change; the next read rebinds.
            }
        }
    }

    /** ViewModel.onCleared: stop in-flight thread loads. */
    fun cancelPending() {
        redditCommentJob?.cancel()
        redditCommentMoreJob?.cancel()
        followStoredThreadJob?.cancel()
    }

    fun loadRedditComments(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ) {
        val loader = commentLoader
        if (loader == null) {
            _redditCommentsUiState.value = RedditCommentsUiState(
                accountStableId = accountStableId,
                postId = postId,
                sort = sort,
                failureKind = RedditCommentUiFailureKind.CLIENT_CONFIGURATION,
                failureDetails = configurationFailureDetails(),
            )
            return
        }
        if (
            _redditCommentsUiState.value.matches(accountStableId, postId, sort) &&
            _redditCommentsUiState.value.loading
        ) {
            return
        }
        followStoredThread(loader, accountStableId, postId, sort)
        redditCommentJob?.cancel()
        redditCommentMoreJob?.cancel()
        _redditCommentsUiState.value =
            _redditCommentsUiState.value
                .beginSortLoad(accountStableId, postId, sort)
                .copy(fullDiscussionLoading = true)
        authDebugLog(COMMENT_LOAD_LOG_TAG, "start post=$postId sort=${sort.apiValue}")
        redditCommentJob = scope.launch {
            val cached = try {
                loader.cached(accountStableId, postId, sort)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            if (_redditCommentsUiState.value.matches(accountStableId, postId, sort)) {
                when (cached) {
                    is AuthenticatedRedditCommentResult.Loaded -> {
                        if (cached.comments.isNotEmpty()) {
                            authDebugLog(
                                COMMENT_LOAD_LOG_TAG,
                                "cache post=$postId sort=${sort.apiValue} " +
                                    "count=${cached.comments.size}",
                            )
                            _redditCommentsUiState.value =
                                _redditCommentsUiState.value.copy(
                                    comments = cached.comments,
                                    suggestedSort = cached.suggestedSort
                                        ?: _redditCommentsUiState.value.suggestedSort,
                                    fromCache = true,
                                    fullDiscussionLoaded = true,
                                    contentRevision =
                                        _redditCommentsUiState.value.contentRevision + 1L,
                                )
                        }
                    }
                    is AuthenticatedRedditCommentResult.Failed -> Unit
                }
            }

            val refreshed = try {
                loader.synchronize(accountStableId, postId, sort)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            if (!_redditCommentsUiState.value.matches(accountStableId, postId, sort)) {
                return@launch
            }
            _redditCommentsUiState.value = when (refreshed) {
                is AuthenticatedRedditCommentResult.Loaded ->
                    _redditCommentsUiState.value.copy(
                        comments = refreshed.comments,
                        suggestedSort = retainRedditSuggestedCommentSort(
                            previous = _redditCommentsUiState.value.suggestedSort,
                            refreshed = refreshed.suggestedSort,
                        ),
                        loading = false,
                        fullDiscussionLoading = false,
                        fullDiscussionLoaded = true,
                        fromCache = false,
                        authorFullnames =
                            _redditCommentsUiState.value.authorFullnames + refreshed.authorFullnames,
                        failureKind = null,
                        failureRetryable = false,
                        contentRevision =
                            _redditCommentsUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditCommentResult.Failed ->
                    _redditCommentsUiState.value.copy(
                        loading = false,
                        fullDiscussionLoading = false,
                        failureKind = refreshed.kind.toUiFailureKind(),
                        failureRetryable = refreshed.retryable,
                        // COMMENTDIAG01_FAILURE_DETAILS_REACH_THE_REPORT_V1
                        failureDetails = refreshed.details,
                    )
            }.also { completed ->
                authDebugLog(
                    COMMENT_LOAD_LOG_TAG,
                    "finish post=$postId sort=${sort.apiValue} " +
                        "loading=${completed.loading} failure=${completed.failureKind} " +
                        "count=${completed.comments.size} " +
                        "suggested=${completed.suggestedSort?.apiValue} " +
                        commentTreeStats(completed.comments),
                )
            }
        }
    }
    fun loadCachedRedditComments(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ) {
        val loader = commentLoader
        if (loader == null) {
            _redditCommentsUiState.value = RedditCommentsUiState(
                accountStableId = accountStableId,
                postId = postId,
                sort = sort,
                failureKind = RedditCommentUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }
        followStoredThread(loader, accountStableId, postId, sort)
        redditCommentJob?.cancel()
        redditCommentMoreJob?.cancel()
        _redditCommentsUiState.value =
            _redditCommentsUiState.value.beginSortLoad(accountStableId, postId, sort)
        authDebugLog(
            COMMENT_LOAD_LOG_TAG,
            "cache-start post=$postId sort=${sort.apiValue}",
        )
        redditCommentJob = scope.launch {
            val cached = try {
                loader.cached(accountStableId, postId, sort)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                )
            }
            if (!_redditCommentsUiState.value.matches(accountStableId, postId, sort)) {
                return@launch
            }
            _redditCommentsUiState.value = when (cached) {
                is AuthenticatedRedditCommentResult.Loaded ->
                    _redditCommentsUiState.value.copy(
                        comments = cached.comments,
                        suggestedSort = cached.suggestedSort
                            ?: _redditCommentsUiState.value.suggestedSort,
                        loading = false,
                        fullDiscussionLoading = false,
                        fullDiscussionLoaded = cached.comments.isNotEmpty(),
                        fromCache = true,
                        failureKind = null,
                        failureRetryable = false,
                        contentRevision =
                            _redditCommentsUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditCommentResult.Failed ->
                    _redditCommentsUiState.value.copy(
                        loading = false,
                        fullDiscussionLoading = false,
                        failureKind = cached.kind.toUiFailureKind(),
                        failureRetryable = cached.retryable,
                        // COMMENTDIAG01_FAILURE_DETAILS_REACH_THE_REPORT_V1
                        failureDetails = cached.details,
                    )
            }.also { completed ->
                authDebugLog(
                    COMMENT_LOAD_LOG_TAG,
                    "cache-finish post=$postId sort=${sort.apiValue} " +
                        "loading=${completed.loading} failure=${completed.failureKind} " +
                        "count=${completed.comments.size}",
                )
            }
        }
    }
    fun refreshRedditCommentsPreservingFocusedTarget(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
    ) {
        val targetId = focusedCommentId
            .trim()
            .takeIf(String::isNotEmpty)
            ?: return
        val loader = commentLoader
        if (loader == null) {
            _redditCommentsUiState.value = RedditCommentsUiState(
                accountStableId = accountStableId,
                postId = postId,
                sort = sort,
                failureKind = RedditCommentUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }
        val current = _redditCommentsUiState.value
        val currentComments = if (current.matches(accountStableId, postId, sort)) {
            current.comments
        } else {
            emptyList()
        }
        val focusedContext = focusedCommentContextForTarget(currentComments, targetId)
        val continuityLoader =
            loader as? AuthenticatedRedditFocusedCommentContinuityLoader
        // AUDIT298A_FOCUSED_REFRESH_DOES_NOT_SHOW_THE_STORED_THREAD_V1: the
        // stored thread may not hold the linked comment; following it at once
        // put that thread on screen (Room emits it immediately) and the
        // landing reported the comment missing. The refresh stores the thread
        // with the comment; the store is followed once that is done.
        stopFollowingStoredThread()
        redditCommentJob?.cancel()
        // AUDIT298A_LOCAL_WRITES_AND_REFRESHES_STOP_MORE_LOADS_V1: a more load
        // answering after the refresh merged into the thread before it.
        redditCommentMoreJob?.cancel()
        _redditCommentsUiState.value =
            _redditCommentsUiState.value
                .beginSortLoad(accountStableId, postId, sort)
                .copy(fullDiscussionLoading = true)
        authDebugLog(
            COMMENT_LOAD_LOG_TAG,
            "refresh-preserve-start post=$postId sort=${sort.apiValue} target=$targetId " +
                "context=${focusedContext.isNotEmpty()} owner=${if (continuityLoader != null) "loader" else "viewmodel"}",
        )
        redditCommentJob = scope.launch {
            val refreshed = try {
                if (continuityLoader != null) {
                    continuityLoader.synchronizePreservingFocusedTarget(
                        accountStableId = accountStableId,
                        postId = postId,
                        sort = sort,
                        focusedCommentId = targetId,
                        currentComments = currentComments,
                    )
                } else {
                    loader.synchronize(accountStableId, postId, sort)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                )
            }
            if (!_redditCommentsUiState.value.matches(accountStableId, postId, sort)) {
                return@launch
            }
            _redditCommentsUiState.value = when (refreshed) {
                is AuthenticatedRedditCommentResult.Loaded ->
                    _redditCommentsUiState.value.copy(
                        comments = if (continuityLoader != null) {
                            followStoredThread(loader, accountStableId, postId, sort)
                            refreshed.comments
                        } else {
                            mergeFocusedCommentContext(
                                current = refreshed.comments,
                                focused = focusedContext,
                                focusedCommentId = targetId,
                            )
                        },
                        suggestedSort = retainRedditSuggestedCommentSort(
                            previous = _redditCommentsUiState.value.suggestedSort,
                            refreshed = refreshed.suggestedSort,
                        ),
                        loading = false,
                        fullDiscussionLoading = false,
                        fullDiscussionLoaded = true,
                        fromCache = false,
                        authorFullnames =
                            _redditCommentsUiState.value.authorFullnames + refreshed.authorFullnames,
                        failureKind = null,
                        failureRetryable = false,
                        contentRevision =
                            _redditCommentsUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditCommentResult.Failed ->
                    _redditCommentsUiState.value.copy(
                        loading = false,
                        fullDiscussionLoading = false,
                        failureKind = refreshed.kind.toUiFailureKind(),
                        failureRetryable = refreshed.retryable,
                        // COMMENTDIAG01_FAILURE_DETAILS_REACH_THE_REPORT_V1
                        failureDetails = refreshed.details,
                    )
            }.also { completed ->
                authDebugLog(
                    COMMENT_LOAD_LOG_TAG,
                    "refresh-preserve-finish post=$postId sort=${sort.apiValue} " +
                        "target=$targetId loading=${completed.loading} " +
                        "failure=${completed.failureKind} count=${completed.comments.size}",
                )
            }
        }
    }
    fun loadFocusedRedditComment(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        focusedCommentId: String,
    ) {
        val requestedFocusedCommentId =
            focusedCommentId
                .trim()
                .takeIf(String::isNotEmpty)
                ?: return

        val loader = commentLoader
        if (loader == null) {
            _redditCommentsUiState.value = RedditCommentsUiState(
                accountStableId = accountStableId,
                postId = postId,
                sort = sort,
                failureKind = RedditCommentUiFailureKind.CLIENT_CONFIGURATION,
            )
            return
        }
        // COMMENTSSOT01_A_FOCUSED_VIEW_IS_NOT_THE_STORED_THREAD_V1: a comment
        // link shows the stored thread with that comment's fresh context,
        // which is never stored as the discussion (profile01a5); the stored
        // thread is not followed until the discussion is loaded again, or a
        // write there would take the context off the screen.
        stopFollowingStoredThread()
        redditCommentJob?.cancel()
        redditCommentMoreJob?.cancel()
        _redditCommentsUiState.value =
            _redditCommentsUiState.value.beginSortLoad(accountStableId, postId, sort)
        authDebugLog(COMMENT_LOAD_LOG_TAG, "start post=$postId sort=${sort.apiValue}")
        redditCommentJob = scope.launch {
            val refreshed = try {
                loader.synchronizeFocused(
                    accountStableId = accountStableId,
                    postId = postId,
                    sort = sort,
                    focusedCommentId =
                        requestedFocusedCommentId,
                    context = 8,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                )
            }
            if (!_redditCommentsUiState.value.matches(accountStableId, postId, sort)) {
                return@launch
            }
            _redditCommentsUiState.value = when (refreshed) {
                is AuthenticatedRedditCommentResult.Loaded ->
                    _redditCommentsUiState.value.copy(
                        comments = refreshed.comments,
                        suggestedSort = retainRedditSuggestedCommentSort(
                            previous = _redditCommentsUiState.value.suggestedSort,
                            refreshed = refreshed.suggestedSort,
                        ),
                        loading = false,
                        fromCache = false,
                        authorFullnames =
                            _redditCommentsUiState.value.authorFullnames + refreshed.authorFullnames,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        contentRevision =
                            _redditCommentsUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditCommentResult.Failed ->
                    _redditCommentsUiState.value.copy(
                        loading = false,
                        failureKind = refreshed.kind.toUiFailureKind(),
                        failureRetryable = refreshed.retryable,
                        failureDetails = refreshed.details,
                    )
            }.also { completed ->
                authDebugLog(
                    COMMENT_LOAD_LOG_TAG,
                    "finish post=$postId sort=${sort.apiValue} " +
                        "loading=${completed.loading} failure=${completed.failureKind} " +
                        "count=${completed.comments.size} " +
                        "suggested=${completed.suggestedSort?.apiValue} " +
                        commentTreeStats(completed.comments),
                )
            }
        }
    }
    // OCB581A_LOAD_MORE_SAYS_WHETHER_IT_STARTED_V1 (OCB-581): false means
    // the request was refused and nothing changed (another thread or sort, the
    // discussion is not loaded or still loading, another node is loading), so
    // a caller waiting for the state to move knows it will not.
    fun loadMoreRedditComments(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
        // ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1
        fromEnd: Boolean = false,
        // AUDIT298C_SMALL_NODES_LOAD_TOGETHER_V1: more nodes loaded in the same
        // round after [moreNodeId] (from their start), published once.
        alsoNodeIds: List<String> = emptyList(),
    ): Boolean {
        val loader = commentLoader ?: return false
        val current = _redditCommentsUiState.value
        if (
            !current.matches(accountStableId, postId, sort) ||
            !current.fullDiscussionLoaded ||
            current.fullDiscussionLoading ||
            current.loadingMoreNodeId != null
        ) {
            return false
        }
        _redditCommentsUiState.value = current.copy(
            loadingMoreNodeId = moreNodeId,
            failedMoreNodeId = null,
            failureKind = null,
            failureRetryable = false,
            failureDetails = null,
        )
        authDebugLog(
            COMMENT_LOAD_LOG_TAG,
            "more-start post=$postId sort=${sort.apiValue} node=$moreNodeId fromEnd=$fromEnd",
        )
        redditCommentMoreJob?.cancel()
        redditCommentMoreJob = scope.launch {
            // SLOWMORE01: 0 except in a dev build that asks for it (src/debug).
            if (SlowMoreLoadsForDebug.delayMs > 0) kotlinx.coroutines.delay(SlowMoreLoadsForDebug.delayMs)
            val result = try {
                if (fromEnd && loader is io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentFromEndLoader) {
                    loader.loadMoreFromSnapshotFromEnd(
                        accountStableId = accountStableId,
                        postId = postId,
                        sort = sort,
                        moreNodeId = moreNodeId,
                        currentComments = current.comments,
                    )
                } else if (loader is io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentSnapshotLoader) {
                    authDebugLog(
                        "SubshellCommentNav",
                        "phase=hydration_merge_base post=$postId node=$moreNodeId source=ui " +
                            "roots=${current.comments.size}",
                    )
                    loader.loadMoreFromSnapshot(
                        accountStableId = accountStableId,
                        postId = postId,
                        sort = sort,
                        moreNodeId = moreNodeId,
                        currentComments = current.comments,
                    )
                } else {
                    authDebugLog(
                        "SubshellCommentNav",
                        "phase=hydration_merge_base post=$postId node=$moreNodeId source=legacy_cache",
                    )
                    loader.loadMore(accountStableId, postId, sort, moreNodeId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditCommentResult.Failed(
                    kind = AuthenticatedRedditCommentFailureKind.STORAGE,
                    retryable = true,
                    details = storageFailureDetails(),
                )
            }
            if (!_redditCommentsUiState.value.matches(accountStableId, postId, sort)) {
                return@launch
            }
            // AUDIT298C_SMALL_NODES_LOAD_TOGETHER_V1: each further node merges
            // into the answer before it; a failure there keeps what loaded.
            var batched = result
            var authorFullnames = (result as? AuthenticatedRedditCommentResult.Loaded)?.authorFullnames.orEmpty()
            if (
                result is AuthenticatedRedditCommentResult.Loaded &&
                alsoNodeIds.isNotEmpty() &&
                loader is io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentSnapshotLoader
            ) {
                for (nodeId in alsoNodeIds) {
                    val base = (batched as AuthenticatedRedditCommentResult.Loaded).comments
                    val next = try {
                        loader.loadMoreFromSnapshot(
                            accountStableId = accountStableId,
                            postId = postId,
                            sort = sort,
                            moreNodeId = nodeId,
                            currentComments = base,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    if (next !is AuthenticatedRedditCommentResult.Loaded) break
                    authorFullnames = authorFullnames + next.authorFullnames
                    batched = next
                    authDebugLog(COMMENT_LOAD_LOG_TAG, "more-batch post=$postId node=$nodeId with=$moreNodeId")
                }
                if (!_redditCommentsUiState.value.matches(accountStableId, postId, sort)) {
                    return@launch
                }
            }
            val published = (batched as? AuthenticatedRedditCommentResult.Loaded)
                ?.copy(authorFullnames = authorFullnames)
                ?: batched
            _redditCommentsUiState.value = when (published) {
                is AuthenticatedRedditCommentResult.Loaded ->
                    _redditCommentsUiState.value.copy(
                        comments = published.comments,
                        loadingMoreNodeId = null,
                        failedMoreNodeId = null,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        fromCache = false,
                        authorFullnames =
                            _redditCommentsUiState.value.authorFullnames + published.authorFullnames,
                        contentRevision =
                            _redditCommentsUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditCommentResult.Failed ->
                    _redditCommentsUiState.value.copy(
                        loadingMoreNodeId = null,
                        failedMoreNodeId = moreNodeId,
                        failureKind = published.kind.toUiFailureKind(),
                        failureRetryable = published.retryable,
                        failureDetails = published.details,
                    )
            }.also { completed ->
                authDebugLog(
                    COMMENT_LOAD_LOG_TAG,
                    "more-finish post=$postId sort=${sort.apiValue} node=$moreNodeId " +
                        "failure=${completed.failureKind} count=${completed.comments.size}",
                )
            }
        }
        return true
    }

    // CONC02_LOCAL_WRITE_CANCELS_STALE_THREAD_LOAD_V1 (OCB-582): a thread load
    // already in flight could finish after a local edit/delete/reply and put
    // back the body it fetched before the write. The write cancels it; the
    // written comment is already cached, and a later load reads it.
    private fun cancelLoadOverwrittenByLocalWrite() {
        if (redditCommentJob?.isActive == true) {
            redditCommentJob?.cancel()
            redditCommentJob = null
        }
        // AUDIT298A_LOCAL_WRITES_AND_REFRESHES_STOP_MORE_LOADS_V1 (audit
        // 2026-11-12): a more load merges into the thread as it was when it
        // started; answering after a posted reply, an edit, a delete or a
        // moderation, it put that older thread back on screen. The node stays
        // and loads again; the write wins.
        if (redditCommentMoreJob?.isActive == true) {
            redditCommentMoreJob?.cancel()
            redditCommentMoreJob = null
            val current = _redditCommentsUiState.value
            if (current.loadingMoreNodeId != null) {
                _redditCommentsUiState.value = current.copy(loadingMoreNodeId = null)
            }
        }
    }

    fun applyEditedComment(request: CommentEditRequest) {
        updateStoredThread(request.accountStableId, request.postId) { stored ->
            stored.replaceEditedCommentBody(request.commentFullname, request.text.trim())
                .let { (updated, changed) -> updated.takeIf { changed } }
        }
        if (!_redditCommentsUiState.value.matches(request.accountStableId, request.postId, request.sort)) return
        // Read after the cancel: it clears loadingMoreNodeId.
        cancelLoadOverwrittenByLocalWrite()
        val current = _redditCommentsUiState.value
        val replacement = current.comments.replaceEditedCommentBody(
            commentFullname = request.commentFullname,
            text = request.text.trim(),
        )
        if (!replacement.second) return
        _redditCommentsUiState.value = current.copy(
            comments = replacement.first,
            loading = false,
            fullDiscussionLoading = false,
            fromCache = false,
            failureKind = null,
            failureRetryable = false,
            contentRevision = current.contentRevision + 1L,
        )
    }
    // ISSUE226_REMOVED_COMMENT_IS_MARKED_V1: a confirmed Remove/Spam marks the
    // comment in the open thread, Approve unmarks it; the next thread read
    // carries Reddit's own `removed` and replaces this.
    fun applyModeratedComment(accountStableId: String, commentFullname: String, removed: Boolean) {
        if (_redditCommentsUiState.value.accountStableId != accountStableId) return
        cancelLoadOverwrittenByLocalWrite()
        val current = _redditCommentsUiState.value
        current.postId?.let { postId ->
            updateStoredThread(accountStableId, postId) { stored ->
                stored.markModeratedComment(commentFullname, removed)
                    .let { (updated, changed) -> updated.takeIf { changed } }
            }
        }
        val replacement = current.comments.markModeratedComment(commentFullname, removed)
        if (!replacement.second) return
        _redditCommentsUiState.value = current.copy(
            comments = replacement.first,
            contentRevision = current.contentRevision + 1L,
        )
    }
    private fun List<io.github.brealorg.subshell.model.Comment>.markModeratedComment(
        commentFullname: String,
        removed: Boolean,
    ): Pair<List<io.github.brealorg.subshell.model.Comment>, Boolean> {
        val target = runCatching { normalizeCommentEditFullname(commentFullname) }.getOrNull()
            ?: return this to false
        var replaced = false
        val updated = map { comment ->
            val commentId = runCatching { normalizeCommentEditFullname(comment.id) }.getOrNull()
            if (!replaced && commentId == target) {
                replaced = true
                comment.copy(removedByModerator = removed)
            } else {
                val childResult = comment.children.markModeratedComment(target, removed)
                if (childResult.second) replaced = true
                comment.copy(children = childResult.first)
            }
        }
        return updated to replaced
    }
    // COMMENTSSOT01_SETTLED_COMMENT_ACTIONS_REACH_THE_THREAD_V1 (#244): a comment
    // vote or save Reddit took is written to every stored sort of the thread
    // shown for that account; the thread on screen follows the store. A comment
    // outside that thread is left (its override stays until a copy agrees).
    fun applySettledCommentAction(
        accountStableId: String,
        commentFullname: String,
        transform: (io.github.brealorg.subshell.model.Comment) -> io.github.brealorg.subshell.model.Comment,
    ) {
        val current = _redditCommentsUiState.value
        if (current.accountStableId != accountStableId) return
        val postId = current.postId ?: return
        if (!current.comments.mapComment(commentFullname, transform).second) return
        updateStoredThread(accountStableId, postId) { stored ->
            stored.mapComment(commentFullname, transform)
                .let { (updated, changed) -> updated.takeIf { changed } }
        }
    }

    private fun List<io.github.brealorg.subshell.model.Comment>.mapComment(
        commentFullname: String,
        transform: (io.github.brealorg.subshell.model.Comment) -> io.github.brealorg.subshell.model.Comment,
    ): Pair<List<io.github.brealorg.subshell.model.Comment>, Boolean> {
        val target = runCatching { normalizeCommentEditFullname(commentFullname) }.getOrNull()
            ?: return this to false
        var replaced = false
        val updated = map { comment ->
            val commentId = runCatching { normalizeCommentEditFullname(comment.id) }.getOrNull()
            if (!replaced && commentId == target) {
                replaced = true
                transform(comment)
            } else {
                val childResult = comment.children.mapComment(target, transform)
                if (childResult.second) replaced = true
                comment.copy(children = childResult.first)
            }
        }
        return updated to replaced
    }

    fun applyDeletedComment(request: CommentDeleteRequest) {
        updateStoredThread(request.accountStableId, request.postId) { stored ->
            stored.replaceDeletedComment(request.commentFullname)
                .let { (updated, changed) -> updated.takeIf { changed } }
        }
        if (!_redditCommentsUiState.value.matches(request.accountStableId, request.postId, request.sort)) return
        // Read after the cancel: it clears loadingMoreNodeId.
        cancelLoadOverwrittenByLocalWrite()
        val current = _redditCommentsUiState.value
        val replacement = current.comments.replaceDeletedComment(request.commentFullname)
        if (!replacement.second) return
        _redditCommentsUiState.value = current.copy(
            comments = replacement.first,
            loading = false,
            fullDiscussionLoading = false,
            failureKind = null,
            failureRetryable = false,
            contentRevision = current.contentRevision + 1L,
        )
    }
    private fun List<io.github.brealorg.subshell.model.Comment>.replaceDeletedComment(
        commentFullname: String,
    ): Pair<List<io.github.brealorg.subshell.model.Comment>, Boolean> {
        val target = normalizeCommentEditFullname(commentFullname)
        var replaced = false
        val updated = map { comment ->
            val commentId = runCatching { normalizeCommentEditFullname(comment.id) }.getOrNull()
            if (!replaced && commentId == target) {
                replaced = true
                comment.copy(
                    author = "[deleted]",
                    body = "[deleted]",
                    inlineMedia = null,
                    localMediaUri = null,
                    localMediaMimeType = null,
                    codeSample = null,
                )
            } else {
                val childResult = comment.children.replaceDeletedComment(target)
                if (childResult.second) replaced = true
                comment.copy(children = childResult.first)
            }
        }
        return updated to replaced
    }
    private fun List<io.github.brealorg.subshell.model.Comment>.replaceEditedCommentBody(
        commentFullname: String,
        text: String,
    ): Pair<List<io.github.brealorg.subshell.model.Comment>, Boolean> {
        val target = normalizeCommentEditFullname(commentFullname)
        var replaced = false
        val updated = map { comment ->
            val commentId = runCatching { normalizeCommentEditFullname(comment.id) }.getOrNull()
            if (!replaced && commentId == target) {
                replaced = true
                // MED1_EDIT_KEEPS_MEDIA_ANNOTATIONS_V1 (OCB-408): the edited
                // text is Reddit's own markup; keep the media addresses the
                // displayed body carried (emotes, appended media), as the post
                // edit rebind does.
                comment.copy(body = reannotateRedditMedia(text, previous = comment.body))
            } else {
                val childResult = comment.children.replaceEditedCommentBody(target, text)
                if (childResult.second) replaced = true
                comment.copy(children = childResult.first)
            }
        }
        return updated to replaced
    }
    // COMMENTCACHE01_SUBMIT_MERGES_DISPLAYED_THREAD_V1 (#138)
    fun displayedThreadFor(request: CommentWriteRequest): List<io.github.brealorg.subshell.model.Comment>? {
        val current = _redditCommentsUiState.value
        if (!current.matches(request.accountStableId, request.postId, request.sort)) return null
        return current.comments.takeIf { comments -> comments.isNotEmpty() }
    }

    fun applySubmittedCommentThread(
        request: CommentWriteRequest,
        comments: List<io.github.brealorg.subshell.model.Comment>,
    ) {
        if (!_redditCommentsUiState.value.matches(request.accountStableId, request.postId, request.sort)) return
        // Read after the cancel: it clears loadingMoreNodeId.
        cancelLoadOverwrittenByLocalWrite()
        val current = _redditCommentsUiState.value
        _redditCommentsUiState.value = current.copy(
            comments = comments,
            loading = false,
            fullDiscussionLoading = false,
            fromCache = false,
            failureKind = null,
            failureRetryable = false,
            contentRevision = current.contentRevision + 1L,
        )
    }
}

private const val COMMENT_LOAD_LOG_TAG = "subshell-comments"

private fun AuthenticatedRedditCommentFailureKind.toUiFailureKind():
    RedditCommentUiFailureKind = when (this) {
    AuthenticatedRedditCommentFailureKind.CLIENT_CONFIGURATION ->
        RedditCommentUiFailureKind.CLIENT_CONFIGURATION
    AuthenticatedRedditCommentFailureKind.SESSION ->
        RedditCommentUiFailureKind.SESSION
    AuthenticatedRedditCommentFailureKind.HTTP ->
        RedditCommentUiFailureKind.HTTP
    AuthenticatedRedditCommentFailureKind.TRANSPORT ->
        RedditCommentUiFailureKind.TRANSPORT
    AuthenticatedRedditCommentFailureKind.PROTOCOL ->
        RedditCommentUiFailureKind.PROTOCOL
    AuthenticatedRedditCommentFailureKind.STORAGE ->
        RedditCommentUiFailureKind.STORAGE
}

/**
 * SLOWMORE01_A_DEV_BUILD_CAN_HAVE_SLOW_MORE_LOADS_V1 (audit 2026-11-12): a
 * more load takes about half a second, too short to post a reply while one
 * runs on a phone. A dev build sets this from `files/more-delay-ms`
 * (src/debug SlowSealProvider); release builds never change it.
 */
internal object SlowMoreLoadsForDebug {
    @Volatile
    var delayMs: Long = 0L
}

/** Debug log only (authDebugLog): how much of a thread is loaded and how much waits in more nodes. */
internal fun commentTreeStats(comments: List<io.github.brealorg.subshell.model.Comment>): String {
    var loaded = 0
    var moreNodes = 0
    var continueNodes = 0
    var waitingIds = 0
    var waitingCount = 0
    fun walk(comment: io.github.brealorg.subshell.model.Comment) {
        when {
            comment.moreChildIds.isNotEmpty() -> {
                moreNodes += 1
                waitingIds += comment.moreChildIds.size
                waitingCount += comment.moreCount
            }
            comment.id.startsWith("continue:") -> continueNodes += 1
            else -> loaded += 1
        }
        comment.children.forEach(::walk)
    }
    comments.forEach(::walk)
    return "tree loaded=$loaded more=$moreNodes continue=$continueNodes waitingIds=$waitingIds waitingCount=$waitingCount"
}
