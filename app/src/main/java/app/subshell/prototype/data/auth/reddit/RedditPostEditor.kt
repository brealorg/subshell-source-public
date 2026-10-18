package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.normalizePostEditFullname
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteProviderFailureDetails
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.error.redditWriteRetryAfterMs
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SystemNetworkClock
import app.subshell.prototype.data.network.reddit.RedditPostEditNetworkDataSource
import app.subshell.prototype.data.network.reddit.RichTextEditStamps
import app.subshell.prototype.data.network.reddit.redditEditedSince
import app.subshell.prototype.data.network.reddit.RedditPostEditNetworkResult
import app.subshell.prototype.data.network.reddit.RedditPostEditReadback
import app.subshell.prototype.data.network.reddit.normalizeRedditPostEditText
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import okhttp3.Call

internal data class AuthenticatedPostEditRequest(
    val accountStableId: String,
    val postFullname: String,
    val text: String,
)

internal enum class AuthenticatedPostEditFailureKind {
    VALIDATION,
    ALREADY_EDITING,
    AUTHORIZATION_REQUIRED,
    SESSION,
    RATE_LIMIT,
    API,
    HTTP,
    TRANSPORT,
    // H2_POST_EDIT_OUTCOME_UNKNOWN_V1: Reddit may have saved the edit; the
    // read-back could not tell. Check again reads, it never re-sends.
    OUTCOME_UNKNOWN,
    // The read-back shows the post without the edit: saving again is safe.
    NOT_SAVED,
}

internal sealed interface AuthenticatedPostEditResult {
    data class Edited(
        val accountStableId: String,
        val postFullname: String,
        val text: String,
        val metadata: NetworkResponseMetadata,
    ) : AuthenticatedPostEditResult

    data class Failed(
        val kind: AuthenticatedPostEditFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val providerErrorCode: String? = null,
        val providerErrorMessage: String? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedPostEditResult
}

internal fun interface AuthenticatedPostEditor {
    suspend fun edit(
        request: AuthenticatedPostEditRequest,
    ): AuthenticatedPostEditResult

    /** Reads what Reddit shows for the post; never sends the edit. */
    suspend fun verify(
        request: AuthenticatedPostEditRequest,
    ): AuthenticatedPostEditResult = AuthenticatedPostEditResult.Failed(
        AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN,
    )
}

internal class ConfiguredRedditPostEditor(
    private val capability: AuthCommentWriteTokenSource,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val networkClock: NetworkClock = SystemNetworkClock,
    private val readBackDelaysMs: List<Long> = POST_EDIT_READBACK_DELAYS_MS,
) : AuthenticatedPostEditor {
    private val editMutex = Mutex()
    private val richTextEditStamps = RichTextEditStamps()

    override suspend fun edit(
        request: AuthenticatedPostEditRequest,
    ): AuthenticatedPostEditResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedPostEditFailureKind.VALIDATION)
        if (!editMutex.tryLock()) {
            return failed(AuthenticatedPostEditFailureKind.ALREADY_EDITING)
        }

        return try {
            val dataSource = when (val authorized = authorizedDataSource(normalized)) {
                is AuthorizedPostEdit.Refused -> return authorized.failure
                is AuthorizedPostEdit.Ready -> authorized.dataSource
            }

            when (
                val result = dataSource.edit(
                    postFullname = normalized.postFullname,
                    text = normalized.text,
                )
            ) {
                is RedditPostEditNetworkResult.Edited ->
                    AuthenticatedPostEditResult.Edited(
                        accountStableId = normalized.accountStableId,
                        postFullname = normalized.postFullname,
                        text = normalized.text,
                        metadata = result.metadata,
                    )

                is RedditPostEditNetworkResult.ApiFailure -> {
                    val providerError = result.errors.firstOrNull()
                    val providerCode = sanitizeRedditPostEditProviderValue(
                        providerError?.code,
                    )?.uppercase()
                    val providerMessage = sanitizeRedditPostEditProviderValue(
                        providerError?.message,
                    )
                    val failureDetails = redditWriteProviderFailureDetails(
                        code = providerError?.code,
                        message = providerError?.message,
                        retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = providerCode == "RATELIMIT"),
                    )
                    when (providerCode) {
                        "RATELIMIT" -> failed(
                            kind = AuthenticatedPostEditFailureKind.RATE_LIMIT,
                            retryable = true,
                            providerErrorCode = providerCode,
                            providerErrorMessage = providerMessage,
                            failureDetails = failureDetails.copy(retryable = true),
                        )
                        "NO_TEXT",
                        "TEXT_TOO_LONG",
                        "BAD_POST",
                        "BAD_COMMENT",
                        -> failed(
                            kind = AuthenticatedPostEditFailureKind.VALIDATION,
                            providerErrorCode = providerCode,
                            providerErrorMessage = providerMessage,
                            failureDetails = failureDetails,
                        )
                        else -> failed(
                            kind = AuthenticatedPostEditFailureKind.API,
                            providerErrorCode = providerCode,
                            providerErrorMessage = providerMessage,
                            failureDetails = failureDetails,
                        )
                    }
                }

                is RedditPostEditNetworkResult.HttpFailure -> {
                    val details = redditWriteHttpFailureDetails(
                        statusCode = result.statusCode,
                        retryable = result.retryable,
                        metadata = result.metadata,
                        providerReason = result.providerReason,
                    )
                    when {
                        result.statusCode == 401 || result.statusCode == 403 ->
                            failed(
                                AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED,
                                failureDetails = details,
                            )
                        result.statusCode == 429 ->
                            failed(
                                kind = AuthenticatedPostEditFailureKind.RATE_LIMIT,
                                retryable = true,
                                retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = true),
                                failureDetails = details.copy(retryable = true),
                            )
                        isRedditWriteOutcomeUnknownStatus(result.statusCode) ->
                            confirmAfterUnknownOutcome(dataSource, normalized, details)
                        else ->
                            failed(
                                kind = AuthenticatedPostEditFailureKind.HTTP,
                                retryable = result.retryable,
                                failureDetails = details,
                            )
                    }
                }

                is RedditPostEditNetworkResult.TransportFailure ->
                    if (result.kind in POST_EDIT_NOT_SENT_TRANSPORT_KINDS) {
                        failed(
                            kind = AuthenticatedPostEditFailureKind.TRANSPORT,
                            retryable = result.retryable,
                            failureDetails = redditWriteTransportFailureDetails(
                                result.kind,
                                result.retryable,
                            ),
                        )
                    } else {
                        // A timeout or a broken connection after sending: the edit may be saved.
                        confirmAfterUnknownOutcome(
                            dataSource,
                            normalized,
                            redditWriteTransportFailureDetails(result.kind, result.retryable),
                        )
                    }

                // ISSUE8B_RICHTEXT_MEDIA_EDIT_V1: nothing reached editusertext.
                is RedditPostEditNetworkResult.NotSent ->
                    failed(
                        kind = AuthenticatedPostEditFailureKind.NOT_SAVED,
                        retryable = true,
                        providerErrorCode = result.reason,
                    )

                is RedditPostEditNetworkResult.ProtocolFailure ->
                    // Reddit answered, but not in a form subshell could read.
                    confirmAfterUnknownOutcome(
                        dataSource,
                        normalized,
                        redditWriteProtocolFailureDetails(result.kind, result.jsonPath),
                    )
            }
        } finally {
            editMutex.unlock()
        }
    }

    override suspend fun verify(
        request: AuthenticatedPostEditRequest,
    ): AuthenticatedPostEditResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedPostEditFailureKind.VALIDATION)
        val dataSource = when (val authorized = authorizedDataSource(normalized)) {
            is AuthorizedPostEdit.Refused -> return authorized.failure
            is AuthorizedPostEdit.Ready -> authorized.dataSource
        }
        return confirmAfterUnknownOutcome(dataSource, normalized, failureDetails = null, delaysMs = listOf(0L))
    }

    // H2_POST_EDIT_READBACK_V1: after an unknown outcome, read the post
    // back instead of re-sending. The edited text means saved; the post
    // without it means not saved; no answer stays unknown.
    private suspend fun confirmAfterUnknownOutcome(
        dataSource: RedditPostEditNetworkDataSource,
        normalized: AuthenticatedPostEditRequest,
        failureDetails: FailureDetails?,
        delaysMs: List<Long> = readBackDelaysMs,
    ): AuthenticatedPostEditResult {
        // EDITREADBACK01_EDITED_STAMP_V1: a rich-text edit counts as saved
        // when Reddit's edited stamp moved past the one read before sending.
        val editedBefore = dataSource.richTextEditedBefore
            ?.also { stamp ->
                richTextEditStamps.remember(normalized.accountStableId, normalized.postFullname, normalized.text, stamp)
            }
            ?: richTextEditStamps.editedBefore(normalized.accountStableId, normalized.postFullname, normalized.text)
        var last: RedditPostEditReadback = RedditPostEditReadback.Unreadable
        for (delayMs in delaysMs) {
            if (delayMs > 0) delay(delayMs)
            last = dataSource.readBack(normalized.postFullname)
            val readBack = last
            if (
                readBack is RedditPostEditReadback.Body &&
                (sameEditedText(readBack.text, normalized.text) || redditEditedSince(editedBefore, readBack.edited))
            ) {
                return AuthenticatedPostEditResult.Edited(
                    accountStableId = normalized.accountStableId,
                    postFullname = normalized.postFullname,
                    text = normalized.text,
                    metadata = readBack.metadata,
                )
            }
        }
        return when (last) {
            is RedditPostEditReadback.Body -> failed(
                kind = AuthenticatedPostEditFailureKind.NOT_SAVED,
                retryable = true,
                failureDetails = failureDetails?.copy(retryable = true),
            )
            RedditPostEditReadback.Absent -> failed(
                kind = AuthenticatedPostEditFailureKind.NOT_SAVED,
                failureDetails = failureDetails?.copy(retryable = false),
            )
            RedditPostEditReadback.Unreadable -> failed(
                kind = AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN,
                failureDetails = failureDetails?.copy(retryable = false),
            )
        }
    }

    private suspend fun authorizedDataSource(
        normalized: AuthenticatedPostEditRequest,
    ): AuthorizedPostEdit {
        val session = when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
            is AuthSessionResult.Authenticated -> authorization.session
            is AuthSessionResult.EndpointFailure -> return AuthorizedPostEdit.Refused(mapSessionFailure(authorization.result))
            // CORRECT02_STORAGE_FAILURE_IS_RETRYABLE_SESSION_V1 (OCB-514): a store
            // read that failed is a retryable session problem, not a new sign-in.
            AuthSessionResult.StorageFailure,
            AuthSessionResult.NoPendingCallback,
            -> return AuthorizedPostEdit.Refused(failed(AuthenticatedPostEditFailureKind.SESSION, retryable = true))
            AuthSessionResult.ClientConfigurationMismatch,
            AuthSessionResult.ReauthenticationRequired,
            is AuthSessionResult.AuthorizationDenied,
            -> return AuthorizedPostEdit.Refused(failed(AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED))
        }
        val identity = session.identity
            ?: return AuthorizedPostEdit.Refused(failed(AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED))
        if (
            authenticatedAccountId(
                provider = session.provider,
                clientMode = session.clientMode,
                providerAccountId = identity.providerAccountId,
            ) != normalized.accountStableId ||
            !session.tokens.grants(RedditOAuth.liveCommentEditScopes)
        ) {
            return AuthorizedPostEdit.Refused(failed(AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED))
        }

        return AuthorizedPostEdit.Ready(RedditPostEditNetworkDataSource(
            SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = json,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
                clock = networkClock,
            ),
        ))
    }

    private fun normalizeRequest(
        request: AuthenticatedPostEditRequest,
    ): AuthenticatedPostEditRequest? = runCatching {
        AuthenticatedPostEditRequest(
            accountStableId = request.accountStableId.trim().also { accountStableId ->
                require(accountStableId.isNotEmpty()) { "Account stable ID cannot be blank" }
            },
            postFullname = normalizePostEditFullname(request.postFullname),
            text = normalizeRedditPostEditText(request.text),
        )
    }.getOrNull()

    private fun mapSessionFailure(
        result: OAuthEndpointResult<Nothing>,
    ): AuthenticatedPostEditResult.Failed = when (result) {
        is OAuthEndpointResult.HttpFailure -> failed(
            kind = if (result.statusCode == 401 || result.statusCode == 403) {
                AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED
            } else {
                AuthenticatedPostEditFailureKind.SESSION
            },
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.TransportFailure -> failed(
            kind = AuthenticatedPostEditFailureKind.SESSION,
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.ProtocolFailure ->
            failed(
                AuthenticatedPostEditFailureKind.SESSION,
                failureDetails = result.toFailureDetails(),
            )
        is OAuthEndpointResult.Success ->
            failed(AuthenticatedPostEditFailureKind.SESSION)
    }

    private fun failed(
        kind: AuthenticatedPostEditFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        providerErrorCode: String? = null,
        providerErrorMessage: String? = null,
        failureDetails: FailureDetails? = null,
    ) = AuthenticatedPostEditResult.Failed(
        kind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        providerErrorCode = providerErrorCode,
        providerErrorMessage = providerErrorMessage,
        failureDetails = failureDetails,
    )
}

internal fun sanitizeRedditPostEditProviderValue(value: String?): String? = value
    ?.trim()
    ?.replace(Regex("\\s+"), " ")
    ?.takeIf(String::isNotEmpty)
    ?.take(REDDIT_POST_EDIT_PROVIDER_DETAIL_MAX_CHARS)

private const val REDDIT_POST_EDIT_PROVIDER_DETAIL_MAX_CHARS = 240

private sealed interface AuthorizedPostEdit {
    data class Ready(val dataSource: RedditPostEditNetworkDataSource) : AuthorizedPostEdit
    data class Refused(val failure: AuthenticatedPostEditResult.Failed) : AuthorizedPostEdit
}

private val POST_EDIT_NOT_SENT_TRANSPORT_KINDS = setOf(
    NetworkTransportFailureKind.DNS,
    NetworkTransportFailureKind.CONNECT,
    NetworkTransportFailureKind.TLS,
)

internal val POST_EDIT_READBACK_DELAYS_MS = listOf(0L, 1_000L, 2_500L)
