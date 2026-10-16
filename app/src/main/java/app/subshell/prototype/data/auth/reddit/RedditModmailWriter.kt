package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.normalizeRedditModmailId
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailFailureKind
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

// MODMAIL02_OWNER_BOUND_REPLY_INTERNAL_NOTE_WRITER_V1
// MODMAIL04A1_ARCHIVE_UNARCHIVE_WRITER_V1
// MODMAIL04A1R1_EMPTY_POST_TRANSPORT_V1
// MODMAIL05_TRIAGE_HIGHLIGHT_MUTE_WRITER_V1
// MODMAIL06_READ_UNREAD_WRITER_V1
internal data class AuthenticatedRedditModmailSendRequest(
    val accountStableId: String,
    val conversationId: String,
    val body: String,
    val internal: Boolean,
    val authorHidden: Boolean,
)

internal sealed interface AuthenticatedRedditModmailSendResult {
    data object Sent : AuthenticatedRedditModmailSendResult

    data object AuthorizationRequired : AuthenticatedRedditModmailSendResult

    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val retryable: Boolean = false,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
        val outcomeUnknown: Boolean = false,
    ) : AuthenticatedRedditModmailSendResult
}

internal data class AuthenticatedRedditModmailArchiveRequest(
    val accountStableId: String,
    val conversationId: String,
    val archived: Boolean,
)

internal sealed interface AuthenticatedRedditModmailArchiveResult {
    data object Applied : AuthenticatedRedditModmailArchiveResult
    data object AuthorizationRequired : AuthenticatedRedditModmailArchiveResult
    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
        val outcomeUnknown: Boolean = false,
    ) : AuthenticatedRedditModmailArchiveResult
}

internal data class AuthenticatedRedditModmailTriageRequest(
    val accountStableId: String,
    val conversationId: String,
    val action: RedditModmailTriageAction,
)

internal sealed interface AuthenticatedRedditModmailTriageResult {
    data object Applied : AuthenticatedRedditModmailTriageResult
    data object AuthorizationRequired : AuthenticatedRedditModmailTriageResult
    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
        val outcomeUnknown: Boolean = false,
    ) : AuthenticatedRedditModmailTriageResult
}

internal data class AuthenticatedRedditModmailReadStateRequest(
    val accountStableId: String,
    val conversationId: String,
    val unread: Boolean,
)

internal sealed interface AuthenticatedRedditModmailReadStateResult {
    data object Applied : AuthenticatedRedditModmailReadStateResult
    data object AuthorizationRequired : AuthenticatedRedditModmailReadStateResult
    data class Failed(
        val kind: AuthenticatedRedditModmailFailureKind,
        val httpStatusCode: Int? = null,
        val failureDetails: FailureDetails? = null,
        val outcomeUnknown: Boolean = false,
    ) : AuthenticatedRedditModmailReadStateResult
}

internal fun interface AuthenticatedRedditModmailWriter {
    suspend fun send(
        request: AuthenticatedRedditModmailSendRequest,
    ): AuthenticatedRedditModmailSendResult

    suspend fun setArchived(
        request: AuthenticatedRedditModmailArchiveRequest,
    ): AuthenticatedRedditModmailArchiveResult =
        AuthenticatedRedditModmailArchiveResult.Failed(
            kind = AuthenticatedRedditModmailFailureKind.CONFIGURATION,
        )

    suspend fun triage(
        request: AuthenticatedRedditModmailTriageRequest,
    ): AuthenticatedRedditModmailTriageResult =
        AuthenticatedRedditModmailTriageResult.Failed(
            kind = AuthenticatedRedditModmailFailureKind.CONFIGURATION,
        )


    suspend fun setReadState(
        request: AuthenticatedRedditModmailReadStateRequest,
    ): AuthenticatedRedditModmailReadStateResult =
        AuthenticatedRedditModmailReadStateResult.Failed(
            kind = AuthenticatedRedditModmailFailureKind.CONFIGURATION,
        )
}

internal class ConfiguredRedditModmailWriter(
    private val capability: AuthCommentWriteCapability,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditModmailWriter {
    private val mutationMutex = Mutex()

    override suspend fun send(
        request: AuthenticatedRedditModmailSendRequest,
    ): AuthenticatedRedditModmailSendResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val account = request.accountStableId.trim()
            val conversationId = runCatching {
                normalizeRedditModmailId(request.conversationId)
            }.getOrNull()
            val body = request.body.trim()
            if (
                account.isEmpty() ||
                conversationId == null ||
                body.isEmpty() ||
                body.length > MODMAIL_MESSAGE_MAX_LENGTH
            ) {
                return@withLock failed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                )
            }

            val session = when (
                val authorization = capability.ensureAuthorized(account)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired ->
                    return@withLock AuthenticatedRedditModmailSendResult.AuthorizationRequired
                is AuthSessionResult.AuthorizationDenied ->
                    return@withLock AuthenticatedRedditModmailSendResult.AuthorizationRequired
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock failed(AuthenticatedRedditModmailFailureKind.CONFIGURATION)
                AuthSessionResult.StorageFailure ->
                    return@withLock failed(
                        kind = AuthenticatedRedditModmailFailureKind.SESSION,
                        retryable = true,
                    )
                AuthSessionResult.NoPendingCallback ->
                    return@withLock failed(AuthenticatedRedditModmailFailureKind.SESSION)
                is AuthSessionResult.EndpointFailure ->
                    return@withLock failed(
                        kind = AuthenticatedRedditModmailFailureKind.SESSION,
                        retryable = authorization.result.retryable(),
                        failureDetails = authorization.result.toFailureDetails(),
                    )
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = client.postFormStatus(
                    redditModmailReplyFormRequest(
                        conversationId = conversationId,
                        body = body,
                        internal = request.internal,
                        authorHidden = request.authorHidden && !request.internal,
                    ),
                )
            ) {
                is NetworkResult.Success -> AuthenticatedRedditModmailSendResult.Sent
                is NetworkResult.HttpFailure -> {
                    // LOW1_403_IS_A_REFUSAL_NOT_A_MISSING_GRANT_V1 (OCB-617): only a 401 asks to authorize.
                    if (result.statusCode == 401) {
                        AuthenticatedRedditModmailSendResult.AuthorizationRequired
                    } else {
                        failed(
                            kind = AuthenticatedRedditModmailFailureKind.HTTP,
                            retryable = false,
                            httpStatusCode = result.statusCode,
                            failureDetails = redditWriteHttpFailureDetails(
                                result.statusCode,
                                result.retryable,
                                result.metadata,
                            ),
                            // DATALAYER07: the reply may have been stored.
                            outcomeUnknown = isRedditWriteOutcomeUnknownStatus(result.statusCode),
                        )
                    }
                }
                is NetworkResult.TransportFailure -> failed(
                    kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                    retryable = false,
                    failureDetails = redditWriteTransportFailureDetails(
                        result.kind,
                        result.retryable,
                    ),
                    outcomeUnknown = true,
                )
                is NetworkResult.ProtocolFailure -> failed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                    retryable = false,
                    failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    outcomeUnknown = true,
                )
            }
        }
    }

    override suspend fun setArchived(
        request: AuthenticatedRedditModmailArchiveRequest,
    ): AuthenticatedRedditModmailArchiveResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val account = request.accountStableId.trim()
            val conversationId = runCatching {
                normalizeRedditModmailId(request.conversationId)
            }.getOrNull()
            if (account.isEmpty() || conversationId == null) {
                return@withLock archiveFailed(AuthenticatedRedditModmailFailureKind.PROTOCOL)
            }

            val session = when (val authorization = capability.ensureAuthorized(account)) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock AuthenticatedRedditModmailArchiveResult.AuthorizationRequired
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock archiveFailed(AuthenticatedRedditModmailFailureKind.CONFIGURATION)
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withLock archiveFailed(AuthenticatedRedditModmailFailureKind.SESSION)
                is AuthSessionResult.EndpointFailure -> return@withLock archiveFailed(
                    kind = AuthenticatedRedditModmailFailureKind.SESSION,
                    failureDetails = authorization.result.toFailureDetails(),
                )
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = client.postEmptyFormStatus(
                    redditModmailArchivePathSegments(
                        conversationId = conversationId,
                        archived = request.archived,
                    ),
                )
            ) {
                is NetworkResult.Success -> AuthenticatedRedditModmailArchiveResult.Applied
                // LOW1_403_IS_A_REFUSAL_NOT_A_MISSING_GRANT_V1 (OCB-617): only a 401 asks to authorize.
                is NetworkResult.HttpFailure -> if (result.statusCode == 401) {
                    AuthenticatedRedditModmailArchiveResult.AuthorizationRequired
                } else {
                    archiveFailed(
                        kind = AuthenticatedRedditModmailFailureKind.HTTP,
                        httpStatusCode = result.statusCode,
                        // WRITEOUT01_MODMAIL_STATE_WRITES_OUTCOME_UNKNOWN_V1 (OCB-510)
                        outcomeUnknown = isRedditWriteOutcomeUnknownStatus(result.statusCode),
                        failureDetails = redditWriteHttpFailureDetails(
                            result.statusCode,
                            result.retryable,
                            result.metadata,
                        ),
                    )
                }
                is NetworkResult.TransportFailure -> archiveFailed(
                    kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                    failureDetails = redditWriteTransportFailureDetails(result.kind, result.retryable),
                    outcomeUnknown = true,
                )
                is NetworkResult.ProtocolFailure -> archiveFailed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                    failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    outcomeUnknown = true,
                )
            }
        }
    }


    override suspend fun triage(
        request: AuthenticatedRedditModmailTriageRequest,
    ): AuthenticatedRedditModmailTriageResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val account = request.accountStableId.trim()
            val conversationId = runCatching {
                normalizeRedditModmailId(request.conversationId)
            }.getOrNull()
            if (account.isEmpty() || conversationId == null) {
                return@withLock triageFailed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                )
            }

            val session = when (val authorization = capability.ensureAuthorized(account)) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock AuthenticatedRedditModmailTriageResult.AuthorizationRequired
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock triageFailed(AuthenticatedRedditModmailFailureKind.CONFIGURATION)
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withLock triageFailed(AuthenticatedRedditModmailFailureKind.SESSION)
                is AuthSessionResult.EndpointFailure -> return@withLock triageFailed(
                    kind = AuthenticatedRedditModmailFailureKind.SESSION,
                    failureDetails = authorization.result.toFailureDetails(),
                )
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            val result = when (request.action) {
                RedditModmailTriageAction.HIGHLIGHT ->
                    client.postEmptyFormStatus(
                        redditModmailHighlightPathSegments(conversationId),
                    )
                RedditModmailTriageAction.UNHIGHLIGHT ->
                    client.deleteStatus(
                        redditModmailHighlightPathSegments(conversationId),
                    )
                RedditModmailTriageAction.MUTE_3_DAYS,
                RedditModmailTriageAction.MUTE_7_DAYS,
                RedditModmailTriageAction.MUTE_28_DAYS,
                -> client.postFormStatus(
                    redditModmailMuteFormRequest(
                        conversationId = conversationId,
                        muteHours = requireNotNull(request.action.muteHours),
                    ),
                )
                RedditModmailTriageAction.UNMUTE ->
                    client.postEmptyFormStatus(
                        redditModmailMutePathSegments(
                            conversationId = conversationId,
                            muted = false,
                        ),
                    )
            }
            when (result) {
                is NetworkResult.Success -> AuthenticatedRedditModmailTriageResult.Applied
                // LOW1_403_IS_A_REFUSAL_NOT_A_MISSING_GRANT_V1 (OCB-617): only a 401 asks to authorize.
                is NetworkResult.HttpFailure -> if (result.statusCode == 401) {
                    AuthenticatedRedditModmailTriageResult.AuthorizationRequired
                } else {
                    triageFailed(
                        kind = AuthenticatedRedditModmailFailureKind.HTTP,
                        httpStatusCode = result.statusCode,
                        // WRITEOUT01_MODMAIL_STATE_WRITES_OUTCOME_UNKNOWN_V1 (OCB-510)
                        outcomeUnknown = isRedditWriteOutcomeUnknownStatus(result.statusCode),
                        failureDetails = redditWriteHttpFailureDetails(
                            result.statusCode,
                            result.retryable,
                            result.metadata,
                        ),
                    )
                }
                is NetworkResult.TransportFailure -> triageFailed(
                    kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                    failureDetails = redditWriteTransportFailureDetails(
                        result.kind,
                        result.retryable,
                    ),
                    outcomeUnknown = true,
                )
                is NetworkResult.ProtocolFailure -> triageFailed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                    failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    outcomeUnknown = true,
                )
            }
        }
    }

    override suspend fun setReadState(
        request: AuthenticatedRedditModmailReadStateRequest,
    ): AuthenticatedRedditModmailReadStateResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val account = request.accountStableId.trim()
            val conversationId = runCatching {
                normalizeRedditModmailId(request.conversationId)
            }.getOrNull()
            if (account.isEmpty() || conversationId == null) {
                return@withLock readStateFailed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                )
            }

            val session = when (val authorization = capability.ensureAuthorized(account)) {
                is AuthSessionResult.Authenticated -> authorization.session
                AuthSessionResult.ReauthenticationRequired,
                is AuthSessionResult.AuthorizationDenied,
                -> return@withLock AuthenticatedRedditModmailReadStateResult.AuthorizationRequired
                AuthSessionResult.ClientConfigurationMismatch ->
                    return@withLock readStateFailed(
                        AuthenticatedRedditModmailFailureKind.CONFIGURATION,
                    )
                AuthSessionResult.StorageFailure,
                AuthSessionResult.NoPendingCallback,
                -> return@withLock readStateFailed(
                    AuthenticatedRedditModmailFailureKind.SESSION,
                )
                is AuthSessionResult.EndpointFailure -> return@withLock readStateFailed(
                    kind = AuthenticatedRedditModmailFailureKind.SESSION,
                    failureDetails = authorization.result.toFailureDetails(),
                )
            }

            val client = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
            )
            when (
                val result = client.postFormStatus(
                    redditModmailReadStateFormRequest(
                        conversationIds = listOf(conversationId),
                        unread = request.unread,
                    ),
                )
            ) {
                is NetworkResult.Success -> AuthenticatedRedditModmailReadStateResult.Applied
                is NetworkResult.HttpFailure -> {
                    // LOW1_403_IS_A_REFUSAL_NOT_A_MISSING_GRANT_V1 (OCB-617): only a 401 asks to authorize.
                    if (result.statusCode == 401) {
                        AuthenticatedRedditModmailReadStateResult.AuthorizationRequired
                    } else {
                        readStateFailed(
                            kind = AuthenticatedRedditModmailFailureKind.HTTP,
                            httpStatusCode = result.statusCode,
                            outcomeUnknown = isRedditWriteOutcomeUnknownStatus(result.statusCode),
                            failureDetails = redditWriteHttpFailureDetails(
                                result.statusCode,
                                result.retryable,
                                result.metadata,
                            ),
                        )
                    }
                }
                is NetworkResult.TransportFailure -> readStateFailed(
                    kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                    failureDetails = redditWriteTransportFailureDetails(
                        result.kind,
                        result.retryable,
                    ),
                    outcomeUnknown = true,
                )
                is NetworkResult.ProtocolFailure -> readStateFailed(
                    kind = AuthenticatedRedditModmailFailureKind.PROTOCOL,
                    failureDetails = redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    outcomeUnknown = true,
                )
            }
        }
    }

    private fun readStateFailed(
        kind: AuthenticatedRedditModmailFailureKind,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
        outcomeUnknown: Boolean = false,
    ) = AuthenticatedRedditModmailReadStateResult.Failed(
        kind = kind,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        outcomeUnknown = outcomeUnknown,
    )

    private fun triageFailed(
        kind: AuthenticatedRedditModmailFailureKind,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
        outcomeUnknown: Boolean = false,
    ) = AuthenticatedRedditModmailTriageResult.Failed(
        kind = kind,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        outcomeUnknown = outcomeUnknown,
    )

    private fun archiveFailed(
        kind: AuthenticatedRedditModmailFailureKind,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
        outcomeUnknown: Boolean = false,
    ) = AuthenticatedRedditModmailArchiveResult.Failed(
        kind = kind,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        outcomeUnknown = outcomeUnknown,
    )

    private fun failed(
        kind: AuthenticatedRedditModmailFailureKind,
        retryable: Boolean = false,
        httpStatusCode: Int? = null,
        failureDetails: FailureDetails? = null,
        outcomeUnknown: Boolean = false,
    ) = AuthenticatedRedditModmailSendResult.Failed(
        kind = kind,
        retryable = retryable,
        httpStatusCode = httpStatusCode,
        failureDetails = failureDetails,
        outcomeUnknown = outcomeUnknown,
    )
}


internal fun redditModmailReadStateFormRequest(
    conversationIds: List<String>,
    unread: Boolean,
): FormPostRequest {
    val ids = conversationIds
        .asSequence()
        .map(::normalizeRedditModmailId)
        .distinct()
        .toList()
    require(ids.isNotEmpty())
    require(ids.size <= REDDIT_MODMAIL_READ_STATE_MAX_CONVERSATIONS)
    return FormPostRequest(
        pathSegments = listOf(
            "api",
            "mod",
            "conversations",
            if (unread) "unread" else "read",
        ),
        fields = listOf("conversationIds" to ids.joinToString(",")),
    )
}

internal fun redditModmailHighlightPathSegments(
    conversationId: String,
): List<String> = listOf(
    "api",
    "mod",
    "conversations",
    normalizeRedditModmailId(conversationId),
    "highlight",
)

internal fun redditModmailMutePathSegments(
    conversationId: String,
    muted: Boolean,
): List<String> = listOf(
    "api",
    "mod",
    "conversations",
    normalizeRedditModmailId(conversationId),
    if (muted) "mute" else "unmute",
)

internal fun redditModmailMuteFormRequest(
    conversationId: String,
    muteHours: Int,
): FormPostRequest {
    require(muteHours in REDDIT_MODMAIL_MUTE_HOURS)
    return FormPostRequest(
        pathSegments = redditModmailMutePathSegments(
            conversationId = conversationId,
            muted = true,
        ),
        fields = listOf("num_hours" to muteHours.toString()),
    )
}

internal fun redditModmailArchivePathSegments(
    conversationId: String,
    archived: Boolean,
): List<String> = listOf(
    "api",
    "mod",
    "conversations",
    normalizeRedditModmailId(conversationId),
    if (archived) "archive" else "unarchive",
)

internal fun redditModmailReplyFormRequest(
    conversationId: String,
    body: String,
    internal: Boolean,
    authorHidden: Boolean,
): FormPostRequest = FormPostRequest(
    pathSegments = listOf(
        "api",
        "mod",
        "conversations",
        normalizeRedditModmailId(conversationId),
    ),
    fields = listOf(
        "body" to body.trim(),
        "isAuthorHidden" to authorHidden.toString(),
        "isInternal" to internal.toString(),
    ),
)

private fun OAuthEndpointResult<*>.retryable(): Boolean = when (this) {
    is OAuthEndpointResult.HttpFailure -> retryable
    is OAuthEndpointResult.TransportFailure -> retryable
    is OAuthEndpointResult.ProtocolFailure -> false
    is OAuthEndpointResult.Success -> false
}

internal const val MODMAIL_MESSAGE_MAX_LENGTH = 10_000
private const val REDDIT_MODMAIL_READ_STATE_MAX_CONVERSATIONS = 100
private val REDDIT_MODMAIL_MUTE_HOURS = setOf(72, 168, 672)
