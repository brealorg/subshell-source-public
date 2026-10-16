package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.SafeFailureText
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteProviderFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.SystemNetworkClock
import app.subshell.prototype.data.network.reddit.EpochSecondsClock
import app.subshell.prototype.data.network.reddit.RedditCommentApiErrorKind
import app.subshell.prototype.data.network.reddit.RedditCommentApiError
import app.subshell.prototype.data.network.reddit.redditCommentSubmitReplyJson
import app.subshell.prototype.data.network.reddit.RedditCommentSubmitNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditCommentSubmitNetworkResult
import app.subshell.prototype.data.network.reddit.RedditMediaBinaryUploadResult
import app.subshell.prototype.data.network.reddit.RedditMediaUploadNetworkDataSource
import app.subshell.prototype.data.network.reddit.prepareRedditInlineMediaRichText
import app.subshell.prototype.data.network.reddit.SystemEpochSecondsClock
import app.subshell.prototype.data.network.reddit.normalizeRedditCommentParentFullname
import app.subshell.prototype.data.network.reddit.normalizeRedditCommentText
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient

internal data class AuthenticatedCommentSubmitMediaAttachment(
    val uri: String,
    val mimeType: String,
    val sourceLinkUri: String? = null,
)

internal data class AuthenticatedCommentSubmitRequest(
    val accountStableId: String,
    val parentFullname: String,
    val text: String,
    val media: AuthenticatedCommentSubmitMediaAttachment? = null,
)

internal enum class AuthenticatedCommentSubmitFailureKind {
    VALIDATION,
    ALREADY_SUBMITTING,
    AUTHORIZATION_REQUIRED,
    SESSION,
    RATE_LIMIT,
    PARENT_UNAVAILABLE,
    PERMISSION,
    API,
    HTTP,
    TRANSPORT,
    OUTCOME_UNKNOWN,
    // COMPOSER09_COMMENT_MEDIA_NOT_ALLOWED_V1: the community does not allow
    // this kind of media in comments (Reddit: META_MEDIA_TYPE_NOT_ALLOWED).
    MEDIA_NOT_ALLOWED,
    // LOW2_COMMENT_MEDIA_STAGING_FAILURES_NAMED_V1 (OCB-494): the attached
    // image could not be staged; each reason has its own message.
    MEDIA_TOO_LARGE,
    MEDIA_UNSUPPORTED,
    MEDIA_UNAVAILABLE,
}

internal sealed interface AuthenticatedCommentSubmitResult {
    data class Submitted(
        val accountStableId: String,
        val comment: Comment,
        val metadata: NetworkResponseMetadata,
    ) : AuthenticatedCommentSubmitResult

    data class Failed(
        val kind: AuthenticatedCommentSubmitFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedCommentSubmitResult
}

private sealed interface CommentSubmitMediaResolution {
    data class Ready(val richtextJson: String) : CommentSubmitMediaResolution
    data class Failed(val result: AuthenticatedCommentSubmitResult.Failed) : CommentSubmitMediaResolution
}

internal fun interface AuthenticatedCommentSubmitter {
    suspend fun submit(
        request: AuthenticatedCommentSubmitRequest,
    ): AuthenticatedCommentSubmitResult
}

internal class ConfiguredRedditCommentSubmitter(
    private val capability: AuthCommentWriteTokenSource,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val networkClock: NetworkClock = SystemNetworkClock,
    private val epochSecondsClock: EpochSecondsClock = SystemEpochSecondsClock,
    private val mediaSource: RedditMediaUploadSource? = null,
    private val mediaUploadCallFactory: Call.Factory? = null,
    private val mediaWebSocketClient: OkHttpClient? = null,
) : AuthenticatedCommentSubmitter {
    private val submitMutex = Mutex()

    override suspend fun submit(
        request: AuthenticatedCommentSubmitRequest,
    ): AuthenticatedCommentSubmitResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedCommentSubmitFailureKind.VALIDATION)
        if (!submitMutex.tryLock()) {
            return failed(AuthenticatedCommentSubmitFailureKind.ALREADY_SUBMITTING)
        }

        return try {
            val session = when (val authorization = capability.ensureAuthorized(normalized.accountStableId)) {
                is AuthSessionResult.Authenticated -> authorization.session
                is AuthSessionResult.EndpointFailure -> return mapSessionFailure(authorization.result)
                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ReauthenticationRequired,
                AuthSessionResult.StorageFailure,
                is AuthSessionResult.AuthorizationDenied,
                -> return failed(AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED)
            }
            val identity = session.identity
                ?: return failed(AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED)
            if (
                authenticatedAccountId(
                    provider = session.provider,
                    clientMode = session.clientMode,
                    providerAccountId = identity.providerAccountId,
                ) != normalized.accountStableId ||
                !session.tokens.grants(RedditOAuth.liveCommentWriteScopes)
            ) {
                return failed(AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED)
            }

            val redditHttpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = json,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
                clock = networkClock,
            )
            val richtextJson = normalized.media?.let { media ->
                when (val resolution = resolveMediaRichText(normalized, media, redditHttpClient)) {
                    is CommentSubmitMediaResolution.Ready -> resolution.richtextJson
                    is CommentSubmitMediaResolution.Failed -> return resolution.result
                }
            }
            val dataSource = RedditCommentSubmitNetworkDataSource(
                httpClient = redditHttpClient,
                clock = epochSecondsClock,
                json = redditCommentSubmitReplyJson(json),
            )
            when (
                val result = dataSource.submit(
                    parentFullname = normalized.parentFullname,
                    text = normalized.text,
                    richtextJson = richtextJson,
                )
            ) {
                is RedditCommentSubmitNetworkResult.Created ->
                    AuthenticatedCommentSubmitResult.Submitted(
                        accountStableId = normalized.accountStableId,
                        comment = result.comment,
                        metadata = result.metadata,
                    )

                is RedditCommentSubmitNetworkResult.ApiFailure ->
                    mapApiFailure(result)

                is RedditCommentSubmitNetworkResult.HttpFailure ->
                    mapHttpFailure(result)

                is RedditCommentSubmitNetworkResult.TransportFailure ->
                    mapTransportFailure(result)

                is RedditCommentSubmitNetworkResult.ProtocolFailure ->
                    mapProtocolFailure(result.kind, result.jsonPath)

                is RedditCommentSubmitNetworkResult.MalformedPayload ->
                    failed(
                        AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN,
                        failureDetails = redditWriteProtocolFailureDetails(
                            NetworkProtocolFailureKind.MALFORMED_JSON,
                        ).copy(providerMessage = SafeFailureText.fromUntrusted(result.diagnostic)),
                    )
            }
        } finally {
            submitMutex.unlock()
        }
    }

    private fun normalizeRequest(
        request: AuthenticatedCommentSubmitRequest,
    ): AuthenticatedCommentSubmitRequest? = runCatching {
        AuthenticatedCommentSubmitRequest(
            accountStableId = request.accountStableId.trim().also { accountStableId ->
                require(accountStableId.isNotEmpty()) { "Account stable ID cannot be blank" }
            },
            parentFullname = normalizeRedditCommentParentFullname(request.parentFullname),
            text = if (request.media == null) {
                normalizeRedditCommentText(request.text)
            } else {
                request.text.trim().also { body ->
                    require(body.length <= app.subshell.prototype.data.network.reddit.REDDIT_COMMENT_TEXT_MAX_LENGTH)
                }
            },
            media = request.media?.copy(
                uri = request.media.uri.trim().also { require(it.isNotEmpty()) },
                mimeType = request.media.mimeType.trim().lowercase().also { mimeType ->
                    require(mimeType in COMMENT_MEDIA_MIME_TYPES)
                },
                sourceLinkUri = request.media.sourceLinkUri?.trim()?.takeIf(String::isNotEmpty),
            ),
        ).also { normalized ->
            require(normalized.text.isNotEmpty() || normalized.media != null)
        }
    }.getOrNull()

    private suspend fun resolveMediaRichText(
        request: AuthenticatedCommentSubmitRequest,
        media: AuthenticatedCommentSubmitMediaAttachment,
        redditHttpClient: SubshellHttpClient,
    ): CommentSubmitMediaResolution {
        val source = mediaSource
            ?: return mediaFailed(AuthenticatedCommentSubmitFailureKind.API)
        val uploadFactory = mediaUploadCallFactory
            ?: return mediaFailed(AuthenticatedCommentSubmitFailureKind.API)
        val webSocketClient = mediaWebSocketClient
            ?: return mediaFailed(AuthenticatedCommentSubmitFailureKind.API)

        val staged = when (val stage = source.stage(media.uri, media.mimeType)) {
            is RedditMediaStageResult.Staged -> stage
            RedditMediaStageResult.TooLarge ->
                return mediaFailed(AuthenticatedCommentSubmitFailureKind.MEDIA_TOO_LARGE)
            RedditMediaStageResult.UnsupportedType ->
                return mediaFailed(AuthenticatedCommentSubmitFailureKind.MEDIA_UNSUPPORTED)
            RedditMediaStageResult.Empty,
            RedditMediaStageResult.Unavailable,
            -> return mediaFailed(AuthenticatedCommentSubmitFailureKind.MEDIA_UNAVAILABLE)
        }

        return try {
            val mediaNetwork = RedditMediaUploadNetworkDataSource(
                redditHttpClient = redditHttpClient,
                uploadCallFactory = uploadFactory,
                webSocketClient = webSocketClient,
                json = json,
            )
            val lease = when (val result = mediaNetwork.createLease(staged.fileName, staged.mimeType)) {
                is app.subshell.prototype.data.network.NetworkResult.Success -> result.value
                is app.subshell.prototype.data.network.NetworkResult.HttpFailure -> return mediaFailed(
                    kind = when (result.statusCode) {
                        401, 403 -> AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED
                        429 -> AuthenticatedCommentSubmitFailureKind.RATE_LIMIT
                        else -> AuthenticatedCommentSubmitFailureKind.HTTP
                    },
                    retryable = result.retryable,
                    retryAfterMs = result.metadata.retryAfterMs ?: result.metadata.rateLimit.resetAfterMs,
                    statusCode = result.statusCode,
                )
                is app.subshell.prototype.data.network.NetworkResult.TransportFailure -> return mediaFailed(
                    kind = AuthenticatedCommentSubmitFailureKind.TRANSPORT,
                    retryable = result.retryable,
                )
                is app.subshell.prototype.data.network.NetworkResult.ProtocolFailure -> return mediaFailed(
                    AuthenticatedCommentSubmitFailureKind.API,
                )
            }
            when (val result = mediaNetwork.upload(lease, staged)) {
                is RedditMediaBinaryUploadResult.Uploaded -> {
                    val assetId = result.assetId
                        ?: return mediaFailed(AuthenticatedCommentSubmitFailureKind.API)
                    val richtext = try {
                        prepareRedditInlineMediaRichText(
                            httpClient = redditHttpClient,
                            text = request.text,
                            assetId = assetId,
                        )
                    } catch (_: IllegalArgumentException) {
                        return mediaFailed(AuthenticatedCommentSubmitFailureKind.VALIDATION)
                    } ?: return mediaFailed(
                        // The text could not be converted; nothing was sent.
                        kind = AuthenticatedCommentSubmitFailureKind.TRANSPORT,
                        retryable = true,
                    )
                    CommentSubmitMediaResolution.Ready(richtext)
                }
                is RedditMediaBinaryUploadResult.HttpFailure -> return mediaFailed(
                    kind = when (result.statusCode) {
                        401, 403 -> AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED
                        429 -> AuthenticatedCommentSubmitFailureKind.RATE_LIMIT
                        else -> AuthenticatedCommentSubmitFailureKind.HTTP
                    },
                    retryable = result.retryable,
                    statusCode = result.statusCode,
                )
                is RedditMediaBinaryUploadResult.TransportFailure -> return mediaFailed(
                    kind = AuthenticatedCommentSubmitFailureKind.TRANSPORT,
                    retryable = result.retryable,
                )
                RedditMediaBinaryUploadResult.InvalidLease -> return mediaFailed(
                    AuthenticatedCommentSubmitFailureKind.API,
                )
            }
        } finally {
            staged.file.delete()
        }
    }

    private fun mediaFailed(
        kind: AuthenticatedCommentSubmitFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        statusCode: Int? = null,
    ): CommentSubmitMediaResolution.Failed = CommentSubmitMediaResolution.Failed(
        failed(
            kind = kind,
            retryable = retryable,
            retryAfterMs = retryAfterMs,
            failureDetails = statusCode?.let { code ->
                FailureDetails(httpStatusCode = code, retryable = retryable)
            },
        ),
    )

    private fun mapSessionFailure(
        result: OAuthEndpointResult<Nothing>,
    ): AuthenticatedCommentSubmitResult.Failed = when (result) {
        is OAuthEndpointResult.HttpFailure -> failed(
            kind = if (result.statusCode == 401 || result.statusCode == 403) {
                AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED
            } else {
                AuthenticatedCommentSubmitFailureKind.SESSION
            },
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )

        is OAuthEndpointResult.TransportFailure -> failed(
            kind = AuthenticatedCommentSubmitFailureKind.SESSION,
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )

        is OAuthEndpointResult.ProtocolFailure -> failed(
            kind = AuthenticatedCommentSubmitFailureKind.SESSION,
            failureDetails = result.toFailureDetails(),
        )

        is OAuthEndpointResult.Success -> failed(
            AuthenticatedCommentSubmitFailureKind.SESSION,
        )
    }

    private fun mapApiFailure(
        result: RedditCommentSubmitNetworkResult.ApiFailure,
    ): AuthenticatedCommentSubmitResult.Failed {
        val kinds = result.errors.map { error -> error.kind }.toSet()
        val retryAfterMs = result.metadata.retryAfterMs
            ?: result.metadata.rateLimit.resetAfterMs
        val failureDetails = redditWriteProviderFailureDetails(
            code = result.errors.firstOrNull()?.code,
            retryAfterMs = retryAfterMs,
        )
        return when {
            redditCommentMediaTypeNotAllowed(result.errors) -> failed(
                AuthenticatedCommentSubmitFailureKind.MEDIA_NOT_ALLOWED,
                failureDetails = failureDetails,
            )

            RedditCommentApiErrorKind.RATE_LIMIT in kinds -> failed(
                kind = AuthenticatedCommentSubmitFailureKind.RATE_LIMIT,
                retryable = true,
                retryAfterMs = retryAfterMs,
                failureDetails = failureDetails.copy(retryable = true),
            )

            RedditCommentApiErrorKind.PARENT_UNAVAILABLE in kinds -> failed(
                AuthenticatedCommentSubmitFailureKind.PARENT_UNAVAILABLE,
               failureDetails = failureDetails,
            )

            RedditCommentApiErrorKind.PERMISSION in kinds -> failed(
                AuthenticatedCommentSubmitFailureKind.PERMISSION,
               failureDetails = failureDetails,
            )

            RedditCommentApiErrorKind.VALIDATION in kinds -> failed(
                AuthenticatedCommentSubmitFailureKind.VALIDATION,
               failureDetails = failureDetails,
            )

            else -> failed(AuthenticatedCommentSubmitFailureKind.API, failureDetails = failureDetails)
        }
    }

    private fun mapHttpFailure(
        result: RedditCommentSubmitNetworkResult.HttpFailure,
    ): AuthenticatedCommentSubmitResult.Failed {
        val retryAfterMs = result.metadata.retryAfterMs
            ?: result.metadata.rateLimit.resetAfterMs
        val details = redditWriteHttpFailureDetails(
            statusCode = result.statusCode,
            retryable = result.retryable,
            metadata = result.metadata,
        )
        return when {
            result.statusCode == 429 -> failed(
                kind = AuthenticatedCommentSubmitFailureKind.RATE_LIMIT,
                retryable = true,
                retryAfterMs = retryAfterMs,
                failureDetails = details.copy(retryable = true),
            )

            result.statusCode == 401 || result.statusCode == 403 -> failed(
                AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED,
                failureDetails = details,
            )

            result.statusCode == 408 ||
                result.statusCode == 421 ||
                result.statusCode == 425 ||
                result.statusCode in 500..599 -> failed(
                    AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = details,
                )

            else -> failed(
                AuthenticatedCommentSubmitFailureKind.HTTP,
                retryable = result.retryable,
                failureDetails = details,
            )
        }
    }

    private fun mapTransportFailure(
        result: RedditCommentSubmitNetworkResult.TransportFailure,
    ): AuthenticatedCommentSubmitResult.Failed {
        val details = redditWriteTransportFailureDetails(result.kind, result.retryable)
        return when (result.kind) {
            NetworkTransportFailureKind.DNS,
            NetworkTransportFailureKind.CONNECT,
            -> failed(
                kind = AuthenticatedCommentSubmitFailureKind.TRANSPORT,
                retryable = true,
                failureDetails = details.copy(retryable = true),
            )

            NetworkTransportFailureKind.TLS -> failed(
                AuthenticatedCommentSubmitFailureKind.TRANSPORT,
                failureDetails = details,
            )

            NetworkTransportFailureKind.TIMEOUT,
            NetworkTransportFailureKind.IO,
            -> failed(
                AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN,
                failureDetails = details,
            )
        }
    }

    private fun mapProtocolFailure(
        kind: NetworkProtocolFailureKind,
        jsonPath: String? = null,
    ): AuthenticatedCommentSubmitResult.Failed = failed(
        kind = AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN,
        failureDetails = redditWriteProtocolFailureDetails(kind, jsonPath),
    )

    private fun failed(
        kind: AuthenticatedCommentSubmitFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        failureDetails: FailureDetails? = null,
    ): AuthenticatedCommentSubmitResult.Failed = AuthenticatedCommentSubmitResult.Failed(
        kind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        failureDetails = failureDetails,
    )
}

private val COMMENT_MEDIA_MIME_TYPES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/gif",
    "image/*",
)

internal const val REDDIT_COMMENT_MEDIA_TYPE_NOT_ALLOWED = "META_MEDIA_TYPE_NOT_ALLOWED"

// COMPOSER09_COMMENT_MEDIA_NOT_ALLOWED_V1
internal fun redditCommentMediaTypeNotAllowed(errors: List<RedditCommentApiError>): Boolean =
    errors.any { error -> error.code == REDDIT_COMMENT_MEDIA_TYPE_NOT_ALLOWED }
