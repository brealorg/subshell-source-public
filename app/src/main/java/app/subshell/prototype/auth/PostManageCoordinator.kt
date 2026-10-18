package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.OwnContentType
import app.subshell.prototype.data.account.PostManageFailureKind
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManagePhase
import app.subshell.prototype.data.account.PostManageSnapshot
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.applyPostManageMutation
import app.subshell.prototype.data.account.ownContentActions
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.PostManageAuthorizationContext
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageLoadResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageMutationResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManager
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class PostManageCoordinator(
    private val manager: AuthenticatedPostManager?,
    private val accountDirectory: AccountSessionDirectory?,
    private val editCapability: AuthCommentWriteCapability?,
    private val editClientMode: OAuthClientMode?,
    private val flairCapability: AuthCommentWriteCapability?,
    private val flairClientMode: OAuthClientMode?,
    private val moderationCapability: AuthCommentWriteCapability?,
    private val moderationClientMode: OAuthClientMode?,
) {
    private val operationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(PostManageUiState())
    private var pendingMutation: PostManageMutationRequest? = null
    private var completionRevision = 0L

    val uiState: StateFlow<PostManageUiState> = mutableUiState.asStateFlow()

    suspend fun load(target: PostManageTarget) {
        // LOW9_LOAD_KEEPS_A_CHANGE_WAITING_FOR_AUTHORIZATION_V1 (OCB-435): the
        // sheet calls load() each time it opens; a change for this post that
        // waits for authorization is kept so the callback still sends it.
        val waiting = mutableUiState.value
        if (
            pendingMutation != null &&
            waiting.target == target &&
            waiting.phase in setOf(
                PostManagePhase.AUTHORIZATION_REQUIRED,
                PostManagePhase.AUTHORIZING,
                PostManagePhase.AWAITING_CALLBACK,
            )
        ) {
            return
        }
        if (!operationMutex.tryLock()) return
        try {
            pendingMutation = null
            val account = activeAuthenticatedAccount(target.accountStableId)
            if (account == null) {
                mutableUiState.value =
                    failure(target, PostManageFailureKind.ACCOUNT_CHANGED)
                return
            }
            if (!isOwner(account.username, target.postAuthor)) {
                mutableUiState.value =
                    failure(target, PostManageFailureKind.NOT_OWNER)
                return
            }
            val configuredManager = manager ?: run {
                mutableUiState.value =
                    failure(target, PostManageFailureKind.CONFIGURATION)
                return
            }
            mutableUiState.value = PostManageUiState(
                phase = PostManagePhase.LOADING,
                target = target,
                completionRevision = completionRevision,
            )
            val result = try {
                configuredManager.load(target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedPostManageLoadResult.Failed(
                    kind = AuthenticatedPostManageFailureKind.TRANSPORT,
                    retryable = true,
                    failureDetails = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }
            when (result) {
                is AuthenticatedPostManageLoadResult.Loaded -> {
                    if (!isOwner(account.username, result.snapshot.author)) {
                        mutableUiState.value =
                            failure(target, PostManageFailureKind.NOT_OWNER)
                        return
                    }
                    mutableUiState.value = PostManageUiState(
                        phase = PostManagePhase.IDLE,
                        target = target,
                        snapshot = result.snapshot,
                        completionRevision = completionRevision,
                    )
                }
                is AuthenticatedPostManageLoadResult.Failed ->
                    mutableUiState.value = failure(
                        target = target,
                        kind = result.kind.toFailureKind(),
                        retryable = result.retryable,
                        retryAfterMs = result.retryAfterMs,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
            }
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun mutate(request: PostManageMutationRequest) {
        if (!operationMutex.tryLock()) return
        try {
            pendingMutation = request
            val account = activeAuthenticatedAccount(request.target.accountStableId)
            if (account == null) {
                mutableUiState.value = failure(
                    target = request.target,
                    kind = PostManageFailureKind.ACCOUNT_CHANGED,
                    operation = request.operation,
                    snapshot = mutableUiState.value.snapshot,
                )
                return
            }
            if (!isOwner(account.username, request.target.postAuthor)) {
                mutableUiState.value = failure(
                    target = request.target,
                    kind = PostManageFailureKind.NOT_OWNER,
                    operation = request.operation,
                    snapshot = mutableUiState.value.snapshot,
                )
                return
            }
            val configuredManager = manager ?: run {
                mutableUiState.value = failure(
                    target = request.target,
                    kind = PostManageFailureKind.CONFIGURATION,
                    operation = request.operation,
                    snapshot = mutableUiState.value.snapshot,
                )
                return
            }
            val previousSnapshot = mutableUiState.value.snapshot
            mutableUiState.value = PostManageUiState(
                phase = PostManagePhase.SENDING,
                target = request.target,
                operation = request.operation,
                snapshot = previousSnapshot,
                completionRevision = completionRevision,
            )
            val result = try {
                configuredManager.mutate(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedPostManageMutationResult.Failed(
                    kind = AuthenticatedPostManageFailureKind.TRANSPORT,
                    retryable = false,
                    failureDetails = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }
            when (result) {
                AuthenticatedPostManageMutationResult.Updated -> {
                    completionRevision += 1L
                    pendingMutation = null
                    mutableUiState.value = PostManageUiState(
                        phase = PostManagePhase.SUCCEEDED,
                        target = request.target,
                        operation = request.operation,
                        snapshot = previousSnapshot?.let { snapshot ->
                            applyPostManageMutation(snapshot, request)
                        },
                        completionRevision = completionRevision,
                    )
                }
                is AuthenticatedPostManageMutationResult.Failed -> {
                    // LOW10_POST_MANAGE_LOST_ANSWER_IS_READ_BACK_V1 (OCB-436): the
                    // change may already be on Reddit; the post is loaded again
                    // (nothing is sent). If it shows the requested value the write
                    // counts as done, otherwise the sheet shows Reddit's values
                    // (the writes are absolute sets, so Retry is safe).
                    if (result.kind == AuthenticatedPostManageFailureKind.TRANSPORT) {
                        val reloaded = try {
                            configuredManager.load(request.target)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (reloaded is AuthenticatedPostManageLoadResult.Loaded) {
                            val current = reloaded.snapshot
                            if (applyPostManageMutation(current, request) == current) {
                                completionRevision += 1L
                                pendingMutation = null
                                mutableUiState.value = PostManageUiState(
                                    phase = PostManagePhase.SUCCEEDED,
                                    target = request.target,
                                    operation = request.operation,
                                    snapshot = current,
                                    completionRevision = completionRevision,
                                )
                            } else {
                                mutableUiState.value = failure(
                                    target = request.target,
                                    kind = result.kind.toFailureKind(),
                                    operation = request.operation,
                                    snapshot = current,
                                    retryable = true,
                                    httpStatusCode = result.httpStatusCode,
                                    failureDetails = result.failureDetails,
                                )
                            }
                            return
                        }
                    }
                    mutableUiState.value =
                        if (
                            result.kind ==
                            AuthenticatedPostManageFailureKind.AUTHORIZATION_REQUIRED
                        ) {
                            PostManageUiState(
                                phase = PostManagePhase.AUTHORIZATION_REQUIRED,
                                target = request.target,
                                operation = request.operation,
                                snapshot = previousSnapshot,
                                completionRevision = completionRevision,
                            )
                        } else {
                            failure(
                                target = request.target,
                                kind = result.kind.toFailureKind(),
                                operation = request.operation,
                                snapshot = previousSnapshot,
                                retryable = result.retryable,
                                retryAfterMs = result.retryAfterMs,
                                httpStatusCode = result.httpStatusCode,
                                failureDetails = result.failureDetails,
                            )
                        }
                }
            }
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingMutation?.takeIf {
            mutableUiState.value.phase == PostManagePhase.AUTHORIZATION_REQUIRED
        } ?: return AuthBrowserLaunchResult.NotStarted
        val capability = capabilityFor(request.operation)
            ?: return configurationFailure(request)
        val clientMode = clientModeFor(request.operation)
            ?: return configurationFailure(request)
        if (activeAuthenticatedAccount(request.target.accountStableId) == null) {
            mutableUiState.value = failure(
                target = request.target,
                kind = PostManageFailureKind.ACCOUNT_CHANGED,
                operation = request.operation,
                snapshot = mutableUiState.value.snapshot,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        mutableUiState.value = mutableUiState.value.copy(
            phase = PostManagePhase.AUTHORIZING,
            failureKind = null,
        )
        return try {
            val authorizationUrl =
                capability.beginAuthorization(request.target.accountStableId)
            mutableUiState.value = mutableUiState.value.copy(
                phase = PostManagePhase.AWAITING_CALLBACK,
            )
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = authorizationUrl,
                clientMode = clientMode,
            )
        } catch (_: Exception) {
            capability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                target = request.target,
                kind = PostManageFailureKind.AUTHORIZATION,
                operation = request.operation,
                snapshot = mutableUiState.value.snapshot,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val request = pendingMutation ?: return
        capabilityFor(request.operation)?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            target = request.target,
            kind = PostManageFailureKind.BROWSER_LAUNCH,
            operation = request.operation,
            snapshot = mutableUiState.value.snapshot,
            retryable = true,
        )
    }

    fun cancelPendingAuthorization() {
        val request = pendingMutation ?: return
        if (
            mutableUiState.value.phase !in setOf(
                PostManagePhase.AUTHORIZATION_REQUIRED,
                PostManagePhase.AUTHORIZING,
                PostManagePhase.AWAITING_CALLBACK,
            )
        ) {
            return
        }
        capabilityFor(request.operation)?.cancelPendingAuthorization()
        pendingMutation = null
        mutableUiState.value = PostManageUiState(
            target = request.target,
            snapshot = mutableUiState.value.snapshot,
            completionRevision = completionRevision,
        )
    }

    fun reset() {
        if (mutableUiState.value.inProgress) return
        pendingMutation = null
        mutableUiState.value =
            PostManageUiState(completionRevision = completionRevision)
    }

    suspend fun handlePersistedAuthorizationRoute(
        resultToken: String,
        contextKey: String?,
    ): Boolean {
        val context =
            PostManageAuthorizationContext.fromWireKey(contextKey)
                ?: return true
        val capability = authorizationCapabilityFor(context)
            ?: run {
                failPendingRequestForContext(
                    context = context,
                    kind = PostManageFailureKind.CONFIGURATION,
                )
                return true
            }

        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            capability.cancelPendingAuthorization()
            cancelPendingRequestForContext(context)
            return true
        }

        if (!capability.hasStoredCallback()) {
            capability.cancelPendingAuthorization()
            failPendingRequestForContext(
                context = context,
                kind = PostManageFailureKind.CALLBACK,
                retryable = true,
            )
            return true
        }

        completePersistedAuthorization(
            context = context,
            capability = capability,
        )
        return true
    }

    suspend fun resumePersistedAuthorizationRouteIfPresent(
        contextKey: String?,
    ): Boolean {
        val context =
            PostManageAuthorizationContext.fromWireKey(contextKey)
                ?: return true
        val capability = authorizationCapabilityFor(context)
            ?: run {
                failPendingRequestForContext(
                    context = context,
                    kind = PostManageFailureKind.CONFIGURATION,
                )
                return true
            }

        return when {
            capability.hasStoredCallback() -> {
                completePersistedAuthorization(
                    context = context,
                    capability = capability,
                )
                true
            }
            capability.hasPendingAuthorization() -> true
            else -> true
        }
    }

    private suspend fun completePersistedAuthorization(
        context: PostManageAuthorizationContext,
        capability: AuthCommentWriteCapability,
    ) {
        val request = pendingMutation
        val matchingRequest = request?.takeIf {
            PostManageAuthorizationContext.fromOperation(it.operation) == context
        }

        if (matchingRequest != null) {
            mutableUiState.value =
                mutableUiState.value.copy(
                    phase = PostManagePhase.AUTHORIZING,
                )
        }

        when (val result = capability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    return
                }

                if (matchingRequest == null) {
                    pendingMutation = null
                    mutableUiState.value = failure(
                        target = request.target,
                        kind = PostManageFailureKind.CALLBACK,
                        operation = request.operation,
                        snapshot = mutableUiState.value.snapshot,
                    )
                    return
                }

                if (
                    result.accountStableId != matchingRequest.target.accountStableId ||
                    activeAuthenticatedAccount(
                        matchingRequest.target.accountStableId,
                    ) == null
                ) {
                    mutableUiState.value = failure(
                        target = matchingRequest.target,
                        kind = PostManageFailureKind.ACCOUNT_CHANGED,
                        operation = matchingRequest.operation,
                        snapshot = mutableUiState.value.snapshot,
                    )
                    return
                }

                mutableUiState.value =
                    mutableUiState.value.copy(
                        phase = PostManagePhase.IDLE,
                    )
                mutate(matchingRequest)
            }

            is AuthCommentWriteCapabilityResult.Failed -> {
                if (request == null) {
                    return
                }

                if (matchingRequest == null) {
                    pendingMutation = null
                    mutableUiState.value = failure(
                        target = request.target,
                        kind = PostManageFailureKind.CALLBACK,
                        operation = request.operation,
                        snapshot = mutableUiState.value.snapshot,
                    )
                    return
                }

                mutableUiState.value = failure(
                    target = matchingRequest.target,
                    kind = result.kind.toFailureKind(),
                    operation = matchingRequest.operation,
                    snapshot = mutableUiState.value.snapshot,
                    retryable = result.retryable,
                )
            }
        }
    }

    private fun cancelPendingRequestForContext(
        context: PostManageAuthorizationContext,
    ) {
        val request = pendingMutation ?: return
        if (
            PostManageAuthorizationContext.fromOperation(request.operation) !=
            context
        ) {
            pendingMutation = null
            mutableUiState.value = failure(
                target = request.target,
                kind = PostManageFailureKind.CALLBACK,
                operation = request.operation,
                snapshot = mutableUiState.value.snapshot,
            )
            return
        }

        pendingMutation = null
        mutableUiState.value = PostManageUiState(
            target = request.target,
            snapshot = mutableUiState.value.snapshot,
            completionRevision = completionRevision,
        )
    }

    private fun failPendingRequestForContext(
        context: PostManageAuthorizationContext,
        kind: PostManageFailureKind,
        retryable: Boolean = false,
    ) {
        val request = pendingMutation ?: return
        val matches =
            PostManageAuthorizationContext.fromOperation(request.operation) ==
                context

        if (!matches) {
            pendingMutation = null
        }

        mutableUiState.value = failure(
            target = request.target,
            kind = if (matches) kind else PostManageFailureKind.CALLBACK,
            operation = request.operation,
            snapshot = mutableUiState.value.snapshot,
            retryable = matches && retryable,
        )
    }

    private fun authorizationCapabilityFor(
        context: PostManageAuthorizationContext,
    ): AuthCommentWriteCapability? = when (context) {
        PostManageAuthorizationContext.FLAIR -> flairCapability
        PostManageAuthorizationContext.MODERATION -> moderationCapability
        PostManageAuthorizationContext.EDIT -> editCapability
    }

    private fun capabilityFor(
        operation: PostManageOperation,
    ): AuthCommentWriteCapability? =
        authorizationCapabilityFor(
            PostManageAuthorizationContext.fromOperation(operation),
        )

    private fun clientModeFor(
        operation: PostManageOperation,
    ): OAuthClientMode? = when (operation) {
        PostManageOperation.FLAIR_ACCESS,
        PostManageOperation.SET_FLAIR,
        -> flairClientMode
        PostManageOperation.SET_NSFW,
        PostManageOperation.SET_SPOILER,
        -> moderationClientMode
        PostManageOperation.SET_SEND_REPLIES -> editClientMode
    }

    private suspend fun configurationFailure(
        request: PostManageMutationRequest,
    ): AuthBrowserLaunchResult {
        mutableUiState.value = failure(
            target = request.target,
            kind = PostManageFailureKind.CONFIGURATION,
            operation = request.operation,
            snapshot = mutableUiState.value.snapshot,
        )
        return AuthBrowserLaunchResult.NotStarted
    }

    private fun activeAuthenticatedAccount(accountStableId: String) =
        accountDirectory?.snapshot()?.let { snapshot ->
            snapshot.accounts.firstOrNull { account ->
                account.stableId == accountStableId &&
                    account.stableId == snapshot.activeAccountId &&
                    account.source == AccountSessionSource.AUTHENTICATED
            }
        }

    private fun isOwner(activeUsername: String?, author: String?): Boolean =
        ownContentActions(
            activeUsername = activeUsername,
            author = author,
            contentType = OwnContentType.OTHER_POST,
        ).canDelete

    private fun failure(
        target: PostManageTarget,
        kind: PostManageFailureKind,
        operation: PostManageOperation? = null,
        snapshot: PostManageSnapshot? = null,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = PostManageUiState(
        phase = PostManagePhase.FAILED,
        target = target,
        operation = operation,
        snapshot = snapshot,
        failureKind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        completionRevision = completionRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toFailureKind() =
    when (this) {
        AuthCommentWriteCapabilityFailureKind.ACCOUNT ->
            PostManageFailureKind.ACCOUNT_CHANGED
        AuthCommentWriteCapabilityFailureKind.CALLBACK ->
            PostManageFailureKind.CALLBACK
        AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
        AuthCommentWriteCapabilityFailureKind.IDENTITY,
        AuthCommentWriteCapabilityFailureKind.SCOPE,
        -> PostManageFailureKind.AUTHORIZATION
        AuthCommentWriteCapabilityFailureKind.STORAGE ->
            PostManageFailureKind.SESSION
    }

private fun AuthenticatedPostManageFailureKind.toFailureKind() =
    when (this) {
        AuthenticatedPostManageFailureKind.VALIDATION ->
            PostManageFailureKind.PROTOCOL
        AuthenticatedPostManageFailureKind.ALREADY_MUTATING ->
            PostManageFailureKind.ALREADY_MUTATING
        AuthenticatedPostManageFailureKind.AUTHORIZATION_REQUIRED ->
            PostManageFailureKind.AUTHORIZATION
        AuthenticatedPostManageFailureKind.SESSION ->
            PostManageFailureKind.SESSION
        AuthenticatedPostManageFailureKind.RATE_LIMIT ->
            PostManageFailureKind.RATE_LIMIT
        AuthenticatedPostManageFailureKind.HTTP ->
            PostManageFailureKind.HTTP
        AuthenticatedPostManageFailureKind.TRANSPORT ->
            PostManageFailureKind.TRANSPORT
        AuthenticatedPostManageFailureKind.PROTOCOL ->
            PostManageFailureKind.PROTOCOL
    }
