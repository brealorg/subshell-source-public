package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.error.redditWriteRetryAfterMs
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationDataSource
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationListing

import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.account.normalizeCrosspostSourceFullname
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.toFailureDetails
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.error.redditWriteProtocolFailureDetails
import app.subshell.prototype.data.error.redditWriteProviderFailureDetails
import app.subshell.prototype.data.error.redditWriteTransportFailureDetails
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.SystemNetworkClock
import app.subshell.prototype.data.network.reddit.RedditMediaBinaryUploadResult
import app.subshell.prototype.data.network.reddit.RedditMediaPostCompletionResult
import app.subshell.prototype.data.network.reddit.RedditGallerySubmitItem
import app.subshell.prototype.data.network.reddit.RedditMediaUploadNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditSubmittedTextPost
import app.subshell.prototype.data.network.reddit.RedditSingleMediaSubmitKind
import app.subshell.prototype.data.network.reddit.RedditTextPostApiError
import app.subshell.prototype.data.network.reddit.RedditTextPostApiErrorKind
import app.subshell.prototype.data.network.reddit.RedditTextPostSubmitNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditTextPostSubmitNetworkResult
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostBody
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostCommunity
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostTitle
import app.subshell.prototype.data.network.reddit.normalizeRedditPostLink
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient

internal data class AuthenticatedTextPostGalleryItem(
    val mediaUri: String,
    val mediaMimeType: String,
    val caption: String = "",
    val outboundUrl: String = "",
)

internal data class AuthenticatedTextPostSubmitRequest(
    val accountStableId: String,
    val community: String,
    val title: String,
    val text: String,
    val sendReplies: Boolean = true,
    val nsfw: Boolean = false,
    val spoiler: Boolean = false,
    val linkUrl: String? = null,
    val mediaUri: String? = null,
    val mediaMimeType: String? = null,
    val galleryItems: List<AuthenticatedTextPostGalleryItem> = emptyList(),
    val flairId: String? = null,
    val flairText: String? = null,
    val requestKey: String = "",
    val mediaAsGif: Boolean = false,
    val crosspostFullname: String? = null,
)

// CROSSPOST01A1A_AUTHENTICATED_SOURCE_VALIDATION_V1
internal fun AuthenticatedTextPostSubmitRequest.validatedCrosspostFullname(): String? {
    val source = crosspostFullname ?: return null
    val normalized = normalizeCrosspostSourceFullname(source)
    require(text.isBlank()) { "Crosspost cannot contain a replacement body" }
    require(
        linkUrl == null && mediaUri == null && mediaMimeType == null &&
            galleryItems.isEmpty() && !mediaAsGif
    ) { "Crosspost cannot also be a link or uploaded media" }
    require(flairText.isNullOrBlank() || !flairId.isNullOrBlank()) {
        "Crosspost flair text requires a destination flair template"
    }
    require(flairText == null || flairText.length <= 64) {
        "Crosspost flair text is too long"
    }
    return normalized
}

internal enum class AuthenticatedTextPostSubmitFailureKind {
    VALIDATION,
    ALREADY_SUBMITTING,
    AUTHORIZATION_REQUIRED,
    SESSION,
    RATE_LIMIT,
    COMMUNITY_UNAVAILABLE,
    PERMISSION,
    SUBMISSION_RESTRICTED,
    CHALLENGE_REQUIRED,
    API,
    HTTP,
    TRANSPORT,
    OUTCOME_UNKNOWN,
    // COMPOSER02_SUBMIT_ATTEMPT_ABSENCE_V1: read back after a lost response;
    // the account's submissions hold no such post, so posting again is safe.
    NOT_POSTED,
}

internal enum class AuthenticatedTextPostValidationField {
    COMMUNITY,
    TITLE,
    FLAIR,
    BODY,
    LINK,
    MEDIA,
    GENERAL,
}

internal data class AuthenticatedTextPostValidationIssue(
    val field: AuthenticatedTextPostValidationField,
    val message: String,
)

internal sealed interface AuthenticatedTextPostSubmitResult {
    data class Submitted(
        val accountStableId: String,
        val post: RedditSubmittedTextPost,
        val metadata: NetworkResponseMetadata,
    ) : AuthenticatedTextPostSubmitResult

    data class Failed(
        val kind: AuthenticatedTextPostSubmitFailureKind,
        val retryable: Boolean = false,
        val retryAfterMs: Long? = null,
        val validationIssues: List<AuthenticatedTextPostValidationIssue> = emptyList(),
        val failureDetails: FailureDetails? = null,
    ) : AuthenticatedTextPostSubmitResult
}

internal fun interface AuthenticatedTextPostSubmitter {
    suspend fun submit(
        request: AuthenticatedTextPostSubmitRequest,
    ): AuthenticatedTextPostSubmitResult

    suspend fun reconcile(
        request: AuthenticatedTextPostSubmitRequest,
        username: String,
        dispatchedAtEpochMs: Long,
    ): AuthenticatedTextPostSubmitResult = AuthenticatedTextPostSubmitResult.Failed(
        kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
    )
}


internal fun redditSingleMediaSubmitKind(
    mediaMimeType: String,
    mediaAsGif: Boolean,
): RedditSingleMediaSubmitKind = when {
    !mediaMimeType.startsWith("video/", ignoreCase = true) ->
        RedditSingleMediaSubmitKind.IMAGE
    mediaAsGif ->
        RedditSingleMediaSubmitKind.VIDEOGIF
    else ->
        RedditSingleMediaSubmitKind.VIDEO
}

internal class ConfiguredRedditTextPostSubmitter(
    private val capability: AuthCommentWriteTokenSource,
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val mediaSource: RedditMediaUploadSource? = null,
    private val mediaUploadCallFactory: Call.Factory = callFactory,
    private val mediaWebSocketClient: OkHttpClient? = null,
    private val json: Json = SubshellNetworkJson.providerJson,
    private val networkClock: NetworkClock = SystemNetworkClock,
    private val onFinalSubmitDispatched: (accountStableId: String, requestKey: String) -> Unit = { _, _ -> },
) : AuthenticatedTextPostSubmitter {
    private val submitMutex = Mutex()

    override suspend fun submit(
        request: AuthenticatedTextPostSubmitRequest,
    ): AuthenticatedTextPostSubmitResult {
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedTextPostSubmitFailureKind.VALIDATION)
        if (!submitMutex.tryLock()) {
            return failed(AuthenticatedTextPostSubmitFailureKind.ALREADY_SUBMITTING)
        }

        return try {
            val session = when (
                val authorization = capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                is AuthSessionResult.EndpointFailure ->
                    return mapSessionFailure(authorization.result)

                AuthSessionResult.ClientConfigurationMismatch,
                AuthSessionResult.NoPendingCallback,
                AuthSessionResult.ReauthenticationRequired,
                AuthSessionResult.StorageFailure,
                is AuthSessionResult.AuthorizationDenied,
                -> return failed(AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED)
            }
            val identity = session.identity
                ?: return failed(AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED)
            if (
                authenticatedAccountId(
                    provider = session.provider,
                    clientMode = session.clientMode,
                    providerAccountId = identity.providerAccountId,
                ) != normalized.accountStableId ||
                !session.tokens.grants(RedditOAuth.liveTextPostWriteScopes)
            ) {
                return failed(AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED)
            }

            val redditHttpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = json,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
                clock = networkClock,
            )
            val dataSource = RedditTextPostSubmitNetworkDataSource(
                httpClient = redditHttpClient,
            )
            if (normalized.galleryItems.isNotEmpty()) {
                submitGallery(
                    request = normalized,
                    redditHttpClient = redditHttpClient,
                    submitDataSource = dataSource,
                )
            } else if (normalized.mediaUri != null) {
                submitSingleMedia(
                    request = normalized,
                    redditHttpClient = redditHttpClient,
                    submitDataSource = dataSource,
                )
            } else {
                if (!markFinalSubmitDispatched(normalized)) return dispatchMarkFailed()
                mapSubmitNetworkResult(
                    request = normalized,
                    result = dataSource.submit(
                        community = normalized.community,
                        title = normalized.title,
                        text = normalized.text,
                        sendReplies = normalized.sendReplies,
                        nsfw = normalized.nsfw,
                        spoiler = normalized.spoiler,
                        linkUrl = normalized.linkUrl,
                        flairId = normalized.flairId,
                        flairText = normalized.flairText,
                        crosspostFullname = normalized.crosspostFullname,
                    ),
                )
            }
        } finally {
            submitMutex.unlock()
        }
    }


    override suspend fun reconcile(
        request: AuthenticatedTextPostSubmitRequest,
        username: String,
        dispatchedAtEpochMs: Long,
    ): AuthenticatedTextPostSubmitResult {
        // CROSSPOST01A1B replaces the interim A1A blanket refusal with source-verified reads.
        val normalized = normalizeRequest(request)
            ?: return failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
        if (!submitMutex.tryLock()) {
            return failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
        }
        return try {
            val session = when (
                val authorization = capability.ensureAuthorized(normalized.accountStableId)
            ) {
                is AuthSessionResult.Authenticated -> authorization.session
                else -> return failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
            }
            val identity = session.identity
                ?: return failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
            if (
                authenticatedAccountId(
                    provider = session.provider,
                    clientMode = session.clientMode,
                    providerAccountId = identity.providerAccountId,
                ) != normalized.accountStableId ||
                !session.tokens.grants(RedditOAuth.liveTextPostWriteScopes)
            ) {
                return failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
            }
            // CROSSPOST01A1B_RECONCILIATION_ACCOUNT_USERNAME_V1
            if (normalized.crosspostFullname != null &&
                !identity.username.trim().equals(username.trim(), ignoreCase = true)
            ) {
                return failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
            }
            val redditHttpClient = SubshellHttpClient(
                callFactory = callFactory,
                endpoint = endpoint,
                json = json,
                userAgent = userAgent,
                bearerTokenProvider = capability.bearerTokenProvider(session.tokens.accessToken),
                clock = networkClock,
            )
            when (
                val result = RedditSubmitReconciliationDataSource(redditHttpClient).submitted(
                    username = username,
                )
            ) {
                is NetworkResult.Success -> {
                    val candidate = selectRedditSubmitReconciliationCandidate(
                        request = normalized,
                        username = username,
                        dispatchedAtEpochMs = dispatchedAtEpochMs,
                        candidates = result.value.candidates,
                    )
                    if (candidate == null) {
                        if (
                            redditSubmitReconciliationShowsNoAttempt(
                                request = normalized,
                                username = username,
                                dispatchedAtEpochMs = dispatchedAtEpochMs,
                                listing = result.value,
                            )
                        ) {
                            failed(AuthenticatedTextPostSubmitFailureKind.NOT_POSTED)
                        } else {
                            failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
                        }
                    } else {
                        AuthenticatedTextPostSubmitResult.Submitted(
                            accountStableId = normalized.accountStableId,
                            post = candidate.toReconciledSubmittedPost(),
                            metadata = result.metadata,
                        )
                    }
                }
                else -> failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
            }
        } finally {
            submitMutex.unlock()
        }
    }

    private fun normalizeRequest(
        request: AuthenticatedTextPostSubmitRequest,
    ): AuthenticatedTextPostSubmitRequest? = runCatching {
        AuthenticatedTextPostSubmitRequest(
            accountStableId = request.accountStableId.trim().also { accountStableId ->
                require(accountStableId.isNotEmpty()) { "Account stable ID cannot be blank" }
            },
            community = normalizeRedditTextPostCommunity(request.community),
            title = normalizeRedditTextPostTitle(request.title),
            text = normalizeRedditTextPostBody(request.text),
            sendReplies = request.sendReplies,
            nsfw = request.nsfw,
            spoiler = request.spoiler,
            linkUrl = request.linkUrl?.let(::normalizeRedditPostLink),
            mediaUri = request.mediaUri?.trim()?.takeIf(String::isNotEmpty),
            mediaMimeType = request.mediaMimeType
                ?.trim()
                ?.lowercase()
                ?.takeIf(String::isNotEmpty),
            galleryItems = request.galleryItems.map { item ->
                AuthenticatedTextPostGalleryItem(
                    mediaUri = item.mediaUri.trim().also { uri ->
                        require(uri.isNotEmpty()) { "Gallery media URI cannot be blank" }
                    },
                    mediaMimeType = item.mediaMimeType.trim().lowercase().also { mime ->
                        require(mime.isNotEmpty()) { "Gallery media MIME type cannot be blank" }
                    },
                    caption = item.caption.also { caption ->
                        require(caption.length <= REDDIT_GALLERY_CAPTION_MAX_LENGTH) {
                            "Gallery caption is too long"
                        }
                    },
                    outboundUrl = item.outboundUrl.trim().let { outbound ->
                        if (outbound.isEmpty()) "" else normalizeRedditPostLink(outbound)
                    },
                )
            },
            flairId = request.flairId?.trim()?.takeIf(String::isNotEmpty),
            flairText = request.flairText?.trim()?.takeIf(String::isNotEmpty),
            requestKey = request.requestKey.trim(),
            mediaAsGif = request.mediaAsGif,
            crosspostFullname = request.validatedCrosspostFullname(),
        ).also { normalized ->
            require((normalized.mediaUri == null) == (normalized.mediaMimeType == null)) {
                "Media URI and MIME type must be provided together"
            }
            require(normalized.linkUrl == null || normalized.mediaUri == null) {
                "Post cannot contain both a link and uploaded media"
            }
            require(normalized.linkUrl == null || normalized.galleryItems.isEmpty()) {
                "Post cannot contain both a link and a gallery"
            }
            require(normalized.mediaUri == null || normalized.galleryItems.isEmpty()) {
                "Post cannot contain both single media and a gallery"
            }
            require(
                normalized.galleryItems.isEmpty() ||
                    normalized.galleryItems.size in 2..REDDIT_GALLERY_MAX_ITEMS
            ) {
                "Gallery item count is outside the supported range"
            }
            require(
                normalized.galleryItems.map { it.mediaUri }.distinct().size ==
                    normalized.galleryItems.size
            ) {
                "Gallery media URIs must be distinct"
            }
            require(
                !normalized.mediaAsGif ||
                    normalized.mediaMimeType?.startsWith("video/", ignoreCase = true) == true
            ) {
                "Post-as-GIF requires video media"
            }
        }
    }.getOrNull()

    private suspend fun submitGallery(
        request: AuthenticatedTextPostSubmitRequest,
        redditHttpClient: SubshellHttpClient,
        submitDataSource: RedditTextPostSubmitNetworkDataSource,
    ): AuthenticatedTextPostSubmitResult {
        val source = mediaSource ?: return failed(AuthenticatedTextPostSubmitFailureKind.API)
        val stagedItems = mutableListOf<Pair<AuthenticatedTextPostGalleryItem, RedditMediaStageResult.Staged>>()
        // LOW2_STAGING_CANCELS_AND_SWEEPS_V1 (OCB-522): a cancel while staging
        // deletes what was already staged.
        try {
        for ((index, item) in request.galleryItems.withIndex()) {
            when (
                val stage = source.stageGallery(
                    uri = item.mediaUri,
                    declaredMimeType = item.mediaMimeType,
                )
            ) {
                is RedditMediaStageResult.Staged -> stagedItems += item to stage
                RedditMediaStageResult.UnsupportedType -> {
                    stagedItems.forEach { (_, staged) -> staged.file.delete() }
                    return mediaValidation(
                        "Gallery item ${index + 1} must be JPEG, PNG, WebP, or GIF",
                    )
                }
                RedditMediaStageResult.TooLarge -> {
                    stagedItems.forEach { (_, staged) -> staged.file.delete() }
                    return mediaValidation("Gallery item ${index + 1} must be 20 MB or smaller")
                }
                RedditMediaStageResult.Empty -> {
                    stagedItems.forEach { (_, staged) -> staged.file.delete() }
                    return mediaValidation("Gallery item ${index + 1} is empty. Choose another file")
                }
                RedditMediaStageResult.Unavailable -> {
                    stagedItems.forEach { (_, staged) -> staged.file.delete() }
                    return mediaValidation(
                        "Gallery item ${index + 1} can no longer be read. Choose it again",
                    )
                }
            }
        }
        } catch (cancelled: CancellationException) {
            stagedItems.forEach { (_, staged) -> staged.file.delete() }
            throw cancelled
        }
        return try {
            val webSocketClient = mediaWebSocketClient
                ?: return failed(AuthenticatedTextPostSubmitFailureKind.API)
            val mediaNetwork = RedditMediaUploadNetworkDataSource(
                redditHttpClient = redditHttpClient,
                uploadCallFactory = mediaUploadCallFactory,
                webSocketClient = webSocketClient,
                json = json,
            )
            val submittedItems = mutableListOf<RedditGallerySubmitItem>()
            val remoteImageUrls = mutableListOf<String>()
            for ((item, staged) in stagedItems) {
                val lease = when (
                    val leaseResult = mediaNetwork.createLease(staged.fileName, staged.mimeType)
                ) {
                    is NetworkResult.Success -> leaseResult.value
                    is NetworkResult.HttpFailure -> return mapPreSubmitHttpFailure(
                        statusCode = leaseResult.statusCode,
                        retryable = leaseResult.retryable,
                        retryAfterMs = redditWriteRetryAfterMs(leaseResult.metadata, rateLimited = leaseResult.statusCode == 429),
                    )
                    is NetworkResult.TransportFailure -> return failed(
                        kind = AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                        retryable = leaseResult.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            leaseResult.kind,
                            leaseResult.retryable,
                        ),
                    )
                    is NetworkResult.ProtocolFailure -> return failed(
                        AuthenticatedTextPostSubmitFailureKind.API,
                        failureDetails = redditWriteProtocolFailureDetails(leaseResult.kind, leaseResult.jsonPath),
                    )
                }
                val assetId = lease.assetId
                    ?: return failed(AuthenticatedTextPostSubmitFailureKind.API)
                val mediaUrl = when (val upload = mediaNetwork.upload(lease, staged)) {
                    is RedditMediaBinaryUploadResult.Uploaded -> upload.mediaUrl
                    is RedditMediaBinaryUploadResult.HttpFailure -> return failed(
                        kind = AuthenticatedTextPostSubmitFailureKind.HTTP,
                        retryable = upload.retryable,
                        failureDetails = FailureDetails(
                            httpStatusCode = upload.statusCode,
                            retryable = upload.retryable,
                        ),
                    )
                    is RedditMediaBinaryUploadResult.TransportFailure -> return failed(
                        kind = AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                        retryable = upload.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            upload.kind,
                            upload.retryable,
                        ),
                    )
                    RedditMediaBinaryUploadResult.InvalidLease -> return failed(
                        AuthenticatedTextPostSubmitFailureKind.API,
                    )
                }
                submittedItems += RedditGallerySubmitItem(
                    mediaId = assetId,
                    caption = item.caption,
                    outboundUrl = item.outboundUrl,
                )
                remoteImageUrls += mediaUrl
            }
            if (!markFinalSubmitDispatched(request)) return dispatchMarkFailed()
            when (
                val result = submitDataSource.submitGallery(
                    community = request.community,
                    title = request.title,
                    text = request.text,
                    sendReplies = request.sendReplies,
                    nsfw = request.nsfw,
                    spoiler = request.spoiler,
                    items = submittedItems,
                    flairId = request.flairId,
                    flairText = request.flairText,
                )
            ) {
                is RedditTextPostSubmitNetworkResult.Created ->
                    AuthenticatedTextPostSubmitResult.Submitted(
                        accountStableId = request.accountStableId,
                        post = result.post.copy(galleryRemoteImageUris = remoteImageUrls),
                        metadata = result.metadata,
                    )
                is RedditTextPostSubmitNetworkResult.MediaPending ->
                    failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
                else -> mapSubmitNetworkResult(request, result)
            }
        } finally {
            stagedItems.forEach { (_, staged) -> staged.file.delete() }
        }
    }

    private suspend fun submitSingleMedia(
        request: AuthenticatedTextPostSubmitRequest,
        redditHttpClient: SubshellHttpClient,
        submitDataSource: RedditTextPostSubmitNetworkDataSource,
    ): AuthenticatedTextPostSubmitResult {
        val mediaUri = requireNotNull(request.mediaUri)
        val mediaMimeType = requireNotNull(request.mediaMimeType)
        val isVideo = mediaMimeType.startsWith("video/", ignoreCase = true)
        val source = mediaSource ?: return if (isVideo) {
            mediaValidation("The local video upload pipeline is unavailable.")
        } else {
            failed(AuthenticatedTextPostSubmitFailureKind.API)
        }
        val staged = when (
            val stage = if (isVideo) {
                source.stageVideo(mediaUri, mediaMimeType)
            } else {
                source.stage(mediaUri, mediaMimeType)
            }
        ) {
            is RedditMediaStageResult.Staged -> stage
            RedditMediaStageResult.UnsupportedType -> return mediaValidation(
                if (isVideo) {
                    "Choose an MP4, MOV, or WebM video"
                } else {
                    "Choose a JPEG, PNG, WebP, or GIF image"
                },
            )
            RedditMediaStageResult.TooLarge -> return mediaValidation(
                if (isVideo) {
                    "Choose a video smaller than 1 GB"
                } else {
                    "Images must be 20 MB or smaller"
                },
            )
            RedditMediaStageResult.Empty -> return mediaValidation(
                if (isVideo) {
                    "This video is empty. Choose another video"
                } else {
                    "This image is empty. Choose another image"
                },
            )
            RedditMediaStageResult.Unavailable -> return mediaValidation(
                if (isVideo) {
                    "This video can no longer be read. Choose it again"
                } else {
                    "This image can no longer be read. Choose it again"
                },
            )
        }

        val poster = if (isVideo) {
            when (val posterStage = source.stageVideoPoster(mediaUri)) {
                is RedditMediaStageResult.Staged -> posterStage
                RedditMediaStageResult.UnsupportedType,
                RedditMediaStageResult.TooLarge,
                RedditMediaStageResult.Empty,
                RedditMediaStageResult.Unavailable,
                -> {
                    staged.file.delete()
                    return mediaValidation(
                        "Reddit needs a preview frame from this video. Choose another video",
                    )
                }
            }
        } else {
            null
        }

        return try {
            val webSocketClient = mediaWebSocketClient
                ?: return if (isVideo) {
                    mediaValidation(
                        "The Reddit video completion channel is unavailable.",
                    )
                } else {
                    failed(AuthenticatedTextPostSubmitFailureKind.API)
                }
            val mediaNetwork = RedditMediaUploadNetworkDataSource(
                redditHttpClient = redditHttpClient,
                uploadCallFactory = mediaUploadCallFactory,
                webSocketClient = webSocketClient,
                json = json,
            )
            val lease = when (
                val leaseResult = mediaNetwork.createLease(staged.fileName, staged.mimeType)
            ) {
                is NetworkResult.Success -> leaseResult.value
                is NetworkResult.HttpFailure -> return mapPreSubmitHttpFailure(
                    statusCode = leaseResult.statusCode,
                    retryable = leaseResult.retryable,
                    retryAfterMs = redditWriteRetryAfterMs(leaseResult.metadata, rateLimited = leaseResult.statusCode == 429),
                )
                is NetworkResult.TransportFailure -> return failed(
                    kind = AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                    retryable = leaseResult.retryable,
                    failureDetails = redditWriteTransportFailureDetails(
                        leaseResult.kind,
                        leaseResult.retryable,
                    ),
                )
                // DIAG04_JSON_PATH_REACHES_THE_REPORT_V1
                is NetworkResult.ProtocolFailure -> return if (isVideo) {
                    mediaValidation(
                        "Reddit's video upload lease response could not be read " +
                            "(${leaseResult.kind.name}).",
                        failureDetails = redditWriteProtocolFailureDetails(leaseResult.kind, leaseResult.jsonPath),
                    )
                } else {
                    failed(
                        AuthenticatedTextPostSubmitFailureKind.API,
                        failureDetails = redditWriteProtocolFailureDetails(leaseResult.kind, leaseResult.jsonPath),
                    )
                }
            }
            val mediaUrl = when (val upload = mediaNetwork.upload(lease, staged)) {
                is RedditMediaBinaryUploadResult.Uploaded -> upload.mediaUrl
                is RedditMediaBinaryUploadResult.HttpFailure -> return failed(
                    kind = AuthenticatedTextPostSubmitFailureKind.HTTP,
                    retryable = upload.retryable,
                    failureDetails = FailureDetails(
                        httpStatusCode = upload.statusCode,
                        retryable = upload.retryable,
                    ),
                )
                is RedditMediaBinaryUploadResult.TransportFailure -> return failed(
                    kind = AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                    retryable = upload.retryable,
                    failureDetails = redditWriteTransportFailureDetails(
                        upload.kind,
                        upload.retryable,
                    ),
                )
                RedditMediaBinaryUploadResult.InvalidLease -> return if (isVideo) {
                    mediaValidation(
                        "Reddit returned an unusable upload lease for this video.",
                    )
                } else {
                    failed(AuthenticatedTextPostSubmitFailureKind.API)
                }
            }

            val videoPosterUrl = poster?.let { stagedPoster ->
                val posterLease = when (
                    val leaseResult = mediaNetwork.createLease(
                        stagedPoster.fileName,
                        stagedPoster.mimeType,
                    )
                ) {
                    is NetworkResult.Success -> leaseResult.value
                    is NetworkResult.HttpFailure -> return mapPreSubmitHttpFailure(
                        statusCode = leaseResult.statusCode,
                        retryable = leaseResult.retryable,
                        retryAfterMs = redditWriteRetryAfterMs(leaseResult.metadata, rateLimited = leaseResult.statusCode == 429),
                    )
                    // LOW2_POSTER_FAILURES_KEEP_DETAILS_V1 (OCB-523)
                    is NetworkResult.TransportFailure -> return failed(
                        kind = AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                        retryable = leaseResult.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            leaseResult.kind,
                            leaseResult.retryable,
                        ),
                    )
                    is NetworkResult.ProtocolFailure -> return mediaValidation(
                        "Reddit's video preview-image lease response could not be read " +
                            "(${leaseResult.kind.name}).",
                        failureDetails = redditWriteProtocolFailureDetails(leaseResult.kind, leaseResult.jsonPath),
                    )
                }
                when (val upload = mediaNetwork.upload(posterLease, stagedPoster)) {
                    is RedditMediaBinaryUploadResult.Uploaded -> upload.mediaUrl
                    is RedditMediaBinaryUploadResult.HttpFailure -> return failed(
                        kind = AuthenticatedTextPostSubmitFailureKind.HTTP,
                        retryable = upload.retryable,
                        failureDetails = FailureDetails(
                            httpStatusCode = upload.statusCode,
                            retryable = upload.retryable,
                        ),
                    )
                    is RedditMediaBinaryUploadResult.TransportFailure -> return failed(
                        kind = AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                        retryable = upload.retryable,
                        failureDetails = redditWriteTransportFailureDetails(
                            upload.kind,
                            upload.retryable,
                        ),
                    )
                    RedditMediaBinaryUploadResult.InvalidLease -> return mediaValidation(
                        "Reddit returned an unusable upload lease for the video preview image.",
                    )
                }
            }

            val singleMediaKind = redditSingleMediaSubmitKind(
                mediaMimeType = mediaMimeType,
                mediaAsGif = request.mediaAsGif,
            )

            if (!markFinalSubmitDispatched(request)) return dispatchMarkFailed()
            when (
                val result = submitDataSource.submit(
                    community = request.community,
                    title = request.title,
                    text = request.text,
                    sendReplies = request.sendReplies,
                    nsfw = request.nsfw,
                    spoiler = request.spoiler,
                    mediaUrl = mediaUrl,
                    mediaKind = singleMediaKind,
                    videoPosterUrl = videoPosterUrl,
                    flairId = request.flairId,
                    flairText = request.flairText,
                )
            ) {
                is RedditTextPostSubmitNetworkResult.Created ->
                    AuthenticatedTextPostSubmitResult.Submitted(
                        accountStableId = request.accountStableId,
                        post = result.post.copy(
                            remoteImageUri = if (isVideo) {
                                videoPosterUrl
                            } else {
                                mediaUrl
                            },
                            videoWidth = if (isVideo) {
                                poster?.width ?: staged.width
                            } else {
                                null
                            },
                            videoHeight = if (isVideo) {
                                poster?.height ?: staged.height
                            } else {
                                null
                            },
                        ),
                        metadata = result.metadata,
                    )
                is RedditTextPostSubmitNetworkResult.MediaPending -> {
                    when (
                        val completion = mediaNetwork.awaitPostCompletion(result.webSocketUrl)
                    ) {
                    is RedditMediaPostCompletionResult.Created ->
                        AuthenticatedTextPostSubmitResult.Submitted(
                            accountStableId = request.accountStableId,
                            post = RedditSubmittedTextPost(
                                fullname = "t3_${completion.id}",
                                id = completion.id,
                                community = request.community,
                                title = request.title,
                                text = request.text,
                                permalink = completion.permalink,
                                remoteImageUri = if (isVideo) {
                                    videoPosterUrl
                                } else {
                                    mediaUrl
                                },
                                videoWidth = if (isVideo) {
                                    poster?.width ?: staged.width
                                } else {
                                    null
                                },
                                videoHeight = if (isVideo) {
                                    poster?.height ?: staged.height
                                } else {
                                    null
                                },
                            ),
                            metadata = result.metadata,
                        )
                    is RedditMediaPostCompletionResult.Failed -> {
                        val mediaLabel = when {
                            isVideo -> "video"
                            mediaMimeType.equals("image/gif", ignoreCase = true) -> "GIF"
                            else -> "image"
                        }
                        val message = completion.detail?.let { detail ->
                            "Reddit couldn't process this $mediaLabel: $detail"
                        } ?: (
                            "The upload completed, but Reddit's media processor rejected " +
                                "this $mediaLabel. Reddit did not provide a specific reason."
                            )
                        mediaValidation(message)
                    }
                    RedditMediaPostCompletionResult.OutcomeUnknown -> failed(
                        AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                    )
                    }
                }
                is RedditTextPostSubmitNetworkResult.ApiFailure ->
                    mapApiFailure(result, singleMediaKind)
                else -> mapSubmitNetworkResult(request, result)
            }
        } finally {
            staged.file.delete()
            poster?.file?.delete()
        }
    }

    // LOW2_DISPATCH_MARK_OFF_MAIN_AND_PRE_DISPATCH_V1 (OCB-524): the mark is a
    // synchronous SharedPreferences commit; it runs on the IO dispatcher, and
    // a failed mark is a failure before anything was sent (safe to retry),
    // not an exception that ended as "outcome unknown".
    private suspend fun markFinalSubmitDispatched(
        request: AuthenticatedTextPostSubmitRequest,
    ): Boolean {
        val key = request.requestKey.takeIf(String::isNotBlank) ?: return true
        return try {
            withContext(Dispatchers.IO) { onFinalSubmitDispatched(request.accountStableId, key) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun dispatchMarkFailed(): AuthenticatedTextPostSubmitResult =
        failed(AuthenticatedTextPostSubmitFailureKind.SESSION, retryable = true)

    private fun mapSubmitNetworkResult(
        request: AuthenticatedTextPostSubmitRequest,
        result: RedditTextPostSubmitNetworkResult,
    ): AuthenticatedTextPostSubmitResult = when (result) {
        is RedditTextPostSubmitNetworkResult.Created ->
            AuthenticatedTextPostSubmitResult.Submitted(
                accountStableId = request.accountStableId,
                post = result.post,
                metadata = result.metadata,
            )
        is RedditTextPostSubmitNetworkResult.MediaPending ->
            failed(AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
        is RedditTextPostSubmitNetworkResult.ApiFailure -> mapApiFailure(result)
        is RedditTextPostSubmitNetworkResult.HttpFailure -> mapHttpFailure(result)
        is RedditTextPostSubmitNetworkResult.TransportFailure -> mapTransportFailure(result)
        is RedditTextPostSubmitNetworkResult.ProtocolFailure -> mapProtocolFailure(result.kind, result.jsonPath)
        RedditTextPostSubmitNetworkResult.MalformedPayload -> {
            failed(
                AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                failureDetails = redditWriteProtocolFailureDetails(
                    NetworkProtocolFailureKind.MALFORMED_JSON,
                ),
            )
        }
    }

    private fun mediaValidation(
        message: String,
        failureDetails: FailureDetails? = null,
    ): AuthenticatedTextPostSubmitResult.Failed =
        failed(
            kind = AuthenticatedTextPostSubmitFailureKind.VALIDATION,
            failureDetails = failureDetails,
            validationIssues = listOf(
                AuthenticatedTextPostValidationIssue(
                    field = AuthenticatedTextPostValidationField.MEDIA,
                    message = message,
                ),
            ),
        )

    private fun mapPreSubmitHttpFailure(
        statusCode: Int,
        retryable: Boolean,
        retryAfterMs: Long?,
    ): AuthenticatedTextPostSubmitResult.Failed {
        val details = FailureDetails(
            httpStatusCode = statusCode,
            retryAfterMs = retryAfterMs,
            retryable = retryable,
        )
        return when {
            statusCode == 429 -> failed(
                kind = AuthenticatedTextPostSubmitFailureKind.RATE_LIMIT,
                retryable = true,
                retryAfterMs = retryAfterMs,
                failureDetails = details.copy(retryable = true),
            )
            statusCode == 401 || statusCode == 403 -> failed(
                AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED,
                failureDetails = details,
            )
            else -> failed(
                kind = AuthenticatedTextPostSubmitFailureKind.HTTP,
                retryable = retryable,
                failureDetails = details,
            )
        }
    }

    private fun mapSessionFailure(
        result: OAuthEndpointResult<Nothing>,
    ): AuthenticatedTextPostSubmitResult.Failed = when (result) {
        is OAuthEndpointResult.HttpFailure -> failed(
            kind = if (result.statusCode == 401 || result.statusCode == 403) {
                AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED
            } else {
                AuthenticatedTextPostSubmitFailureKind.SESSION
            },
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )

        is OAuthEndpointResult.TransportFailure -> failed(
            kind = AuthenticatedTextPostSubmitFailureKind.SESSION,
            retryable = result.retryable,
            failureDetails = result.toFailureDetails(),
        )

        is OAuthEndpointResult.ProtocolFailure -> failed(
            kind = AuthenticatedTextPostSubmitFailureKind.SESSION,
            failureDetails = result.toFailureDetails(),
        )

        is OAuthEndpointResult.Success -> failed(
            AuthenticatedTextPostSubmitFailureKind.SESSION,
        )
    }

    private fun mapApiFailure(
        result: RedditTextPostSubmitNetworkResult.ApiFailure,
        singleMediaKind: RedditSingleMediaSubmitKind? = null,
    ): AuthenticatedTextPostSubmitResult.Failed {
        val kinds = result.errors.map { error -> error.kind }.toSet()
        val validationIssues = if (
            singleMediaKind in setOf(
                RedditSingleMediaSubmitKind.VIDEO,
                RedditSingleMediaSubmitKind.VIDEOGIF,
            )
        ) {
            if (result.errors.isEmpty()) {
                listOf(
                    AuthenticatedTextPostValidationIssue(
                        field = AuthenticatedTextPostValidationField.MEDIA,
                        message = "Reddit rejected this video without an error code or field.",
                    ),
                )
            } else {
                result.errors
                    .map { error -> error.toVideoSubmitValidationIssue() }
                    .distinct()
            }
        } else {
            result.errors
                .mapNotNull { error -> error.toAuthenticatedValidationIssue() }
                .distinct()
        }
        val retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = RedditTextPostApiErrorKind.RATE_LIMIT in kinds)
        val providerError = result.errors.firstOrNull()
        val failureDetails = redditWriteProviderFailureDetails(
            code = providerError?.code,
            retryAfterMs = retryAfterMs,
        )
        return when {
            RedditTextPostApiErrorKind.RATE_LIMIT in kinds -> failed(
                kind = AuthenticatedTextPostSubmitFailureKind.RATE_LIMIT,
                retryable = true,
                retryAfterMs = retryAfterMs,
                failureDetails = failureDetails.copy(retryable = true),
            )

            RedditTextPostApiErrorKind.COMMUNITY_UNAVAILABLE in kinds -> failed(
                AuthenticatedTextPostSubmitFailureKind.COMMUNITY_UNAVAILABLE,
                failureDetails = failureDetails,
            )

            RedditTextPostApiErrorKind.PERMISSION in kinds -> failed(
                AuthenticatedTextPostSubmitFailureKind.PERMISSION,
                failureDetails = failureDetails,
            )

            RedditTextPostApiErrorKind.SUBMISSION_RESTRICTED in kinds -> failed(
                AuthenticatedTextPostSubmitFailureKind.SUBMISSION_RESTRICTED,
                failureDetails = failureDetails,
            )

            RedditTextPostApiErrorKind.CHALLENGE_REQUIRED in kinds -> failed(
                AuthenticatedTextPostSubmitFailureKind.CHALLENGE_REQUIRED,
                failureDetails = failureDetails,
            )

            validationIssues.isNotEmpty() || RedditTextPostApiErrorKind.VALIDATION in kinds -> failed(
                kind = AuthenticatedTextPostSubmitFailureKind.VALIDATION,
                validationIssues = validationIssues,
                failureDetails = failureDetails,
            )

            else -> failed(AuthenticatedTextPostSubmitFailureKind.API, failureDetails = failureDetails)
        }
    }

    private fun mapHttpFailure(
        result: RedditTextPostSubmitNetworkResult.HttpFailure,
    ): AuthenticatedTextPostSubmitResult.Failed {
        val retryAfterMs = redditWriteRetryAfterMs(result.metadata, rateLimited = result.statusCode == 429)
        val details = redditWriteHttpFailureDetails(
            statusCode = result.statusCode,
            retryable = result.retryable,
            metadata = result.metadata,
            providerReason = result.providerReason,
        )
        return when {
            result.statusCode == 429 -> failed(
                kind = AuthenticatedTextPostSubmitFailureKind.RATE_LIMIT,
                retryable = true,
                retryAfterMs = retryAfterMs,
                failureDetails = details.copy(retryable = true),
            )

            result.statusCode == 401 || result.statusCode == 403 -> failed(
                AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED,
                failureDetails = details,
            )

            result.statusCode == 408 ||
                result.statusCode == 421 ||
                result.statusCode == 425 ||
                result.statusCode in 500..599 -> failed(
                    AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = details,
                )

            else -> failed(
                AuthenticatedTextPostSubmitFailureKind.HTTP,
                retryable = result.retryable,
                failureDetails = details,
            )
        }
    }

    private fun mapTransportFailure(
        result: RedditTextPostSubmitNetworkResult.TransportFailure,
    ): AuthenticatedTextPostSubmitResult.Failed {
        val details = redditWriteTransportFailureDetails(result.kind, result.retryable)
        return when (result.kind) {
            NetworkTransportFailureKind.DNS,
            NetworkTransportFailureKind.CONNECT,
            -> failed(
                kind = AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                retryable = true,
                failureDetails = details.copy(retryable = true),
            )

            NetworkTransportFailureKind.TLS -> failed(
                AuthenticatedTextPostSubmitFailureKind.TRANSPORT,
                failureDetails = details,
            )

            NetworkTransportFailureKind.TIMEOUT,
            NetworkTransportFailureKind.IO,
            -> failed(
                AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                failureDetails = details,
            )
        }
    }

    private fun mapProtocolFailure(
        kind: NetworkProtocolFailureKind,
        jsonPath: String? = null,
    ): AuthenticatedTextPostSubmitResult.Failed = failed(
        kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
        failureDetails = redditWriteProtocolFailureDetails(kind, jsonPath),
    )

    private fun failed(
        kind: AuthenticatedTextPostSubmitFailureKind,
        retryable: Boolean = false,
        retryAfterMs: Long? = null,
        validationIssues: List<AuthenticatedTextPostValidationIssue> = emptyList(),
        failureDetails: FailureDetails? = null,
    ): AuthenticatedTextPostSubmitResult.Failed = AuthenticatedTextPostSubmitResult.Failed(
        kind = kind,
        retryable = retryable,
        retryAfterMs = retryAfterMs,
        validationIssues = validationIssues,
        failureDetails = failureDetails,
    )
}

internal fun RedditTextPostApiError.toVideoSubmitValidationIssue():
    AuthenticatedTextPostValidationIssue {
    val details = buildList {
        code?.let { add("code: $it") }
        field?.let { add("field: $it") }
    }
    val message = if (details.isEmpty()) {
        "Reddit rejected this video without an error code or field."
    } else {
        "Reddit rejected this video (${details.joinToString(", ")})."
    }
    return AuthenticatedTextPostValidationIssue(
        field = AuthenticatedTextPostValidationField.MEDIA,
        message = message,
    )
}

internal fun selectRedditSubmitReconciliationCandidate(
    request: AuthenticatedTextPostSubmitRequest,
    username: String,
    dispatchedAtEpochMs: Long,
    candidates: List<RedditSubmitReconciliationCandidate>,
): RedditSubmitReconciliationCandidate? {
    // CROSSPOST01A1B_PARENT_BLIND_MATCHES_STILL_FORBIDDEN_V1
    if (request.crosspostFullname != null) {
        return selectSourceVerifiedCrosspostCandidate(
            request = request,
            username = username,
            dispatchedAtEpochMs = dispatchedAtEpochMs,
            candidates = candidates,
        )
    }
    val expectedKind = when {
        request.galleryItems.isNotEmpty() -> RedditSubmitReconciliationKind.GALLERY
        request.mediaMimeType?.startsWith("video/", ignoreCase = true) == true ->
            RedditSubmitReconciliationKind.VIDEO
        request.mediaMimeType != null -> RedditSubmitReconciliationKind.IMAGE
        request.linkUrl != null -> RedditSubmitReconciliationKind.LINK
        else -> RedditSubmitReconciliationKind.TEXT
    }
    val dispatchedAtEpochSeconds = dispatchedAtEpochMs / 1_000L
    val earliest = dispatchedAtEpochSeconds - SUBMIT_READBACK_CLOCK_SKEW_SECONDS
    val latest = dispatchedAtEpochSeconds + 300L
    val expectedCommunity = normalizeRedditTextPostCommunity(request.community)
    val expectedTitle = normalizeRedditTextPostTitle(request.title)
    val expectedText = normalizeRedditTextPostBody(request.text)
    val expectedLink = request.linkUrl?.let(::normalizeRedditPostLink)

    // SUBMITREADBACK01_REDDIT_MUTABLE_MARKS_IGNORED_V1: NSFW/spoiler marks and
    // flair are not compared. Reddit marks every post in an NSFW community, and
    // AutoModerator or moderators re-flair new posts, so an exact match left a
    // post that really went up unconfirmed for good (#141). The absence check
    // ignores them for the same reason.
    return candidates
        .asSequence()
        .filter { candidate ->
            // DATALAYER11: one older submission whose title or body the
            // normalisers reject (a newline in a title) threw out of the whole
            // readback; it is simply not a match, as in the crosspost matcher.
            runCatching {
            candidate.author.equals(username.trim(), ignoreCase = true) &&
                normalizeRedditTextPostCommunity(candidate.community)
                    .equals(expectedCommunity, ignoreCase = true) &&
                normalizeRedditTextPostTitle(candidate.title) == expectedTitle &&
                normalizeRedditTextPostBody(candidate.text) == expectedText &&
                candidate.createdUtcEpochSeconds in earliest..latest &&
                redditSubmitReconciliationKindMatches(expectedKind, candidate) &&
                (
                    expectedLink == null ||
                        candidate.destinationUrl?.let(::normalizeRedditPostLink) == expectedLink
                    )
            }.getOrDefault(false)
        }
        .toList()
        .singleOrNull()
}

// SUBMITREADBACK03_LINK_KIND_FROM_REDDIT_HINT_V1 (#146): Reddit marks a link
// post to a direct image or an embeddable video (YouTube) with post_hint
// image/rich:video, so it reads back as IMAGE/VIDEO; the link URL must still
// match exactly. An uploaded image reads as a LINK to i.redd.it until Reddit
// fills post_hint.
internal fun redditSubmitReconciliationKindMatches(
    expected: RedditSubmitReconciliationKind,
    candidate: RedditSubmitReconciliationCandidate,
): Boolean = when (expected) {
    RedditSubmitReconciliationKind.LINK -> candidate.kind in setOf(
        RedditSubmitReconciliationKind.LINK,
        RedditSubmitReconciliationKind.IMAGE,
        RedditSubmitReconciliationKind.VIDEO,
    )
    RedditSubmitReconciliationKind.IMAGE ->
        candidate.kind == RedditSubmitReconciliationKind.IMAGE ||
            candidate.kind == RedditSubmitReconciliationKind.LINK &&
            candidate.destinationUrl
                ?.let { url -> runCatching { URI(url.trim()).host }.getOrNull() }
                ?.equals("i.redd.it", ignoreCase = true) == true
    else -> candidate.kind == expected
}

// SUBMITREADBACK02_CLOCK_SKEW_MARGIN_V1 (#135): the dispatch time is the
// phone's clock, created_utc is Reddit's. A phone clock running fast put the
// real post "before" the dispatch: the matcher missed it and the absence
// check called it NOT_POSTED, so Retry sent a duplicate. Both look back this
// far before the dispatch. A slow clock only ever leaves the attempt unknown.
internal const val SUBMIT_READBACK_CLOCK_SKEW_SECONDS = 600L

// COMPOSER02_SUBMIT_ATTEMPT_ABSENCE_V1: "not posted" needs more proof than a
// missed match. The listing must cover the time before the dispatch, and no
// post of the account in that community after it may carry the same title,
// whatever its body, kind, flair or NSFW/spoiler marks (Reddit or a community
// can change those). Anything that could be the attempt keeps it unresolved.
internal fun redditSubmitReconciliationShowsNoAttempt(
    request: AuthenticatedTextPostSubmitRequest,
    username: String,
    dispatchedAtEpochMs: Long,
    listing: RedditSubmitReconciliationListing,
): Boolean {
    if (dispatchedAtEpochMs <= 0L) return false
    val author = username.trim().takeIf(String::isNotEmpty) ?: return false
    val community = runCatching { normalizeRedditTextPostCommunity(request.community) }
        .getOrNull() ?: return false
    val title = runCatching { normalizeRedditTextPostTitle(request.title) }
        .getOrNull()
        ?.let(::looseSubmitTitle)
        ?: return false
    val earliest = dispatchedAtEpochMs / 1_000L - SUBMIT_READBACK_CLOCK_SKEW_SECONDS
    val covered = listing.reachesEnd ||
        listing.candidates.any { candidate -> candidate.createdUtcEpochSeconds < earliest }
    if (!covered) return false
    return listing.candidates.none { candidate ->
        candidate.createdUtcEpochSeconds >= earliest &&
            candidate.author.equals(author, ignoreCase = true) &&
            runCatching {
                normalizeRedditTextPostCommunity(candidate.community)
                    .equals(community, ignoreCase = true) &&
                    looseSubmitTitle(candidate.title) == title
            }.getOrDefault(true)
    }
}

private fun looseSubmitTitle(title: String): String =
    title.trim().replace(Regex("\\s+"), " ").lowercase()

internal fun RedditTextPostApiError.toAuthenticatedValidationIssue():
    AuthenticatedTextPostValidationIssue? {
    if (kind != RedditTextPostApiErrorKind.VALIDATION) return null

    val normalizedField = field?.lowercase()
    val target = when {
        code == "SUBMIT_VALIDATION_FLAIR_REQUIRED" ||
            code?.contains("FLAIR") == true ||
            normalizedField == "flair" ||
            normalizedField == "flair_id" -> AuthenticatedTextPostValidationField.FLAIR
        code?.contains("TITLE") == true || normalizedField == "title" ->
            AuthenticatedTextPostValidationField.TITLE
        code == "BAD_IMAGE" ||
            code?.contains("IMAGE") == true ||
            code?.contains("MEDIA") == true ||
            code?.contains("GALLERY") == true ||
            normalizedField == "image" ||
            normalizedField == "image_url" ||
            normalizedField == "media" ||
            normalizedField == "media_id" ||
            normalizedField == "gallery" ||
            normalizedField == "items" -> AuthenticatedTextPostValidationField.MEDIA
        code?.contains("DOMAIN") == true ||
            code?.contains("LINK") == true ||
            normalizedField == "url" ||
            normalizedField == "link" -> AuthenticatedTextPostValidationField.LINK
        code?.contains("BODY") == true ||
            code?.contains("TEXT") == true ||
            normalizedField == "text" ||
            normalizedField == "body" -> AuthenticatedTextPostValidationField.BODY
        normalizedField == "sr" || normalizedField == "subreddit" ->
            AuthenticatedTextPostValidationField.COMMUNITY
        else -> AuthenticatedTextPostValidationField.GENERAL
    }

    val message = when (code) {
        "SUBMIT_VALIDATION_FLAIR_REQUIRED" -> "Post flair is required"
        "NO_TITLE" -> "Add a title"
        "BAD_TITLE" -> "Review the post title"
        "TITLE_TOO_LONG" -> "The post title is too long"
        "BAD_TEXT" -> "Review the post body"
        "BAD_IMAGE" -> "Reddit couldn't process this image. Choose another image"
        "TOO_LONG" -> when (target) {
            AuthenticatedTextPostValidationField.TITLE -> "The post title is too long"
            AuthenticatedTextPostValidationField.BODY -> "The post body is too long"
            else -> "This field is too long"
        }
        "SUBMIT_VALIDATION_MIN_LENGTH" -> when (target) {
            AuthenticatedTextPostValidationField.TITLE ->
                "The title is shorter than this community allows"
            AuthenticatedTextPostValidationField.BODY ->
                "The post body is shorter than this community allows"
            else -> "This field is shorter than this community allows"
        }
        "SUBMIT_VALIDATION_MAX_LENGTH" -> when (target) {
            AuthenticatedTextPostValidationField.TITLE ->
                "The title is longer than this community allows"
            AuthenticatedTextPostValidationField.BODY ->
                "The post body is longer than this community allows"
            else -> "This field is longer than this community allows"
        }
        "SUBMIT_VALIDATION_TITLE_BLACKLISTED_STRING" ->
            "The title contains text blocked by this community"
        "SUBMIT_VALIDATION_TITLE_REQUIRED_STRING" ->
            "The title is missing text required by this community"
        "SUBMIT_VALIDATION_BODY_BLACKLISTED_STRING" ->
            "The post body contains text blocked by this community"
        "SUBMIT_VALIDATION_BODY_REQUIRED_STRING" ->
            "The post body is missing text required by this community"
        "SUBMIT_VALIDATION_DOMAIN_BLACKLISTED" ->
            "This community blocks links from that domain"
        "SUBMIT_VALIDATION_DOMAIN_NOT_ALLOWED" ->
            "This community doesn't allow links from that domain"
        else -> when (target) {
            AuthenticatedTextPostValidationField.FLAIR -> "Choose a valid post flair"
            AuthenticatedTextPostValidationField.TITLE ->
                "The title doesn't meet this community's posting requirements"
            AuthenticatedTextPostValidationField.BODY ->
                "The post body doesn't meet this community's posting requirements"
            AuthenticatedTextPostValidationField.LINK ->
                "The link doesn't meet this community's posting requirements"
            AuthenticatedTextPostValidationField.MEDIA ->
                "The media doesn't meet this community's posting requirements"
            AuthenticatedTextPostValidationField.COMMUNITY ->
                "This community rejected the post"
            AuthenticatedTextPostValidationField.GENERAL ->
                "The post doesn't meet this community's posting requirements"
        }
    }
    return AuthenticatedTextPostValidationIssue(field = target, message = message)


}

private const val REDDIT_GALLERY_MAX_ITEMS = 20
private const val REDDIT_GALLERY_CAPTION_MAX_LENGTH = 180
