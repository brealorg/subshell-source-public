package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.model.reannotateRedditMedia
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.RedditCommentUiFailureKind
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.beginSortLoad
import app.subshell.prototype.data.account.normalizeCommentEditFullname
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFocusedCommentContinuityLoader
import app.subshell.prototype.data.network.reddit.focusedCommentContextForTarget
import app.subshell.prototype.data.network.reddit.mergeFocusedCommentContext
import app.subshell.prototype.data.retainRedditSuggestedCommentSort
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

    /** ViewModel.onCleared: stop in-flight thread loads. */
    fun cancelPending() {
        redditCommentJob?.cancel()
        redditCommentMoreJob?.cancel()
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
                        "suggested=${completed.suggestedSort?.apiValue}",
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
        redditCommentJob?.cancel()
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
                            refreshed.comments
                        } else {
                            mergeFocusedCommentContext(
                                current = refreshed.comments,
                                focused = focusedContext,
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
                        "suggested=${completed.suggestedSort?.apiValue}",
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
            "more-start post=$postId sort=${sort.apiValue} node=$moreNodeId",
        )
        redditCommentMoreJob?.cancel()
        redditCommentMoreJob = scope.launch {
            val result = try {
                if (loader is app.subshell.prototype.data.auth.AuthenticatedRedditCommentSnapshotLoader) {
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
            _redditCommentsUiState.value = when (result) {
                is AuthenticatedRedditCommentResult.Loaded ->
                    _redditCommentsUiState.value.copy(
                        comments = result.comments,
                        loadingMoreNodeId = null,
                        failedMoreNodeId = null,
                        failureKind = null,
                        failureRetryable = false,
                        failureDetails = null,
                        fromCache = false,
                        authorFullnames =
                            _redditCommentsUiState.value.authorFullnames + result.authorFullnames,
                        contentRevision =
                            _redditCommentsUiState.value.contentRevision + 1L,
                    )
                is AuthenticatedRedditCommentResult.Failed ->
                    _redditCommentsUiState.value.copy(
                        loadingMoreNodeId = null,
                        failedMoreNodeId = moreNodeId,
                        failureKind = result.kind.toUiFailureKind(),
                        failureRetryable = result.retryable,
                        failureDetails = result.details,
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
    }

    fun applyEditedComment(request: CommentEditRequest) {
        val current = _redditCommentsUiState.value
        if (!current.matches(request.accountStableId, request.postId, request.sort)) return
        cancelLoadOverwrittenByLocalWrite()
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
        val current = _redditCommentsUiState.value
        if (current.accountStableId != accountStableId) return
        val replacement = current.comments.markModeratedComment(commentFullname, removed)
        if (!replacement.second) return
        _redditCommentsUiState.value = current.copy(
            comments = replacement.first,
            contentRevision = current.contentRevision + 1L,
        )
    }
    private fun List<app.subshell.prototype.model.Comment>.markModeratedComment(
        commentFullname: String,
        removed: Boolean,
    ): Pair<List<app.subshell.prototype.model.Comment>, Boolean> {
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
    fun applyDeletedComment(request: CommentDeleteRequest) {
        val current = _redditCommentsUiState.value
        if (!current.matches(request.accountStableId, request.postId, request.sort)) return
        cancelLoadOverwrittenByLocalWrite()
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
    private fun List<app.subshell.prototype.model.Comment>.replaceDeletedComment(
        commentFullname: String,
    ): Pair<List<app.subshell.prototype.model.Comment>, Boolean> {
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
    private fun List<app.subshell.prototype.model.Comment>.replaceEditedCommentBody(
        commentFullname: String,
        text: String,
    ): Pair<List<app.subshell.prototype.model.Comment>, Boolean> {
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
    fun displayedThreadFor(request: CommentWriteRequest): List<app.subshell.prototype.model.Comment>? {
        val current = _redditCommentsUiState.value
        if (!current.matches(request.accountStableId, request.postId, request.sort)) return null
        return current.comments.takeIf { comments -> comments.isNotEmpty() }
    }

    fun applySubmittedCommentThread(
        request: CommentWriteRequest,
        comments: List<app.subshell.prototype.model.Comment>,
    ) {
        val current = _redditCommentsUiState.value
        if (!current.matches(request.accountStableId, request.postId, request.sort)) return
        cancelLoadOverwrittenByLocalWrite()
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
