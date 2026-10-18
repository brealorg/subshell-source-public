package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.error.isRedditWriteOutcomeUnknownStatus
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.normalizedMessageReplyRequest
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.error.redditWriteRetryAfterMs
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditMediaBinaryUploadResult
import app.subshell.prototype.data.network.reddit.RedditMediaUploadNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditMessageReplyNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditMessageReplyNetworkResult
import app.subshell.prototype.data.network.reddit.prepareRedditInlineMediaRichText
import app.subshell.prototype.data.network.reddit.normalizeRedditPrivateMessageFullname
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient

internal sealed interface AuthenticatedRedditMessageReplyResult {
    data class Accepted(
        val request: MessageReplyRequest,
        val sentBody: String,
        val metadata: NetworkResponseMetadata,
        val sentMediaUri: String? = null,
        val sentMatrixMediaId: String? = null,
        val sentMatrixEventId: String? = null,
    ) : AuthenticatedRedditMessageReplyResult

    data class Failed(
        val kind: MessageReplyFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val statusCode: Int? = null,
        val providerCode: String? = null,
        // DIAG04_JSON_PATH_REACHES_THE_REPORT_V1: an answer that could not be read, and its field.
        val protocolKind: NetworkProtocolFailureKind? = null,
        val jsonPath: String? = null,
        // SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1
        // Non-null only when the first Matrix m.image write was already server-confirmed
        // and the subsequent Reddit-parity m.text write failed or became uncertain.
        val sentMediaUri: String? = null,
        val sentMatrixMediaId: String? = null,
        val sentMatrixEventId: String? = null,
    ) : AuthenticatedRedditMessageReplyResult
}

internal fun interface AuthenticatedRedditMessageReplyWriter {
    suspend fun send(request: MessageReplyRequest): AuthenticatedRedditMessageReplyResult
}

private sealed interface MessageReplyMediaResolution {
    data class Ready(
        val richtextJson: String,
    ) : MessageReplyMediaResolution

    data class Failed(
        val result: AuthenticatedRedditMessageReplyResult.Failed,
    ) : MessageReplyMediaResolution
}

internal class RedditMessageReplyWriter(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val capability: AuthInboxReadCapability,
    private val mediaCapability: AuthCommentWriteTokenSource? = null,
    private val mediaSource: RedditMediaUploadSource? = null,
    private val mediaUploadCallFactory: Call.Factory? = null,
    private val mediaWebSocketClient: OkHttpClient? = null,
    private val directChatMediaWriter: RedditChatMatrixMediaWriter? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditMessageReplyWriter {
    private val sendMutex = Mutex()

    override suspend fun send(
        request: MessageReplyRequest,
    ): AuthenticatedRedditMessageReplyResult = withContext(ioDispatcher) {
        val normalized = runCatching {
            request.normalizedMessageReplyRequest().copy(
                parentFullname = normalizeRedditPrivateMessageFullname(request.parentFullname),
            )
        }.getOrNull() ?: return@withContext failed(MessageReplyFailureKind.VALIDATION)

        sendMutex.withLock {
            // CORRECT04_REPLY_LOCAL_FAILURE_BEFORE_DISPATCH_V1 (OCB-616): an
            // exception before the reply is sent (e.g. staging the image) did not
            // reach Reddit, so it is a retryable local failure, not "may have been sent".
            var replyDispatched = false
            try {
                val session = when (
                    val authorization = capability.ensureAuthorized(normalized.accountStableId)
                ) {
                    is AuthSessionResult.Authenticated -> authorization.session
                    is AuthSessionResult.EndpointFailure ->
                        return@withLock mapSessionFailure(authorization.result)
                    // LOW1 (OCB-506): a store fault is retryable, a client
                    // configuration mismatch is no reason to authorize.
                    AuthSessionResult.StorageFailure ->
                        return@withLock failed(MessageReplyFailureKind.SESSION, retryable = true)
                    AuthSessionResult.ClientConfigurationMismatch ->
                        return@withLock failed(MessageReplyFailureKind.UNAVAILABLE)
                    AuthSessionResult.NoPendingCallback,
                    AuthSessionResult.ReauthenticationRequired,
                    is AuthSessionResult.AuthorizationDenied,
                    -> return@withLock failed(MessageReplyFailureKind.AUTHORIZATION_REQUIRED)
                }

                val identity = session.identity
                    ?: return@withLock failed(MessageReplyFailureKind.AUTHORIZATION_REQUIRED)
                if (
                    authenticatedAccountId(
                        provider = session.provider,
                        clientMode = session.clientMode,
                        providerAccountId = identity.providerAccountId,
                    ) != normalized.accountStableId ||
                    !session.tokens.grants(RedditOAuth.liveInboxReadScopes)
                ) {
                    return@withLock failed(MessageReplyFailureKind.AUTHORIZATION_REQUIRED)
                }

                if (
                    normalized.channel == MessageReplyChannel.DIRECT_CHAT &&
                    normalized.media != null
                ) {
                    val matrixWriter = directChatMediaWriter
                        ?: return@withLock failed(MessageReplyFailureKind.UNAVAILABLE)
                    return@withLock matrixWriter.send(
                        request = normalized,
                        expectedProviderAccountId = identity.providerAccountId,
                    )
                }

                var replyHttpClient = redditHttpClient(session.tokens.accessToken)
                val richtextJson = if (normalized.media == null) {
                    null
                } else {
                    val mediaSession = when (
                        val authorization = mediaCapability?.ensureAuthorized(normalized.accountStableId)
                    ) {
                        is AuthSessionResult.Authenticated -> authorization.session
                        else -> return@withLock failed(
                            MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED,
                        )
                    }
                    val mediaIdentity = mediaSession.identity
                    if (
                        mediaIdentity == null ||
                        authenticatedAccountId(
                            provider = mediaSession.provider,
                            clientMode = mediaSession.clientMode,
                            providerAccountId = mediaIdentity.providerAccountId,
                        ) != normalized.accountStableId ||
                        !mediaSession.tokens.grants(RedditOAuth.liveInboxReadScopes) ||
                        !mediaSession.tokens.grants(RedditOAuth.liveCommentWriteScopes)
                    ) {
                        return@withLock failed(
                            MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED,
                        )
                    }
                    val mediaHttpClient = redditHttpClient(mediaSession.tokens.accessToken)
                    replyHttpClient = mediaHttpClient
                    when (
                        val resolution = resolveMediaBody(
                            request = normalized,
                            redditHttpClient = mediaHttpClient,
                        )
                    ) {
                        is MessageReplyMediaResolution.Ready -> resolution.richtextJson
                        is MessageReplyMediaResolution.Failed -> return@withLock resolution.result
                    }
                }

                val dataSource = RedditMessageReplyNetworkDataSource(replyHttpClient)
                replyDispatched = true
                when (
                    val result = dataSource.reply(
                        parentFullname = normalized.parentFullname,
                        text = normalized.text,
                        richtextJson = richtextJson,
                    )
                ) {
                    is RedditMessageReplyNetworkResult.Accepted ->
                        AuthenticatedRedditMessageReplyResult.Accepted(
                            request = normalized,
                            sentBody = normalized.text,
                            metadata = result.metadata,
                        )

                    is RedditMessageReplyNetworkResult.ApiFailure -> failed(
                        kind = if (result.providerCode == "RATELIMIT") {
                            MessageReplyFailureKind.RATE_LIMIT
                        } else {
                            MessageReplyFailureKind.API
                        },
                        retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = result.providerCode == "RATELIMIT"),
                        providerCode = result.providerCode,
                    )

                    is RedditMessageReplyNetworkResult.HttpFailure -> failed(
                        kind = when {
                            result.statusCode == 401 || result.statusCode == 403 ->
                                MessageReplyFailureKind.AUTHORIZATION_REQUIRED
                            result.statusCode == 429 -> MessageReplyFailureKind.RATE_LIMIT
                            // DATALAYER07: the reply may have been stored; a resend
                            // would post it twice.
                            isRedditWriteOutcomeUnknownStatus(result.statusCode) ->
                                MessageReplyFailureKind.OUTCOME_UNKNOWN
                            else -> MessageReplyFailureKind.HTTP
                        },
                        retryable = result.retryable &&
                            !isRedditWriteOutcomeUnknownStatus(result.statusCode),
                        retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = result.statusCode == 429),
                        statusCode = result.statusCode,
                    )

                    is RedditMessageReplyNetworkResult.TransportFailure -> when (result.kind) {
                        NetworkTransportFailureKind.TIMEOUT,
                        NetworkTransportFailureKind.IO,
                        -> failed(MessageReplyFailureKind.OUTCOME_UNKNOWN)

                        NetworkTransportFailureKind.DNS,
                        NetworkTransportFailureKind.CONNECT,
                        -> failed(
                            kind = MessageReplyFailureKind.TRANSPORT,
                            retryable = true,
                        )

                        NetworkTransportFailureKind.TLS ->
                            failed(MessageReplyFailureKind.TRANSPORT)
                    }

                    is RedditMessageReplyNetworkResult.ProtocolFailure ->
                        failed(
                            MessageReplyFailureKind.OUTCOME_UNKNOWN,
                            protocolKind = result.kind,
                            jsonPath = result.jsonPath,
                        )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (replyDispatched) {
                    failed(MessageReplyFailureKind.OUTCOME_UNKNOWN)
                } else {
                    failed(
                        kind = if (normalized.media != null) {
                            MessageReplyFailureKind.MEDIA_UNAVAILABLE
                        } else {
                            MessageReplyFailureKind.UNAVAILABLE
                        },
                        retryable = true,
                    )
                }
            }
        }
    }

    private suspend fun resolveMediaBody(
        request: MessageReplyRequest,
        redditHttpClient: SubshellHttpClient,
    ): MessageReplyMediaResolution {
        val media = requireNotNull(request.media)
        val source = mediaSource
            ?: return mediaFailed(MessageReplyFailureKind.MEDIA_UNAVAILABLE)
        val uploadFactory = mediaUploadCallFactory
            ?: return mediaFailed(MessageReplyFailureKind.MEDIA_UNAVAILABLE)
        val webSocketClient = mediaWebSocketClient
            ?: return mediaFailed(MessageReplyFailureKind.MEDIA_UNAVAILABLE)

        val staged = when (val stage = source.stage(media.uri, media.mimeType)) {
            is RedditMediaStageResult.Staged -> stage
            RedditMediaStageResult.UnsupportedType ->
                return mediaFailed(MessageReplyFailureKind.MEDIA_UNSUPPORTED)
            RedditMediaStageResult.TooLarge ->
                return mediaFailed(MessageReplyFailureKind.MEDIA_TOO_LARGE)
            RedditMediaStageResult.Empty,
            RedditMediaStageResult.Unavailable,
            -> return mediaFailed(MessageReplyFailureKind.MEDIA_UNAVAILABLE)
        }

        return try {
            val mediaNetwork = RedditMediaUploadNetworkDataSource(
                redditHttpClient = redditHttpClient,
                uploadCallFactory = uploadFactory,
                webSocketClient = webSocketClient,
                json = SubshellNetworkJson.providerJson,
            )
            val lease = when (
                val result = mediaNetwork.createLease(staged.fileName, staged.mimeType)
            ) {
                is NetworkResult.Success -> result.value
                is NetworkResult.HttpFailure -> return mediaFailed(
                    kind = when (result.statusCode) {
                        401, 403 -> MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED
                        429 -> MessageReplyFailureKind.RATE_LIMIT
                        else -> MessageReplyFailureKind.HTTP
                    },
                    retryable = result.retryable,
                    statusCode = result.statusCode,
                    retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = result.statusCode == 429),
                )
                is NetworkResult.TransportFailure -> return mediaFailed(
                    MessageReplyFailureKind.TRANSPORT,
                    retryable = result.retryable,
                )
                is NetworkResult.ProtocolFailure ->
                    return mediaFailed(
                        MessageReplyFailureKind.API,
                        protocolKind = result.kind,
                        jsonPath = result.jsonPath,
                    )
            }
            val uploaded = when (val result = mediaNetwork.upload(lease, staged)) {
                is RedditMediaBinaryUploadResult.Uploaded -> result
                is RedditMediaBinaryUploadResult.HttpFailure -> return mediaFailed(
                    kind = when (result.statusCode) {
                        401, 403 -> MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED
                        429 -> MessageReplyFailureKind.RATE_LIMIT
                        else -> MessageReplyFailureKind.HTTP
                    },
                    retryable = result.retryable,
                    statusCode = result.statusCode,
                )
                is RedditMediaBinaryUploadResult.TransportFailure -> return mediaFailed(
                    MessageReplyFailureKind.TRANSPORT,
                    retryable = result.retryable,
                )
                RedditMediaBinaryUploadResult.InvalidLease ->
                    return mediaFailed(MessageReplyFailureKind.API)
            }
            val assetId = uploaded.assetId
                ?: return mediaFailed(MessageReplyFailureKind.API)
            val richtextJson = try {
                prepareRedditInlineMediaRichText(
                    httpClient = redditHttpClient,
                    text = request.text,
                    assetId = assetId,
                )
            } catch (_: IllegalArgumentException) {
                return mediaFailed(MessageReplyFailureKind.VALIDATION)
            } ?: return mediaFailed(
                // The text could not be converted; nothing was sent.
                MessageReplyFailureKind.TRANSPORT,
                retryable = true,
            )
            MessageReplyMediaResolution.Ready(richtextJson)
        } finally {
            staged.file.delete()
        }
    }

    private fun redditHttpClient(accessToken: String): SubshellHttpClient = SubshellHttpClient(
        callFactory = callFactory,
        endpoint = endpoint,
        json = SubshellNetworkJson.providerJson,
        userAgent = userAgent,
        bearerTokenProvider = capability.bearerTokenProvider(accessToken),
    )

    private fun mapSessionFailure(
        result: OAuthEndpointResult<Nothing>,
    ): AuthenticatedRedditMessageReplyResult.Failed = when (result) {
        is OAuthEndpointResult.HttpFailure -> failed(
            kind = if (result.statusCode == 401 || result.statusCode == 403) {
                MessageReplyFailureKind.AUTHORIZATION_REQUIRED
            } else {
                MessageReplyFailureKind.SESSION
            },
            retryable = result.retryable,
            statusCode = result.statusCode,
        )

        is OAuthEndpointResult.TransportFailure -> failed(
            kind = MessageReplyFailureKind.SESSION,
            retryable = result.retryable,
        )

        is OAuthEndpointResult.ProtocolFailure -> failed(MessageReplyFailureKind.SESSION)
        is OAuthEndpointResult.Success -> failed(MessageReplyFailureKind.SESSION)
    }

    private fun mediaFailed(
        kind: MessageReplyFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        statusCode: Int? = null,
        protocolKind: NetworkProtocolFailureKind? = null,
        jsonPath: String? = null,
    ): MessageReplyMediaResolution.Failed = MessageReplyMediaResolution.Failed(
        failed(
            kind = kind,
            retryable = retryable,
            retryAfterMs = retryAfterMs,
            statusCode = statusCode,
            protocolKind = protocolKind,
            jsonPath = jsonPath,
        ),
    )

    private fun failed(
        kind: MessageReplyFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        statusCode: Int? = null,
        providerCode: String? = null,
        protocolKind: NetworkProtocolFailureKind? = null,
        jsonPath: String? = null,
    ): AuthenticatedRedditMessageReplyResult.Failed =
        AuthenticatedRedditMessageReplyResult.Failed(
            kind = kind,
            retryable = retryable,
            retryAfterMs = retryAfterMs,
            statusCode = statusCode,
            providerCode = providerCode,
            protocolKind = protocolKind,
            jsonPath = jsonPath,
        )
}
