package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.OwnContentType
import io.github.brealorg.subshell.data.account.PostDeleteFailureKind
import io.github.brealorg.subshell.data.account.PostDeletePhase
import io.github.brealorg.subshell.data.account.PostDeleteRequest
import io.github.brealorg.subshell.data.account.PostDeleteUiState
import io.github.brealorg.subshell.data.account.ownContentActions
import io.github.brealorg.subshell.data.auth.AuthBrowserLaunchResult
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapabilityFailureKind
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapabilityResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleteFailureKind
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleteRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleteResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleter
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.brealorg.subshell.data.cache.DeletedPostCacheRemover
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import io.github.brealorg.subshell.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

internal class PostDeleteCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val deleter: AuthenticatedPostDeleter?,
    private val accountDirectory: AccountSessionDirectory?,
    private val clientMode: OAuthClientMode?,
    private val deletedPostCacheRemover: DeletedPostCacheRemover? = null,
    private val cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val operationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(PostDeleteUiState())
    private var pendingRequest: PostDeleteRequest? = null
    private var completionRevision = 0L
    val uiState: StateFlow<PostDeleteUiState> = mutableUiState.asStateFlow()

    suspend fun delete(request: PostDeleteRequest) {
        if (!operationMutex.tryLock()) return
        try {
            pendingRequest = request
            val activeAccount = activeAuthenticatedAccount(request.accountStableId)
            if (activeAccount == null) {
                mutableUiState.value = failure(request, PostDeleteFailureKind.ACCOUNT_CHANGED)
                return
            }
            if (!ownContentActions(
                    activeUsername = activeAccount.username,
                    author = request.postAuthor,
                    contentType = OwnContentType.OTHER_POST,
                ).canDelete
            ) {
                mutableUiState.value = failure(request, PostDeleteFailureKind.NOT_OWNER)
                return
            }
            val configuredDeleter = deleter ?: run {
                mutableUiState.value = failure(request, PostDeleteFailureKind.CONFIGURATION)
                return
            }
            mutableUiState.value = state(PostDeletePhase.SENDING, request)
            val result = try {
                configuredDeleter.delete(
                    AuthenticatedPostDeleteRequest(request.accountStableId, request.postFullname),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // The request may already have reached Reddit.
                AuthenticatedPostDeleteResult.Failed(
                    kind = AuthenticatedPostDeleteFailureKind.OUTCOME_UNKNOWN,
                    retryable = false,
                    failureDetails = FailureDetails(
                        retryable = false,
                        localKind = LocalFailureKind.UNKNOWN,
                    ).withExceptionClass(error),
                )
            }
            applyResult(request, result)
            if (result is AuthenticatedPostDeleteResult.Deleted) evictDeletedPost(request)
        } finally {
            operationMutex.unlock()
        }
    }

    // H3_POST_DELETE_CHECK_AGAIN_READS_ONLY_V1: after an unknown outcome the
    // user can ask again whether Reddit still shows the post; never re-sent here.
    suspend fun checkAgain() {
        val request = pendingRequest
            ?.takeIf {
                mutableUiState.value.phase == PostDeletePhase.FAILED &&
                    mutableUiState.value.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN
            }
            ?: return
        if (!operationMutex.tryLock()) return
        try {
            if (activeAuthenticatedAccount(request.accountStableId) == null) {
                mutableUiState.value = failure(request, PostDeleteFailureKind.ACCOUNT_CHANGED)
                return
            }
            val configuredDeleter = deleter ?: return
            mutableUiState.value = state(PostDeletePhase.CHECKING, request)
            val result = try {
                configuredDeleter.verify(
                    AuthenticatedPostDeleteRequest(request.accountStableId, request.postFullname),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AuthenticatedPostDeleteResult.Failed(
                    kind = AuthenticatedPostDeleteFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = FailureDetails(localKind = LocalFailureKind.UNKNOWN).withExceptionClass(error),
                )
            }
            applyResult(request, result)
            if (result is AuthenticatedPostDeleteResult.Deleted) evictDeletedPost(request)
        } finally {
            operationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val request = pendingRequest?.takeIf {
            mutableUiState.value.phase == PostDeletePhase.AUTHORIZATION_REQUIRED
        } ?: return AuthBrowserLaunchResult.NotStarted
        val configuredCapability = capability
        val configuredClientMode = clientMode
        if (configuredCapability == null || configuredClientMode == null) {
            mutableUiState.value = failure(request, PostDeleteFailureKind.CONFIGURATION)
            return AuthBrowserLaunchResult.NotStarted
        }
        if (activeAuthenticatedAccount(request.accountStableId) == null) {
            mutableUiState.value = failure(request, PostDeleteFailureKind.ACCOUNT_CHANGED)
            return AuthBrowserLaunchResult.NotStarted
        }
        mutableUiState.value = state(PostDeletePhase.AUTHORIZING, request)
        return try {
            val authorizationUrl = configuredCapability.beginAuthorization(request.accountStableId)
            mutableUiState.value = state(PostDeletePhase.AWAITING_CALLBACK, request)
            AuthBrowserLaunchResult.Ready(authorizationUrl, configuredClientMode)
        } catch (_: Exception) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = failure(
                request,
                PostDeleteFailureKind.AUTHORIZATION,
                retryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        val request = pendingRequest ?: return
        capability?.cancelPendingAuthorization()
        mutableUiState.value = failure(
            request,
            PostDeleteFailureKind.BROWSER_LAUNCH,
            retryable = true,
        )
    }

    fun reset() {
        if (mutableUiState.value.inProgress) return
        pendingRequest = null
        mutableUiState.value = PostDeleteUiState(completionRevision = completionRevision)
    }

    fun cancelPendingAuthorization() {
        if (mutableUiState.value.phase !in setOf(
                PostDeletePhase.AUTHORIZING,
                PostDeletePhase.AWAITING_CALLBACK,
                PostDeletePhase.AUTHORIZATION_REQUIRED,
            )
        ) return
        capability?.cancelPendingAuthorization()
        pendingRequest = null
        mutableUiState.value = PostDeleteUiState(completionRevision = completionRevision)
    }

    fun hasActiveAuthorizationRequest(): Boolean = pendingRequest != null &&
        mutableUiState.value.phase in setOf(
            PostDeletePhase.AUTHORIZING,
            PostDeletePhase.AWAITING_CALLBACK,
        )

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        if (!hasActiveAuthorizationRequest()) return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            cancelPendingAuthorization()
            return true
        }
        val configuredCapability = capability
        if (configuredCapability == null || !configuredCapability.hasStoredCallback()) {
            configuredCapability?.cancelPendingAuthorization()
            val request = pendingRequest
            mutableUiState.value = if (request == null) {
                PostDeleteUiState(completionRevision = completionRevision)
            } else {
                failure(request, PostDeleteFailureKind.CALLBACK, retryable = true)
            }
            return true
        }
        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        if (!hasActiveAuthorizationRequest()) return false
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
        if (request != null) mutableUiState.value = state(PostDeletePhase.AUTHORIZING, request)
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (request == null) {
                    mutableUiState.value = PostDeleteUiState(completionRevision = completionRevision)
                    return
                }
                if (
                    result.accountStableId != request.accountStableId ||
                    activeAuthenticatedAccount(request.accountStableId) == null
                ) {
                    mutableUiState.value = failure(request, PostDeleteFailureKind.ACCOUNT_CHANGED)
                    return
                }
                mutableUiState.value = state(PostDeletePhase.IDLE, request)
                delete(request)
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = if (request == null) {
                    PostDeleteUiState(completionRevision = completionRevision)
                } else {
                    failure(request, result.kind.toPostDeleteFailureKind(), result.retryable)
                }
            }
        }
    }

    // OCB102_DELETED_POST_LEAVES_THE_CACHE_V1: best effort; a failure leaves
    // the cached pages for the next refresh to replace.
    private suspend fun evictDeletedPost(request: PostDeleteRequest) {
        val remover = deletedPostCacheRemover ?: return
        try {
            withContext(cacheDispatcher) { remover.removeDeletedPost(request.postFullname) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private fun applyResult(
        request: PostDeleteRequest,
        result: AuthenticatedPostDeleteResult,
    ) {
        when (result) {
            is AuthenticatedPostDeleteResult.Deleted -> {
                completionRevision += 1L
                pendingRequest = null
                mutableUiState.value = PostDeleteUiState(
                    phase = PostDeletePhase.SUCCEEDED,
                    accountStableId = request.accountStableId,
                    postFullname = request.postFullname,
                    completionRevision = completionRevision,
                )
            }
            is AuthenticatedPostDeleteResult.Failed -> {
                mutableUiState.value = if (
                    result.kind == AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED
                ) {
                    state(PostDeletePhase.AUTHORIZATION_REQUIRED, request)
                } else {
                    failure(
                        request,
                        result.kind.toPostDeleteFailureKind(),
                        result.retryable,
                        retryAfterMs = result.retryAfterMs,
                        httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                }
            }
        }
    }

    private fun activeAuthenticatedAccount(accountStableId: String) =
        accountDirectory?.snapshot()?.let { snapshot ->
            snapshot.accounts.firstOrNull { account ->
                account.stableId == accountStableId &&
                    account.stableId == snapshot.activeAccountId &&
                    account.source == AccountSessionSource.AUTHENTICATED
            }
        }

    private fun state(phase: PostDeletePhase, request: PostDeleteRequest) = PostDeleteUiState(
        phase = phase,
        accountStableId = request.accountStableId,
        postFullname = request.postFullname,
        completionRevision = completionRevision,
    )

    private fun failure(
        request: PostDeleteRequest,
        kind: PostDeleteFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
    ) = PostDeleteUiState(
        phase = PostDeletePhase.FAILED,
        accountStableId = request.accountStableId,
        postFullname = request.postFullname,
        failureKind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        completionRevision = completionRevision,
    )
}

private fun AuthCommentWriteCapabilityFailureKind.toPostDeleteFailureKind() = when (this) {
    AuthCommentWriteCapabilityFailureKind.ACCOUNT -> PostDeleteFailureKind.ACCOUNT_CHANGED
    AuthCommentWriteCapabilityFailureKind.CALLBACK -> PostDeleteFailureKind.CALLBACK
    AuthCommentWriteCapabilityFailureKind.TOKEN_EXCHANGE,
    AuthCommentWriteCapabilityFailureKind.IDENTITY,
    AuthCommentWriteCapabilityFailureKind.SCOPE,
    -> PostDeleteFailureKind.AUTHORIZATION
    AuthCommentWriteCapabilityFailureKind.STORAGE -> PostDeleteFailureKind.SESSION
}

private fun AuthenticatedPostDeleteFailureKind.toPostDeleteFailureKind() = when (this) {
    AuthenticatedPostDeleteFailureKind.VALIDATION -> PostDeleteFailureKind.PROTOCOL
    AuthenticatedPostDeleteFailureKind.ALREADY_DELETING -> PostDeleteFailureKind.ALREADY_DELETING
    AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED -> PostDeleteFailureKind.AUTHORIZATION
    AuthenticatedPostDeleteFailureKind.SESSION -> PostDeleteFailureKind.SESSION
    AuthenticatedPostDeleteFailureKind.RATE_LIMIT -> PostDeleteFailureKind.RATE_LIMIT
    AuthenticatedPostDeleteFailureKind.HTTP -> PostDeleteFailureKind.HTTP
    AuthenticatedPostDeleteFailureKind.TRANSPORT -> PostDeleteFailureKind.TRANSPORT
    AuthenticatedPostDeleteFailureKind.PROTOCOL -> PostDeleteFailureKind.PROTOCOL
    AuthenticatedPostDeleteFailureKind.OUTCOME_UNKNOWN -> PostDeleteFailureKind.OUTCOME_UNKNOWN
    AuthenticatedPostDeleteFailureKind.NOT_DELETED -> PostDeleteFailureKind.NOT_DELETED
}
