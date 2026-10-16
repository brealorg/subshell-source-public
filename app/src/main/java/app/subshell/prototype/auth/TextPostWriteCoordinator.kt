package app.subshell.prototype.auth

import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.normalizeCrosspostSourceFullname
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostCommunity
import app.subshell.prototype.data.account.SubmittedTextPostGalleryItem
import app.subshell.prototype.data.account.TextPostWriteFailureKind
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.data.account.TextPostWriteValidationField
import app.subshell.prototype.data.account.TextPostWriteValidationIssue
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostGalleryItem
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostValidationField
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class TextPostWriteCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val submitter: AuthenticatedTextPostSubmitter?,
    private val accountDirectory: AccountSessionDirectory?,
    private val clientMode: OAuthClientMode?,
    private val attemptStore: TextPostSubmitAttemptStore? = null,
    // LOW_DRAFTS_ATTEMPT_STORE_OFF_MAIN_V1 (OCB-531): the attempt store
    // commits the shared preferences file synchronously; the coordinator runs
    // on viewModelScope, so every store call goes through this dispatcher.
    private val storeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val operationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(TextPostWriteUiState())
    private var pendingRequest: TextPostWriteRequest? = null
    private var completionRevision = 0L

    val uiState: StateFlow<TextPostWriteUiState> = mutableUiState.asStateFlow()

    suspend fun submit(request: TextPostWriteRequest) {
        if (!operationMutex.tryLock()) return
        try {
            loadAttempt(request.accountStableId)
                ?.takeIf { attempt -> attempt.dispatchedAtEpochMs != null }
                ?.let { unresolved ->
                    pendingRequest = unresolved.request
                    mutableUiState.value = if (
                        unresolved.request.requestKey == request.requestKey &&
                        unresolved.request.accountStableId == request.accountStableId
                    ) {
                        failure(
                            request = unresolved.request,
                            kind = TextPostWriteFailureKind.API,
                            outcomeUnknown = true,
                        )
                    } else {
                        // COMPOSER02_SUBMIT_ATTEMPT_PER_ACCOUNT_V1: another post of
                        // this account is unconfirmed; Check again reads it back.
                        failure(
                            request = request,
                            kind = TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED,
                            retryable = true,
                        )
                    }
                    return
                }
            pendingRequest = request
            if (!isActiveAuthenticatedAccount(request.accountStableId)) {
                mutableUiState.value = failure(
                    request = request,
                    kind = TextPostWriteFailureKind.ACCOUNT_CHANGED,
                )
                return
            }
            val configuredSubmitter = submitter
            if (configuredSubmitter == null) {
                mutableUiState.value = failure(
                    request = request,
                    kind = TextPostWriteFailureKind.CONFIGURATION,
                )
                return
            }

            // LOWCRASH01_ATTEMPT_STORE_FAILURE_NOT_A_CRASH_V1 (OCB-448): a failed
            // commit (full disk) threw out of viewModelScope. Nothing is sent
            // without a staged attempt; the post fails locally and can be retried.
            val staged = try {
                withContext(storeDispatcher) { attemptStore?.stage(request) }
                true
            } catch (error: Exception) {
                false
            }
            if (!staged) {
                mutableUiState.value = TextPostWriteUiState(
                    phase = TextPostWritePhase.FAILED,
                    accountStableId = request.accountStableId,
                    requestKey = request.requestKey,
                    retryable = true,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.STORAGE),
                    completionRevision = completionRevision,
                )
                return
            }
            mutableUiState.value = state(TextPostWritePhase.SENDING, request)
            val result = try {
                configuredSubmitter.submit(
                    AuthenticatedTextPostSubmitRequest(
                        accountStableId = request.accountStableId,
                        community = request.community,
                        title = request.title,
                        text = request.text,
                        sendReplies = request.sendReplies,
                        nsfw = request.nsfw,
                        spoiler = request.spoiler,
                        linkUrl = request.linkUrl,
                        mediaUri = request.mediaUri,
                        mediaMimeType = request.mediaMimeType,
                        galleryItems = request.galleryItems.map { item ->
                            AuthenticatedTextPostGalleryItem(
                                mediaUri = item.mediaUri,
                                mediaMimeType = item.mediaMimeType,
                                caption = item.caption,
                                outboundUrl = item.outboundUrl,
                            )
                        },
                        flairId = request.flairId,
                        flairText = request.flairText,
                        requestKey = request.requestKey,
                        mediaAsGif = request.mediaAsGif,
                        crosspostFullname = request.crosspostFullname,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedTextPostSubmitResult.Failed(
                    kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN),
                )
            }
            applySubmitResult(request, result)
        } finally {
            operationMutex.unlock()
        }
    }


    suspend fun restorePendingSubmitState() {
        if (!operationMutex.tryLock()) return
        try {
            val accountStableId = accountDirectory?.snapshot()?.activeAccountId ?: return
            val attempt = loadAttempt(accountStableId) ?: return
            if (attempt.dispatchedAtEpochMs == null) {
                clearAttempt(accountStableId, attempt.request.requestKey)
                return
            }
            pendingRequest = attempt.request
            mutableUiState.value = failure(
                request = attempt.request,
                kind = TextPostWriteFailureKind.API,
                outcomeUnknown = true,
            )
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun reconcilePendingSubmit() {
        if (!operationMutex.tryLock()) return
        try {
            val accountStableId = accountDirectory?.snapshot()?.activeAccountId ?: return
            val attempt = loadAttempt(accountStableId)
            if (attempt == null) {
                // Resolved elsewhere (another store instance, an earlier check).
                if (
                    mutableUiState.value.phase == TextPostWritePhase.OUTCOME_UNKNOWN ||
                    mutableUiState.value.failureKind ==
                    TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED
                ) {
                    pendingRequest = null
                    mutableUiState.value = TextPostWriteUiState(
                        completionRevision = completionRevision,
                    )
                }
                return
            }
            val dispatchedAtEpochMs = attempt.dispatchedAtEpochMs
            if (dispatchedAtEpochMs == null) {
                clearAttempt(accountStableId, attempt.request.requestKey)
                pendingRequest = null
                mutableUiState.value = TextPostWriteUiState(
                    completionRevision = completionRevision,
                )
                return
            }
            val snapshot = accountDirectory.snapshot()
            val account = snapshot
                .accounts
                .firstOrNull { candidate ->
                    candidate.stableId == attempt.request.accountStableId &&
                        candidate.source == AccountSessionSource.AUTHENTICATED
                }
            if (
                snapshot.activeAccountId != attempt.request.accountStableId ||
                account == null
            ) {
                pendingRequest = attempt.request
                mutableUiState.value = failure(
                    request = attempt.request,
                    kind = TextPostWriteFailureKind.API,
                    outcomeUnknown = true,
                )
                return
            }
            val configuredSubmitter = submitter
            if (configuredSubmitter == null) {
                pendingRequest = attempt.request
                mutableUiState.value = failure(
                    request = attempt.request,
                    kind = TextPostWriteFailureKind.API,
                    outcomeUnknown = true,
                )
                return
            }
            pendingRequest = attempt.request
            mutableUiState.value = state(
                TextPostWritePhase.RECONCILING,
                attempt.request,
            )
            val result = try {
                configuredSubmitter.reconcile(
                    request = AuthenticatedTextPostSubmitRequest(
                        accountStableId = attempt.request.accountStableId,
                        community = attempt.request.community,
                        title = attempt.request.title,
                        text = attempt.request.text,
                        sendReplies = attempt.request.sendReplies,
                        nsfw = attempt.request.nsfw,
                        spoiler = attempt.request.spoiler,
                        linkUrl = attempt.request.linkUrl,
                        mediaUri = attempt.request.mediaUri,
                        mediaMimeType = attempt.request.mediaMimeType,
                        galleryItems = attempt.request.galleryItems.map { item ->
                            AuthenticatedTextPostGalleryItem(
                                mediaUri = item.mediaUri,
                                mediaMimeType = item.mediaMimeType,
                                caption = item.caption,
                                outboundUrl = item.outboundUrl,
                            )
                        },
                        flairId = attempt.request.flairId,
                        flairText = attempt.request.flairText,
                        requestKey = attempt.request.requestKey,
                        mediaAsGif = attempt.request.mediaAsGif,
                        crosspostFullname = attempt.request.crosspostFullname,
                    ),
                    username = account.username,
                    dispatchedAtEpochMs = dispatchedAtEpochMs,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedTextPostSubmitResult.Failed(
                    kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN),
                )
            }
            // CROSSPOST01A1B_RECONCILIATION_RESULT_FENCE_V1
            val acceptedResult = if (attempt.request.crosspostFullname != null) {
                validateCrosspostReconciliationResult(
                    request = attempt.request,
                    result = result,
                    accountStillActive = isActiveAuthenticatedAccount(attempt.request.accountStableId),
                )
            } else {
                result
            }
            applySubmitResult(attempt.request, acceptedResult)
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(request: TextPostWriteRequest): AuthBrowserLaunchResult {
        val awaitingRequest = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == TextPostWritePhase.AUTHORIZATION_REQUIRED
            }
            ?: return AuthBrowserLaunchResult.NotStarted
        if (awaitingRequest.accountStableId != request.accountStableId) {
            mutableUiState.value = failure(
                request = awaitingRequest,
                kind = TextPostWriteFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        pendingRequest = request
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            mutableUiState.value = failure(
                request = request,
                kind = TextPostWriteFailureKind.CONFIGURATION,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        if (!isActiveAuthenticatedAccount(request.accountStableId)) {
            mutableUiState.value = failure(
                request = request,
                kind = TextPostWriteFailureKind.ACCOUNT_CHANGED,
            )
            return AuthBrowserLaunchResult.NotStarted
        }

        mutableUiState.value = state(TextPostWritePhase.AUTHORIZING, request)
        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(
                request.accountStableId,
            )
            mutableUiState.value = state(TextPostWritePhase.AWAITING_CALLBACK, request)
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                request = request,
                kind = TextPostWriteFailureKind.AUTHORIZATION,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    suspend fun beginCapabilityAuthorization(accountStableId: String): AuthBrowserLaunchResult {
        val configuredCapability = capability
        val configuredClientMode = clientMode
        val normalizedAccountId = accountStableId.trim()
        if (
            configuredCapability == null ||
            configuredClientMode == null ||
            normalizedAccountId.isEmpty() ||
            ownsStoredOrPendingAuthorization() ||
            !isActiveAuthenticatedAccount(normalizedAccountId)
        ) {
            return AuthBrowserLaunchResult.NotStarted
        }
        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = configuredCapability.beginAuthorization(normalizedAccountId),
                clientMode = configuredClientMode,
            )
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val request = pendingRequest ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            request = request,
            kind = TextPostWriteFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun capabilityBrowserLaunchFailed() {
        capability?.cancelPendingAuthorization()
    }

    fun reset() {
        if (mutableUiState.value.inProgress) return
        pendingRequest = null
        mutableUiState.value = TextPostWriteUiState(
            completionRevision = completionRevision,
        )
    }

    fun cancelPendingAuthorization() {
        if (
            mutableUiState.value.phase !in setOf(
                TextPostWritePhase.AUTHORIZING,
                TextPostWritePhase.AWAITING_CALLBACK,
                TextPostWritePhase.AUTHORIZATION_REQUIRED,
            )
        ) {
            return
        }
        capability?.cancelPendingAuthorization()
        pendingRequest = null
        mutableUiState.value = TextPostWriteUiState(
            completionRevision = completionRevision,
        )
    }

    fun hasActiveAuthorizationRequest(): Boolean =
        pendingRequest != null && mutableUiState.value.phase in setOf(
            TextPostWritePhase.AUTHORIZING,
            TextPostWritePhase.AWAITING_CALLBACK,
        )

    fun ownsStoredOrPendingAuthorization(): Boolean =
        capability?.let { configured ->
            configured.hasStoredCallback() || configured.hasPendingAuthorization()
        } ?: false

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val ownsCallback = hasActiveAuthorizationRequest() || ownsStoredOrPendingAuthorization()
        if (!ownsCallback) return false

        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            // AUTH15_CANCEL_DROPS_OWNED_PENDING_RECORD_V1 (#129): the composer
            // flair and post-requirements coordinators only begin capability
            // authorizations and stay IDLE, so the phase-gated cancel below
            // returned early and their pending record blocked every later
            // Authorize. An owned record is dropped whatever the phase.
            if (ownsStoredOrPendingAuthorization()) capability?.cancelPendingAuthorization()
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability
        if (configuredCapability == null || !configuredCapability.hasStoredCallback()) {
            configuredCapability?.cancelPendingAuthorization()
            val request = pendingRequest
            mutableUiState.value = if (request == null) {
                TextPostWriteUiState(completionRevision = completionRevision)
            } else {
                failure(
                    request = request,
                    kind = TextPostWriteFailureKind.CALLBACK,
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
            mutableUiState.value = state(TextPostWritePhase.AUTHORIZING, request)
        }
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    mutableUiState.value = TextPostWriteUiState(
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
                        kind = TextPostWriteFailureKind.ACCOUNT_CHANGED,
                    )
                    return
                }
                mutableUiState.value = state(TextPostWritePhase.IDLE, request)
                // CROSSPOST01A1A_EXPLICIT_POST_AUTH_SUBMIT_V1
                // Crossposts require review after authorization; other post types retain their flow.
                if (request.crosspostFullname == null) submit(request)
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                if (request == null) {
                    mutableUiState.value = TextPostWriteUiState(
                        completionRevision = completionRevision,
                    )
                } else {
                    mutableUiState.value = failure(
                        request = request,
                        kind = result.kind.toTextPostFailureKind(),
                        retryable = result.retryable,
                    )
                }
            }
        }
    }

    private suspend fun applySubmitResult(
        request: TextPostWriteRequest,
        result: AuthenticatedTextPostSubmitResult,
    ) {
        when (result) {
            is AuthenticatedTextPostSubmitResult.Submitted ->
                clearAttempt(request.accountStableId, request.requestKey)
            is AuthenticatedTextPostSubmitResult.Failed ->
                if (result.kind != AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN) {
                    clearAttempt(request.accountStableId, request.requestKey)
                }
        }
        when (result) {
            is AuthenticatedTextPostSubmitResult.Submitted -> {
                completionRevision += 1L
                pendingRequest = null
                mutableUiState.value = TextPostWriteUiState(
                    phase = TextPostWritePhase.SUCCEEDED,
                    accountStableId = request.accountStableId,
                    requestKey = request.requestKey,
                    submittedPost = SubmittedTextPost(
                        fullname = result.post.fullname,
                        id = result.post.id,
                        community = result.post.community,
                        title = result.post.title,
                        text = result.post.text,
                        permalink = result.post.permalink,
                        linkUrl = request.linkUrl?.trim(),
                        linkPreviewImageUrl = request.linkPreviewImageUrl?.trim(),
                        mediaLocalUri = request.mediaUri?.trim(),
                        mediaMimeType = request.mediaMimeType?.trim(),
                        mediaRemoteImageUrl = result.post.remoteImageUri,
                        videoWidth = result.post.videoWidth,
                        videoHeight = result.post.videoHeight,
                        galleryItems = request.galleryItems.mapIndexed { index, item ->
                            SubmittedTextPostGalleryItem(
                                mediaLocalUri = item.mediaUri.trim(),
                                mediaMimeType = item.mediaMimeType.trim(),
                                mediaRemoteImageUrl = result.post.galleryRemoteImageUris
                                    .getOrNull(index),
                                caption = item.caption,
                                outboundUrl = item.outboundUrl.trim(),
                            )
                        },
                        flairText = request.flairText?.trim(),
                        nsfw = request.nsfw,
                        spoiler = request.spoiler,
                        mediaAsGif = request.mediaAsGif,
                        crosspostFullname = request.crosspostFullname
                            ?.let(::normalizeCrosspostSourceFullname),
                    ),
                    completionRevision = completionRevision,
                )
            }
            is AuthenticatedTextPostSubmitResult.Failed -> {
                mutableUiState.value = result.toUiState(request)
            }
        }
    }

    private fun AuthenticatedTextPostSubmitResult.Failed.toUiState(
        request: TextPostWriteRequest,
    ): TextPostWriteUiState = when (kind) {
        AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED ->
            state(TextPostWritePhase.AUTHORIZATION_REQUIRED, request)

        AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN ->
            failure(
                request = request,
                kind = TextPostWriteFailureKind.API,
                outcomeUnknown = true,
                failureDetails = failureDetails,
            )

        AuthenticatedTextPostSubmitFailureKind.NOT_POSTED ->
            failure(
                request = request,
                kind = TextPostWriteFailureKind.NOT_POSTED,
                retryable = true,
                failureDetails = failureDetails,
            )

        else -> failure(
            request = request,
            kind = kind.toTextPostFailureKind(),
            retryable = retryable,
            retryAfterMs = retryAfterMs,
            failureDetails = failureDetails,
            validationIssues = validationIssues.map { issue ->
                TextPostWriteValidationIssue(
                    field = issue.field.toTextPostValidationField(),
                    message = issue.message,
                )
            },
        )
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
        phase: TextPostWritePhase,
        request: TextPostWriteRequest,
    ): TextPostWriteUiState = TextPostWriteUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        requestKey = request.requestKey,
        completionRevision = completionRevision,
    )

    // OCB-448: a failed clear leaves a stale attempt that a later read-back
    // resolves; it must not crash the app or hide a post that succeeded.
    private suspend fun loadAttempt(accountStableId: String) =
        withContext(storeDispatcher) { attemptStore?.load(accountStableId) }

    private suspend fun clearAttempt(accountStableId: String, requestKey: String?) {
        try {
            withContext(storeDispatcher) { attemptStore?.clear(accountStableId, requestKey) }
        } catch (error: Exception) {
            // Left for the read-back; see above.
        }
    }

    private fun failure(
        request: TextPostWriteRequest,
        kind: TextPostWriteFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        outcomeUnknown: Boolean = false,
        validationIssues: List<TextPostWriteValidationIssue> = emptyList(),
        failureDetails: FailureDetails? = null,
    ): TextPostWriteUiState = TextPostWriteUiState(
        phase = if (outcomeUnknown) {
            TextPostWritePhase.OUTCOME_UNKNOWN
        } else {
            TextPostWritePhase.FAILED
        },
        accountStableId = request.accountStableId,
        requestKey = request.requestKey,
        failureKind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        failureDetails = failureDetails,
        validationIssues = validationIssues,
        completionRevision = completionRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toTextPostFailureKind():
    TextPostWriteFailureKind = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
        TextPostWriteFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.CALLBACK ->
        TextPostWriteFailureKind.CALLBACK
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> TextPostWriteFailureKind.AUTHORIZATION
    AuthCommentWriteCapabilityFailureKind.STORAGE ->
        TextPostWriteFailureKind.SESSION
}

private fun AuthenticatedTextPostSubmitFailureKind.toTextPostFailureKind():
    TextPostWriteFailureKind = when (this) {
    AuthenticatedTextPostSubmitFailureKind.VALIDATION ->
        TextPostWriteFailureKind.VALIDATION
    AuthenticatedTextPostSubmitFailureKind.ALREADY_SUBMITTING ->
        TextPostWriteFailureKind.ALREADY_SUBMITTING
    AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED ->
        TextPostWriteFailureKind.AUTHORIZATION
    AuthenticatedTextPostSubmitFailureKind.SESSION ->
        TextPostWriteFailureKind.SESSION
    AuthenticatedTextPostSubmitFailureKind.RATE_LIMIT ->
        TextPostWriteFailureKind.RATE_LIMIT
    AuthenticatedTextPostSubmitFailureKind.COMMUNITY_UNAVAILABLE ->
        TextPostWriteFailureKind.COMMUNITY_UNAVAILABLE
    AuthenticatedTextPostSubmitFailureKind.PERMISSION ->
        TextPostWriteFailureKind.PERMISSION
    AuthenticatedTextPostSubmitFailureKind.SUBMISSION_RESTRICTED ->
        TextPostWriteFailureKind.SUBMISSION_RESTRICTED
    AuthenticatedTextPostSubmitFailureKind.CHALLENGE_REQUIRED ->
        TextPostWriteFailureKind.CHALLENGE_REQUIRED
    AuthenticatedTextPostSubmitFailureKind.API ->
        TextPostWriteFailureKind.API
    AuthenticatedTextPostSubmitFailureKind.HTTP ->
        TextPostWriteFailureKind.HTTP
    AuthenticatedTextPostSubmitFailureKind.TRANSPORT ->
        TextPostWriteFailureKind.TRANSPORT
    AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN ->
        TextPostWriteFailureKind.API
    AuthenticatedTextPostSubmitFailureKind.NOT_POSTED ->
        TextPostWriteFailureKind.NOT_POSTED
}

private fun AuthenticatedTextPostValidationField.toTextPostValidationField():
    TextPostWriteValidationField = when (this) {
        AuthenticatedTextPostValidationField.COMMUNITY -> TextPostWriteValidationField.COMMUNITY
        AuthenticatedTextPostValidationField.TITLE -> TextPostWriteValidationField.TITLE
        AuthenticatedTextPostValidationField.FLAIR -> TextPostWriteValidationField.FLAIR
        AuthenticatedTextPostValidationField.BODY -> TextPostWriteValidationField.BODY
        AuthenticatedTextPostValidationField.LINK -> TextPostWriteValidationField.LINK
        AuthenticatedTextPostValidationField.MEDIA -> TextPostWriteValidationField.MEDIA
        AuthenticatedTextPostValidationField.GENERAL -> TextPostWriteValidationField.GENERAL
    }

// CROSSPOST01A1B_PENDING_RETAINED_WITHOUT_VERIFIED_SOURCE_V1
internal fun validateCrosspostReconciliationResult(
    request: TextPostWriteRequest,
    result: AuthenticatedTextPostSubmitResult,
    accountStillActive: Boolean,
): AuthenticatedTextPostSubmitResult {
    val source = request.crosspostFullname?.let {
        runCatching { normalizeCrosspostSourceFullname(it) }.getOrNull()
    }
    val submitted = result as? AuthenticatedTextPostSubmitResult.Submitted
    val valid = source != null && accountStillActive && submitted != null && runCatching {
        val post = submitted.post
        submitted.accountStableId == request.accountStableId &&
            post.verifiedCrosspostFullname == source &&
            post.fullname == "t3_${post.id}" &&
            normalizeCrosspostSourceFullname(post.fullname) == post.fullname &&
            post.fullname != source &&
            normalizeRedditTextPostCommunity(post.community) == normalizeRedditTextPostCommunity(request.community) &&
            post.title.trim() == request.title.trim() &&
            post.text.isEmpty() &&
            post.remoteImageUri == null && post.galleryRemoteImageUris.isEmpty() &&
            post.videoWidth == null && post.videoHeight == null &&
            post.permalink == "/r/${post.community}/comments/${post.id}/"
    }.getOrDefault(false)
    if (valid) return requireNotNull(submitted)
    // COMPOSER02_SUBMIT_ATTEMPT_ABSENCE_V1: a listing that shows no such post at
    // all is a verified answer, not an unresolved read.
    if (
        accountStillActive &&
        (result as? AuthenticatedTextPostSubmitResult.Failed)?.kind ==
        AuthenticatedTextPostSubmitFailureKind.NOT_POSTED
    ) {
        return result
    }
    // Every unresolved read outcome retains dispatch state; never free the duplicate-write guard.
    return AuthenticatedTextPostSubmitResult.Failed(
        kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
        failureDetails = (result as? AuthenticatedTextPostSubmitResult.Failed)?.failureDetails,
    )
}
