package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.normalizeCommentEditFullname
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
import app.subshell.prototype.data.network.reddit.RedditCommentEditNetworkDataSource
import app.subshell.prototype.data.network.reddit.RichTextEditStamps
import app.subshell.prototype.data.network.reddit.redditEditedSince
import app.subshell.prototype.data.network.reddit.RedditCommentEditNetworkResult
import app.subshell.prototype.data.network.reddit.RedditCommentEditReadback
import app.subshell.prototype.data.network.reddit.normalizeRedditCommentEditText
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import okhttp3.Call

internal data class AuthenticatedCommentEditRequest(
    val accountStableId: String,
    val commentFullname: String,
    val text: String,
)

internal enum class AuthenticatedCommentEditFailureKind {
    VALIDATION,
    ALREADY_EDITING,
    AUTHORIZATION_REQUIRED,
    SESSION,
    RATE_LIMIT,
    API,
    HTTP,
    TRANSPORT,
    // H1_COMMENT_EDIT_OUTCOME_UNKNOWN_V1: Reddit may have saved the edit; the
    // read-back could not tell. Check again reads, it never re-sends.
    OUTCOME_UNKNOWN,
    // The read-back shows the comment without the edit: saving again is safe.
    NOT_SAVED,
}

internal sealed interface AuthenticatedCommentEditResult {
    data class Edited(
        val accountStableId: String,
        val commentFullname: String,
        val text: String,
        val metadata: NetworkResponseMetadata,
    ) : AuthenticatedCommentEditResult

    data class Failed(
        val kind: AuthenticatedCommentEditFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val providerErrorCode: String? = null,
        val providerErrorMessage: String? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedCommentEditResult
}

internal fun interface AuthenticatedCommentEditor {
    suspend fun edit(
        request: AuthenticatedCommentEditRequest,
    ): AuthenticatedCommentEditResult

    /** Reads what Reddit shows for the comment; never sends the edit. */
    suspend fun verify(
        request: AuthenticatedCommentEditRequest,
    ): AuthenticatedCommentEditResult = AuthenticatedCommentEditResult.Failed(
        AuthenticatedCommentEditFailureKind.OUTCOME_UNKNOWN,
    )
}

internal class ConfiguredRedditCommentEditor(
    private val capability: AuthCommentWriteTokenSource,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val networkClock: NetworkClock = SystemNetworkClock,
    private val readBackDelaysMs: List<Long> = COMMENT_EDIT_READBACK_DELAYS_MS,
) : AuthenticatedCommentEditor {
    private val editMutex = Mutex()
    private val richTextEditStamps = RichTextEditStamps()

    override suspend fun edit(
        request: AuthenticatedCommentEditRequest,
    ): AuthenticatedCommentEditResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedCommentEditFailureKind.VALIDATION)
        if (!editMutex.tryLock()) {
            return failed(AuthenticatedCommentEditFailureKind.ALREADY_EDITING)
        }

        return try {
            val dataSource = when (val authorized = authorizedDataSource(normalized)) {
                is AuthorizedCommentEdit.Refused -> return authorized.failure
                is AuthorizedCommentEdit.Ready -> authorized.dataSource
            }

            when (
                val result = dataSource.edit(
                    commentFullname = normalized.commentFullname,
                    text = normalized.text,
                )
            ) {
                is RedditCommentEditNetworkResult.Edited ->
                    AuthenticatedCommentEditResult.Edited(
                        accountStableId = normalized.accountStableId,
                        commentFullname = normalized.commentFullname,
                        text = normalized.text,
                        metadata = result.metadata,
                    )

                is RedditCommentEditNetworkResult.ApiFailure -> {
                    val providerError = result.errors.firstOrNull()
                    val providerCode = sanitizeRedditCommentEditProviderValue(
                        providerError?.code,
                    )?.uppercase()
                    val providerMessage = sanitizeRedditCommentEditProviderValue(
                        providerError?.message,
                    )
                    val failureDetails = redditWriteProviderFailureDetails(
                        code = providerError?.code,
                        message = providerError?.message,
                        retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = providerCode == "RATELIMIT"),
                    )
                    when (providerCode) {
                        "RATELIMIT" -> failed(
                            kind = AuthenticatedCommentEditFailureKind.RATE_LIMIT,
                            retryable = true,
                            providerErrorCode = providerCode,
                            providerErrorMessage = providerMessage,
                            failureDetails = failureDetails.copy(retryable = true),
                        )
                        "NO_TEXT",
                        "TEXT_TOO_LONG",
                        "BAD_COMMENT",
                        -> failed(
                            kind = AuthenticatedCommentEditFailureKind.VALIDATION,
                            providerErrorCode = providerCode,
                            providerErrorMessage = providerMessage,
                            failureDetails = failureDetails,
                        )
                        else -> failed(
                            kind = AuthenticatedCommentEditFailureKind.API,
                            providerErrorCode = providerCode,
                            providerErrorMessage = providerMessage,
                            failureDetails = failureDetails,
                        )
                    }
                }

                is RedditCommentEditNetworkResult.HttpFailure -> {
                    val details = redditWriteHttpFailureDetails(
                        statusCode = result.statusCode,
                        retryable = result.retryable,
                        metadata = result.metadata,
                        providerReason = result.providerReason,
                    )
                    when {
                        result.statusCode == 401 || result.statusCode == 403 ->
                            failed(
                                AuthenticatedCommentEditFailureKind.AUTHORIZATION_REQUIRED,
                                failureDetails = details,
                            )
                        result.statusCode == 429 ->
                            failed(
                                kind = AuthenticatedCommentEditFailureKind.RATE_LIMIT,
                                retryable = true,
                                retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = true),
                                failureDetails = details.copy(retryable = true),
                            )
                        isRedditWriteOutcomeUnknownStatus(result.statusCode) ->
                            confirmAfterUnknownOutcome(dataSource, normalized, details)
                        else ->
                            failed(
                                kind = AuthenticatedCommentEditFailureKind.HTTP,
                                retryable = result.retryable,
                                failureDetails = details,
                            )
                    }
                }

                is RedditCommentEditNetworkResult.TransportFailure ->
                    if (result.kind in COMMENT_EDIT_NOT_SENT_TRANSPORT_KINDS) {
                        failed(
                            kind = AuthenticatedCommentEditFailureKind.TRANSPORT,
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
                is RedditCommentEditNetworkResult.NotSent ->
                    failed(
                        kind = AuthenticatedCommentEditFailureKind.NOT_SAVED,
                        retryable = true,
                        providerErrorCode = result.reason,
                    )

                is RedditCommentEditNetworkResult.ProtocolFailure ->
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
        request: AuthenticatedCommentEditRequest,
    ): AuthenticatedCommentEditResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedCommentEditFailureKind.VALIDATION)
        val dataSource = when (val authorized = authorizedDataSource(normalized)) {
            is AuthorizedCommentEdit.Refused -> return authorized.failure
            is AuthorizedCommentEdit.Ready -> authorized.dataSource
        }
        return confirmAfterUnknownOutcome(dataSource, normalized, failureDetails = null, delaysMs = listOf(0L))
    }

    // H1_COMMENT_EDIT_READBACK_V1: after an unknown outcome, read the comment
    // back instead of re-sending. The edited text means saved; the comment
    // without it means not saved; no answer stays unknown.
    private suspend fun confirmAfterUnknownOutcome(
        dataSource: RedditCommentEditNetworkDataSource,
        normalized: AuthenticatedCommentEditRequest,
        failureDetails: FailureDetails?,
        delaysMs: List<Long> = readBackDelaysMs,
    ): AuthenticatedCommentEditResult {
        // EDITREADBACK01_EDITED_STAMP_V1: a rich-text edit counts as saved
        // when Reddit's edited stamp moved past the one read before sending.
        val editedBefore = dataSource.richTextEditedBefore
            ?.also { stamp ->
                richTextEditStamps.remember(normalized.accountStableId, normalized.commentFullname, normalized.text, stamp)
            }
            ?: richTextEditStamps.editedBefore(normalized.accountStableId, normalized.commentFullname, normalized.text)
        var last: RedditCommentEditReadback = RedditCommentEditReadback.Unreadable
        for (delayMs in delaysMs) {
            if (delayMs > 0) delay(delayMs)
            last = dataSource.readBack(normalized.commentFullname)
            val readBack = last
            if (
                readBack is RedditCommentEditReadback.Body &&
                (sameEditedText(readBack.text, normalized.text) || redditEditedSince(editedBefore, readBack.edited))
            ) {
                return AuthenticatedCommentEditResult.Edited(
                    accountStableId = normalized.accountStableId,
                    commentFullname = normalized.commentFullname,
                    text = normalized.text,
                    metadata = readBack.metadata,
                )
            }
        }
        return when (last) {
            is RedditCommentEditReadback.Body -> failed(
                kind = AuthenticatedCommentEditFailureKind.NOT_SAVED,
                retryable = true,
                failureDetails = failureDetails?.copy(retryable = true),
            )
            RedditCommentEditReadback.Absent -> failed(
                kind = AuthenticatedCommentEditFailureKind.NOT_SAVED,
                failureDetails = failureDetails?.copy(retryable = false),
            )
            RedditCommentEditReadback.Unreadable -> failed(
                kind = AuthenticatedCommentEditFailureKind.OUTCOME_UNKNOWN,
                failureDetails = failureDetails?.copy(retryable = false),
            )
        }
    }

    private suspend fun authorizedDataSource(
        normalized: AuthenticatedCommentEditRequest,
    ): AuthorizedCommentEdit {
        val session = when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
            is AuthSessionResult.Authenticated -> authorization.session
            is AuthSessionResult.EndpointFailure -> return AuthorizedCommentEdit.Refused(mapSessionFailure(authorization.result))
            AuthSessionResult.ClientConfigurationMismatch,
            AuthSessionResult.NoPendingCallback,
            AuthSessionResult.ReauthenticationRequired,
            AuthSessionResult.StorageFailure,
            is AuthSessionResult.AuthorizationDenied,
            -> return AuthorizedCommentEdit.Refused(failed(AuthenticatedCommentEditFailureKind.AUTHORIZATION_REQUIRED))
        }
        val identity = session.identity
            ?: return AuthorizedCommentEdit.Refused(failed(AuthenticatedCommentEditFailureKind.AUTHORIZATION_REQUIRED))
        if (
            authenticatedAccountId(
                provider = session.provider,
                clientMode = session.clientMode,
                providerAccountId = identity.providerAccountId,
            ) != normalized.accountStableId ||
            !session.tokens.grants(RedditOAuth.liveCommentEditScopes)
        ) {
            return AuthorizedCommentEdit.Refused(failed(AuthenticatedCommentEditFailureKind.AUTHORIZATION_REQUIRED))
        }

        return AuthorizedCommentEdit.Ready(RedditCommentEditNetworkDataSource(
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
        request: AuthenticatedCommentEditRequest,
    ): AuthenticatedCommentEditRequest? = runCatching {
        AuthenticatedCommentEditRequest(
            accountStableId = request.accountStableId.trim().also { accountStableId ->
                require(accountStableId.isNotEmpty()) { "Account stable ID cannot be blank" }
            },
            commentFullname = normalizeCommentEditFullname(request.commentFullname),
            text = normalizeRedditCommentEditText(request.text),
        )
    }.getOrNull()

    private fun mapSessionFailure(
        result: OAuthEndpointResult<Nothing>,
    ): AuthenticatedCommentEditResult.Failed = when (result) {
        is OAuthEndpointResult.HttpFailure -> failed(
            kind = if (result.statusCode == 401 || result.statusCode == 403) {
                AuthenticatedCommentEditFailureKind.AUTHORIZATION_REQUIRED
            } else {
                AuthenticatedCommentEditFailureKind.SESSION
            },
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.TransportFailure -> failed(
            kind = AuthenticatedCommentEditFailureKind.SESSION,
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )
        is OAuthEndpointResult.ProtocolFailure ->
            failed(
                AuthenticatedCommentEditFailureKind.SESSION,
                failureDetails = result.toFailureDetails(),
            )
        is OAuthEndpointResult.Success ->
            failed(AuthenticatedCommentEditFailureKind.SESSION)
    }

    private fun failed(
        kind: AuthenticatedCommentEditFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        providerErrorCode: String? = null,
        providerErrorMessage: String? = null,
        failureDetails: FailureDetails? = null,
    ) = AuthenticatedCommentEditResult.Failed(
        kind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        providerErrorCode = providerErrorCode,
        providerErrorMessage = providerErrorMessage,
        failureDetails = failureDetails,
    )
}

internal fun sanitizeRedditCommentEditProviderValue(value: String?): String? = value
    ?.trim()
    ?.replace(Regex("\\s+"), " ")
    ?.takeIf(String::isNotEmpty)
    ?.take(REDDIT_COMMENT_EDIT_PROVIDER_DETAIL_MAX_CHARS)

private const val REDDIT_COMMENT_EDIT_PROVIDER_DETAIL_MAX_CHARS = 240

private sealed interface AuthorizedCommentEdit {
    data class Ready(val dataSource: RedditCommentEditNetworkDataSource) : AuthorizedCommentEdit
    data class Refused(val failure: AuthenticatedCommentEditResult.Failed) : AuthorizedCommentEdit
}

internal fun sameEditedText(shown: String, sent: String): Boolean =
    shown.replace("\r\n", "\n").trim() == sent.replace("\r\n", "\n").trim()

private val COMMENT_EDIT_NOT_SENT_TRANSPORT_KINDS = setOf(
    NetworkTransportFailureKind.DNS,
    NetworkTransportFailureKind.CONNECT,
    NetworkTransportFailureKind.TLS,
)

internal val COMMENT_EDIT_READBACK_DELAYS_MS = listOf(0L, 1_000L, 2_500L)
