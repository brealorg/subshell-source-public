package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.reddit.SUBMIT_READBACK_CLOCK_SKEW_SECONDS
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.CommentWriteFailureKind
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitMediaAttachment
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.model.Comment
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.subshell.prototype.data.network.reddit.redditCommentBodyStillProcessing
import app.subshell.prototype.data.network.reddit.redditCommentHistoryShowsNoAttempt
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class CommentWriteCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val submitter: AuthenticatedCommentSubmitter?,
    private val commentLoader: AuthenticatedRedditCommentLoader?,
    private val accountDirectory: AccountSessionDirectory?,
    private val clientMode: OAuthClientMode?,
    private val onThreadUpdated: (
        request: CommentWriteRequest,
        comments: List<Comment>,
    ) -> Unit = { _, _ -> },
    // COMMENTCACHE01_SUBMIT_MERGES_DISPLAYED_THREAD_V1 (#138): the thread the
    // reader sees for this request's account, post and sort, or null.
    private val displayedThread: (CommentWriteRequest) -> List<Comment>? = { null },
) {
    private val operationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(CommentWriteUiState())
    private var pendingRequest: CommentWriteRequest? = null

    // COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: an image/GIF comment Reddit
    // accepted while the media was still processing ("*Processing img …*").
    private var processingMediaComment: Pair<CommentWriteRequest, Comment>? = null

    // COMPOSER01_COMMENT_OUTCOME_READ_BACK_V1: a comment whose response was
    // lost. Until Reddit has been read back, the same content is never
    // dispatched again: submit() checks instead (Retry, or Dismiss then Send).
    private data class UnknownOutcomeAttempt(
        val request: CommentWriteRequest,
        val attemptStartedEpochSeconds: Long,
    )
    // LOW10_COMMENT_UNKNOWN_OUTCOMES_KEPT_PER_COMMENT_V1 (OCB-583): one slot was
    // cleared by any other comment, after which the unconfirmed one could be
    // sent again with no read-back. Each unconfirmed comment (account, parent,
    // content) stays until Reddit is read back for it.
    private val unknownOutcomeAttempts = mutableListOf<UnknownOutcomeAttempt>()

    private fun forgetUnknownOutcome(request: CommentWriteRequest) {
        unknownOutcomeAttempts.removeAll { it.request.sameCommentAs(request) }
    }
    private var completionRevision = 0L

    val uiState: StateFlow<CommentWriteUiState> = mutableUiState.asStateFlow()

    suspend fun submit(request: CommentWriteRequest) {
        if (!operationMutex.tryLock()) return
        try {
            unknownOutcomeAttempts.firstOrNull { it.request.sameCommentAs(request) }?.let { unknown ->
                checkUnknownOutcome(unknown)
                return
            }
            pendingRequest = request
            if (!isActiveAuthenticatedAccount(request.accountStableId)) {
                mutableUiState.value = failure(
                    request = request,
                    kind = CommentWriteFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            val configuredSubmitter = submitter
            if (configuredSubmitter == null) {
                mutableUiState.value = failure(
                    request = request,
                    kind = CommentWriteFailureKind.CONFIGURATION,
                )
                return
            }

            // SUBSHELL_MESSAGES02C4R3_MEDIA_COMMENT_AUTHORITATIVE_ACK_RECONCILIATION_V1
            val attemptStartedEpochSeconds = System.currentTimeMillis() / 1_000L
            mutableUiState.value = state(CommentWritePhase.SENDING, request)
            val result = try {
                configuredSubmitter.submit(
                    AuthenticatedCommentSubmitRequest(
                        accountStableId = request.accountStableId,
                        parentFullname = request.parentFullname,
                        text = request.text,
                        media = request.media?.let { attachment ->
                            AuthenticatedCommentSubmitMediaAttachment(
                                uri = attachment.uri,
                                mimeType = attachment.mimeType,
                                sourceLinkUri = attachment.sourceLinkUri,
                            )
                        },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedCommentSubmitResult.Failed(
                    kind = AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN),
                )
            }
            applySubmitResult(
                request = request,
                result = result,
                attemptStartedEpochSeconds = attemptStartedEpochSeconds,
            )
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == CommentWritePhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            mutableUiState.value = failure(
                request = request,
                kind = CommentWriteFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (!isActiveAuthenticatedAccount(request.accountStableId)) {
            mutableUiState.value = failure(
                request = request,
                kind = CommentWriteFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(CommentWritePhase.AUTHORIZING, request)
        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(
                request.accountStableId,
            )
            mutableUiState.value = state(CommentWritePhase.AWAITING_CALLBACK, request)
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                request = request,
                kind = CommentWriteFailureKind.AUTHORIZATION,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val request = pendingRequest ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            request = request,
            kind = CommentWriteFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun reset() {
        if (mutableUiState.value.inProgress) return
        pendingRequest = null
        mutableUiState.value = CommentWriteUiState(
            completionRevision = completionRevision,
        )
    }

    fun cancelPendingAuthorization() {
        if (
            mutableUiState.value.phase !in setOf(
                CommentWritePhase.AUTHORIZING,
                CommentWritePhase.AWAITING_CALLBACK,
                CommentWritePhase.AUTHORIZATION_REQUIRED,
            )
        ) {
            return
        }
        capability?.cancelPendingAuthorization()
        pendingRequest = null
        mutableUiState.value = CommentWriteUiState(
            completionRevision = completionRevision,
        )
    }

    fun ownsStoredOrPendingAuthorization(): Boolean =
        capability?.let { configured ->
            configured.hasStoredCallback() || configured.hasPendingAuthorization()
        } ?: false

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val ownsCallback = ownsStoredOrPendingAuthorization() ||
            mutableUiState.value.phase == CommentWritePhase.AWAITING_CALLBACK
        if (!ownsCallback) return false

        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability
        if (configuredCapability == null || !configuredCapability.hasStoredCallback()) {
            configuredCapability?.cancelPendingAuthorization()
            val request = pendingRequest
            mutableUiState.value = if (request == null) {
                CommentWriteUiState(completionRevision = completionRevision)
            } else {
                failure(
                    request = request,
                    kind = CommentWriteFailureKind.CALLBACK,
                    retryable = true,
                )
            }
            return true
        }
        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        val configuredCapability = capability ?: return false
        return when {
            configuredCapability.hasStoredCallback() -> {
                completeAuthorization()
                true
            }
            configuredCapability.hasPendingAuthorization() -> true
            else -> false
        }
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val request = pendingRequest
        if (request != null) {
            mutableUiState.value = state(CommentWritePhase.AUTHORIZING, request)
        }
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    mutableUiState.value = CommentWriteUiState(
                        completionRevision = completionRevision,
                    )
                    return
                }
                if (
                    result.accountStableId != request.accountStableId ||
                    !isActiveAuthenticatedAccount(request.accountStableId)
                ) {
                    mutableUiState.value = failure(
                        request = request,
                        kind = CommentWriteFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = state(CommentWritePhase.IDLE, request)
                submit(request)
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                if (request == null) {
                    mutableUiState.value = CommentWriteUiState(
                        completionRevision = completionRevision,
                    )
                } else {
                    mutableUiState.value = failure(
                        request = request,
                        kind = result.kind.toUiFailureKind(),
                        retryable = result.retryable,
                    )
                }
            }
        }
    }

    private suspend fun applySubmitResult(
        request: CommentWriteRequest,
        result: AuthenticatedCommentSubmitResult,
        attemptStartedEpochSeconds: Long,
    ) {
        when (result) {
            is AuthenticatedCommentSubmitResult.Submitted -> {
                val cached = try {
                    commentLoader?.cacheSubmittedCommentOnto(
                        accountStableId = request.accountStableId,
                        postId = request.postId,
                        sort = request.sort,
                        parentFullname = request.parentFullname,
                        comment = result.comment,
                        currentComments = displayedThread(request),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                val updatedComments = (cached as? AuthenticatedRedditCommentResult.Loaded)?.comments
                if (updatedComments != null) {
                    onThreadUpdated(request, updatedComments)
                }
                processingMediaComment = (request to result.comment).takeIf {
                    request.media != null && commentMediaStillProcessing(result.comment.body)
                }
                completionRevision += 1L
                pendingRequest = null
                mutableUiState.value = CommentWriteUiState(
                    phase = CommentWritePhase.SUCCEEDED,
                    accountStableId = request.accountStableId,
                    postId = request.postId,
                    targetStableKey = request.targetStableKey,
                    submittedComment = result.comment,
                    threadUpdated = updatedComments != null,
                    completionRevision = completionRevision,
                )
            }
            is AuthenticatedCommentSubmitResult.Failed -> {
                if (
                    result.kind == AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN &&
                    request.media != null &&
                    reconcileOutcomeUnknownMedia(
                        request = request,
                        attemptStartedEpochSeconds = attemptStartedEpochSeconds,
                    )
                ) {
                    return
                }
                if (result.kind == AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN) {
                    val unknown = UnknownOutcomeAttempt(request, attemptStartedEpochSeconds)
                    forgetUnknownOutcome(request)
                    unknownOutcomeAttempts += unknown
                    while (unknownOutcomeAttempts.size > MAX_UNKNOWN_OUTCOME_ATTEMPTS) {
                        unknownOutcomeAttempts.removeAt(0)
                    }
                    // Right after the attempt Reddit may lag: only FOUND counts
                    // here; NOT_FOUND is reported only by an explicit check.
                    if (request.media == null && readBackTextComment(unknown) == TextReadBack.FOUND) {
                        return
                    }
                }
                mutableUiState.value = result.toUiState(request)
            }
        }
    }

    private enum class TextReadBack { FOUND, NOT_FOUND, UNAVAILABLE }

    /** Check again: reads Reddit, never dispatches. */
    private suspend fun checkUnknownOutcome(unknown: UnknownOutcomeAttempt) {
        val request = unknown.request
        pendingRequest = request
        mutableUiState.value = state(CommentWritePhase.SENDING, request)
        if (request.media != null) {
            if (
                !reconcileOutcomeUnknownMedia(
                    request = request,
                    attemptStartedEpochSeconds = unknown.attemptStartedEpochSeconds,
                )
            ) {
                mutableUiState.value = outcomeUnknown(request)
            }
            return
        }
        when (readBackTextComment(unknown)) {
            TextReadBack.FOUND -> Unit
            TextReadBack.UNAVAILABLE -> mutableUiState.value = outcomeUnknown(request)
            TextReadBack.NOT_FOUND -> {
                forgetUnknownOutcome(request)
                mutableUiState.value = failure(
                    request = request,
                    kind = CommentWriteFailureKind.NOT_POSTED,
                    retryable = true,
                )
            }
        }
    }

    /**
     * Reads the thread back after a lost response. FOUND publishes SUCCEEDED;
     * NOT_FOUND means the thread loaded and holds no such comment from this
     * account; UNAVAILABLE means Reddit could not be read.
     */
    private suspend fun readBackTextComment(unknown: UnknownOutcomeAttempt): TextReadBack {
        val request = unknown.request
        val configuredLoader = commentLoader ?: return TextReadBack.UNAVAILABLE
        val activeUsername = accountDirectory
            ?.snapshot()
            ?.accounts
            ?.firstOrNull { account ->
                account.stableId == request.accountStableId &&
                    account.source == AccountSessionSource.AUTHENTICATED
            }
            ?.username
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return TextReadBack.UNAVAILABLE
        val synchronized = try {
            configuredLoader.synchronize(
                accountStableId = request.accountStableId,
                postId = request.postId,
                sort = request.sort,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return TextReadBack.UNAVAILABLE
        }
        val comments = (synchronized as? AuthenticatedRedditCommentResult.Loaded)?.comments
            ?: return TextReadBack.UNAVAILABLE
        val earliest = unknown.attemptStartedEpochSeconds - READBACK_CLOCK_SKEW_SECONDS
        val authoritative = findAuthoritativeTextCommentAfterUnknownOutcome(
            comments = comments,
            username = activeUsername,
            parentFullname = request.parentFullname,
            submittedText = request.text,
            earliestCreatedEpochSeconds = earliest,
        ) ?: return textCommentAbsenceFromAccountHistory(
            loader = configuredLoader,
            request = request,
            username = activeUsername,
            earliestCreatedEpochSeconds = earliest,
        )

        forgetUnknownOutcome(request)
        onThreadUpdated(request, comments)
        completionRevision += 1L
        pendingRequest = null
        mutableUiState.value = CommentWriteUiState(
            phase = CommentWritePhase.SUCCEEDED,
            accountStableId = request.accountStableId,
            postId = request.postId,
            targetStableKey = request.targetStableKey,
            submittedComment = authoritative,
            threadUpdated = true,
            completionRevision = completionRevision,
        )
        return TextReadBack.FOUND
    }

    // COMMENTREADBACK01_ACCOUNT_HISTORY_ABSENCE_V1 (#123): the thread listing is
    // truncated (100 comments, depth 8, the reader's sort, unopened "more"
    // nodes), so its miss proves nothing. NOT_FOUND (which allows a resend)
    // needs the account's own history to cover the attempt without any
    // comment under the same parent; otherwise the outcome stays unknown.
    private suspend fun textCommentAbsenceFromAccountHistory(
        loader: AuthenticatedRedditCommentLoader,
        request: CommentWriteRequest,
        username: String,
        earliestCreatedEpochSeconds: Long,
    ): TextReadBack {
        val history = try {
            loader.readAccountRecentComments(request.accountStableId, username)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return TextReadBack.UNAVAILABLE
        return if (
            redditCommentHistoryShowsNoAttempt(
                username = username,
                parentFullname = request.parentFullname,
                earliestCreatedEpochSeconds = earliestCreatedEpochSeconds,
                history = history,
            )
        ) {
            TextReadBack.NOT_FOUND
        } else {
            TextReadBack.UNAVAILABLE
        }
    }

    private fun outcomeUnknown(request: CommentWriteRequest): CommentWriteUiState = failure(
        request = request,
        kind = CommentWriteFailureKind.API,
        outcomeUnknown = true,
    )

    private suspend fun reconcileOutcomeUnknownMedia(
        request: CommentWriteRequest,
        attemptStartedEpochSeconds: Long,
    ): Boolean {
        val configuredLoader = commentLoader ?: return false
        val mediaMimeType = request.media?.mimeType ?: return false
        val activeUsername = accountDirectory
            ?.snapshot()
            ?.accounts
            ?.firstOrNull { account ->
                account.stableId == request.accountStableId &&
                    account.source == AccountSessionSource.AUTHENTICATED
            }
            ?.username
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return false
        val synchronized = try {
            configuredLoader.synchronize(
                accountStableId = request.accountStableId,
                postId = request.postId,
                sort = request.sort,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }
        val comments = (synchronized as? AuthenticatedRedditCommentResult.Loaded)?.comments
            ?: return false
        val authoritative = findAuthoritativeMediaCommentAfterUnknownOutcome(
            comments = comments,
            username = activeUsername,
            parentFullname = request.parentFullname,
            submittedText = request.text,
            mediaMimeType = mediaMimeType,
            earliestCreatedEpochSeconds = attemptStartedEpochSeconds - READBACK_CLOCK_SKEW_SECONDS,
        ) ?: return false

        forgetUnknownOutcome(request)
        onThreadUpdated(request, comments)
        completionRevision += 1L
        pendingRequest = null
        mutableUiState.value = CommentWriteUiState(
            phase = CommentWritePhase.SUCCEEDED,
            accountStableId = request.accountStableId,
            postId = request.postId,
            targetStableKey = request.targetStableKey,
            submittedComment = authoritative,
            threadUpdated = true,
            completionRevision = completionRevision,
        )
        return true
    }

    private fun AuthenticatedCommentSubmitResult.Failed.toUiState(
        request: CommentWriteRequest,
    ): CommentWriteUiState = when (kind) {
        AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED ->
            state(CommentWritePhase.AUTHORIZATION_REQUIRED, request)

        AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN ->
            failure(
                request = request,
                kind = CommentWriteFailureKind.API,
                outcomeUnknown = true,
                failureDetails = failureDetails,
            )

        else -> failure(
            request = request,
            kind = kind.toUiFailureKind(),
            retryable = retryable,
            retryAfterMs = retryAfterMs,
            failureDetails = failureDetails,
        )
    }

    /**
     * COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: after [submit], an image/GIF
     * comment still processing is read back with backoff until Reddit has
     * resolved its media, then replaced in the cached and shown thread (as the
     * submit itself was). Runs outside the submit lock; stops when the account
     * changes; after the last try the placeholder stays, as before.
     */
    suspend fun followProcessingMediaComment(
        delaysMs: List<Long> = COMMENT_MEDIA_READ_BACK_DELAYS_MS,
    ) {
        val (request, comment) = processingMediaComment ?: return
        processingMediaComment = null
        val loader = commentLoader ?: return
        for (delayMs in delaysMs) {
            delay(delayMs)
            if (!isActiveAuthenticatedAccount(request.accountStableId)) return
            val body = try {
                loader.readCommentBody(request.accountStableId, comment.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (body == null || commentMediaStillProcessing(body)) continue
            val cached = try {
                loader.cacheSubmittedCommentOnto(
                    accountStableId = request.accountStableId,
                    postId = request.postId,
                    sort = request.sort,
                    parentFullname = request.parentFullname,
                    comment = comment.copy(body = body),
                    currentComments = displayedThread(request),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            (cached as? AuthenticatedRedditCommentResult.Loaded)?.comments?.let { comments ->
                onThreadUpdated(request, comments)
            }
            return
        }
    }

    private fun isActiveAuthenticatedAccount(accountStableId: String): Boolean =
        accountDirectory?.snapshot()?.let { snapshot ->
            snapshot.activeAccountId == accountStableId &&
                snapshot.accounts.any { account ->
                    account.stableId == accountStableId &&
                        account.source == AccountSessionSource.AUTHENTICATED
                }
        } == true

    private fun state(
        phase: CommentWritePhase,
        request: CommentWriteRequest,
    ): CommentWriteUiState = CommentWriteUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        postId = request.postId,
        targetStableKey = request.targetStableKey,
        completionRevision = completionRevision,
    )

    private fun failure(
        request: CommentWriteRequest,
        kind: CommentWriteFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        outcomeUnknown: Boolean = false,
        failureDetails: FailureDetails? = null,
    ): CommentWriteUiState = CommentWriteUiState(
        phase = if (outcomeUnknown) {
            CommentWritePhase.OUTCOME_UNKNOWN
        } else {
            CommentWritePhase.FAILED
        },
        accountStableId = request.accountStableId,
        postId = request.postId,
        targetStableKey = request.targetStableKey,
        failureKind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        failureDetails = failureDetails,
        completionRevision = completionRevision,
    )

    companion object {
        // LOW10_COMMENT_READ_BACK_ALLOWS_CLOCK_SKEW_V1 (OCB-409): the phone's
        // clock against Reddit's created_utc, as for posts (SUBMITREADBACK02).
        private const val READBACK_CLOCK_SKEW_SECONDS = SUBMIT_READBACK_CLOCK_SKEW_SECONDS
        private const val MAX_UNKNOWN_OUTCOME_ATTEMPTS = 20
    }
}

private fun CommentWriteRequest.sameCommentAs(other: CommentWriteRequest): Boolean =
    accountStableId == other.accountStableId &&
        targetStableKey == other.targetStableKey &&
        text.trim() == other.text.trim() &&
        media?.uri == other.media?.uri

// COMPOSER01_COMMENT_OUTCOME_READ_BACK_V1: the account's comment under the
// same parent, created after the attempt started, with the same text.
internal fun findAuthoritativeTextCommentAfterUnknownOutcome(
    comments: List<Comment>,
    username: String,
    parentFullname: String,
    submittedText: String,
    earliestCreatedEpochSeconds: Long,
): Comment? {
    val normalizedUsername = username.trim()
    val normalizedParent = parentFullname.trim().lowercase()
    val normalizedText = submittedText.normalizedCommentReadBackText()
    if (normalizedUsername.isEmpty() || normalizedParent.isEmpty() || normalizedText.isEmpty()) return null
    return flattenCommentTreeForWriteReconciliation(comments)
        .filter { comment -> comment.author.equals(normalizedUsername, ignoreCase = true) }
        .filter { comment -> comment.parentId?.trim()?.lowercase() == normalizedParent }
        .filter { comment ->
            comment.createdEpochSeconds?.let { created -> created >= earliestCreatedEpochSeconds } == true
        }
        .filter { comment -> comment.body.normalizedCommentReadBackText() == normalizedText }
        .maxByOrNull { comment -> comment.createdEpochSeconds ?: Long.MIN_VALUE }
}

private fun String.normalizedCommentReadBackText(): String =
    replace("\r\n", "\n").lines().joinToString("\n") { line -> line.trimEnd() }.trim()

internal fun findAuthoritativeMediaCommentAfterUnknownOutcome(
    comments: List<Comment>,
    username: String,
    parentFullname: String,
    submittedText: String,
    mediaMimeType: String,
    earliestCreatedEpochSeconds: Long,
): Comment? {
    val normalizedUsername = username.trim()
    val normalizedParent = parentFullname.trim().lowercase()
    val normalizedText = submittedText.trim()
    val normalizedMime = mediaMimeType.trim().lowercase()
    if (normalizedUsername.isEmpty() || normalizedParent.isEmpty()) return null

    return flattenCommentTreeForWriteReconciliation(comments)
        .filter { comment -> comment.author.equals(normalizedUsername, ignoreCase = true) }
        .filter { comment -> comment.parentId?.trim()?.lowercase() == normalizedParent }
        .filter { comment ->
            comment.createdEpochSeconds?.let { created ->
                created >= earliestCreatedEpochSeconds
            } == true
        }
        .filter { comment ->
            val body = comment.body.trim()
            when {
                normalizedText.isNotEmpty() ->
                    body.startsWith(normalizedText)
                normalizedMime == "image/gif" ->
                    body.contains(".gif", ignoreCase = true) ||
                        body.contains("Processing img ", ignoreCase = true)
                else ->
                    body.contains("i.redd.it/", ignoreCase = true) ||
                        body.contains("preview.redd.it/", ignoreCase = true) ||
                        body.contains("Processing img ", ignoreCase = true)
            }
        }
        .maxByOrNull { comment -> comment.createdEpochSeconds ?: Long.MIN_VALUE }
}

private fun flattenCommentTreeForWriteReconciliation(
    comments: List<Comment>,
): Sequence<Comment> = sequence {
    comments.forEach { comment ->
        yield(comment)
        yieldAll(flattenCommentTreeForWriteReconciliation(comment.children))
    }
}

private fun AuthCommentWriteCapabilityFailureKind.toUiFailureKind(): CommentWriteFailureKind =
    when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            CommentWriteFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            CommentWriteFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> CommentWriteFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            CommentWriteFailureKind.SESSION
    }

private fun AuthenticatedCommentSubmitFailureKind.toUiFailureKind(): CommentWriteFailureKind =
    when (this) {
        AuthenticatedCommentSubmitFailureKind.VALIDATION ->
            CommentWriteFailureKind.VALIDATION
        AuthenticatedCommentSubmitFailureKind.MEDIA_TOO_LARGE ->
            CommentWriteFailureKind.MEDIA_TOO_LARGE
        AuthenticatedCommentSubmitFailureKind.MEDIA_UNSUPPORTED ->
            CommentWriteFailureKind.MEDIA_UNSUPPORTED
        AuthenticatedCommentSubmitFailureKind.MEDIA_UNAVAILABLE ->
            CommentWriteFailureKind.MEDIA_UNAVAILABLE
        AuthenticatedCommentSubmitFailureKind.ALREADY_SUBMITTING ->
            CommentWriteFailureKind.ALREADY_SUBMITTING
        AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED ->
            CommentWriteFailureKind.AUTHORIZATION
        AuthenticatedCommentSubmitFailureKind.SESSION ->
            CommentWriteFailureKind.SESSION
        AuthenticatedCommentSubmitFailureKind.RATE_LIMIT ->
            CommentWriteFailureKind.RATE_LIMIT
        AuthenticatedCommentSubmitFailureKind.PARENT_UNAVAILABLE ->
            CommentWriteFailureKind.PARENT_UNAVAILABLE
        AuthenticatedCommentSubmitFailureKind.PERMISSION ->
            CommentWriteFailureKind.PERMISSION
        AuthenticatedCommentSubmitFailureKind.API ->
            CommentWriteFailureKind.API
        AuthenticatedCommentSubmitFailureKind.HTTP ->
            CommentWriteFailureKind.HTTP
        AuthenticatedCommentSubmitFailureKind.TRANSPORT ->
            CommentWriteFailureKind.TRANSPORT
        AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN ->
            CommentWriteFailureKind.API
        AuthenticatedCommentSubmitFailureKind.MEDIA_NOT_ALLOWED ->
            CommentWriteFailureKind.MEDIA_NOT_ALLOWED
    }

internal val COMMENT_MEDIA_READ_BACK_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)

// An image comment's body is empty or has a "Processing img" line until
// Reddit has processed the image.
private fun commentMediaStillProcessing(body: String): Boolean =
    body.isBlank() || redditCommentBodyStillProcessing(body)
