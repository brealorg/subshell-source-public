package app.subshell.prototype.data.network.reddit


import app.subshell.prototype.data.network.toNetworkTransportFailureKind
import app.subshell.prototype.data.auth.reddit.RedditMediaStageResult
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.isRetryableHttpStatus
import app.subshell.prototype.data.network.isRetryableTransportFailure
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal data class RedditMediaAssetLeaseResponseDto(
    val args: RedditMediaAssetLeaseArgsDto? = null,
    val asset: RedditMediaAssetLeaseAssetDto? = null,
)

@Serializable
internal data class RedditMediaAssetLeaseAssetDto(
    @SerialName("asset_id") val assetId: String? = null,
)

@Serializable
internal data class RedditMediaAssetLeaseArgsDto(
    val action: String? = null,
    val fields: List<RedditMediaAssetLeaseFieldDto> = emptyList(),
)

@Serializable
internal data class RedditMediaAssetLeaseFieldDto(
    val name: String? = null,
    val value: String? = null,
)

internal data class RedditMediaUploadLease(
    val uploadUrl: HttpUrl,
    val fields: List<Pair<String, String>>,
    val assetId: String? = null,
)

internal sealed interface RedditMediaBinaryUploadResult {
    data class Uploaded(
        val mediaUrl: String,
        val assetId: String? = null,
    ) : RedditMediaBinaryUploadResult
    data class HttpFailure(val statusCode: Int, val retryable: Boolean) : RedditMediaBinaryUploadResult
    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditMediaBinaryUploadResult

    data object InvalidLease : RedditMediaBinaryUploadResult
}

internal sealed interface RedditMediaPostCompletionResult {
    data class Created(
        val id: String,
        val permalink: String,
    ) : RedditMediaPostCompletionResult

    data class Failed(
        val detail: String? = null,
    ) : RedditMediaPostCompletionResult
    data object OutcomeUnknown : RedditMediaPostCompletionResult
}

internal class RedditMediaUploadNetworkDataSource(
    private val redditHttpClient: SubshellHttpClient,
    private val uploadCallFactory: Call.Factory,
    private val webSocketClient: OkHttpClient,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun createLease(
        fileName: String,
        mimeType: String,
    ): NetworkResult<RedditMediaUploadLease> {
        val result = redditHttpClient.postFormJson(
            request = FormPostRequest(
                pathSegments = listOf("api", "media", "asset.json"),
                fields = listOf(
                    "filepath" to fileName,
                    "mimetype" to mimeType,
                ),
            ),
            deserializer = RedditMediaAssetLeaseResponseDto.serializer(),
        )
        return when (result) {
            is NetworkResult.Success -> {
                val lease = result.value.toLeaseOrNull()
                    ?: return NetworkResult.ProtocolFailure(
                        NetworkProtocolFailureKind.MALFORMED_JSON,
                    )
                NetworkResult.Success(lease, result.metadata)
            }
            is NetworkResult.HttpFailure -> result
            is NetworkResult.TransportFailure -> result
            is NetworkResult.ProtocolFailure -> result
        }
    }

    suspend fun upload(
        lease: RedditMediaUploadLease,
        staged: RedditMediaStageResult.Staged,
    ): RedditMediaBinaryUploadResult {
        val key = lease.fields.firstOrNull { (name, _) -> name == "key" }
            ?.second
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return RedditMediaBinaryUploadResult.InvalidLease
        if (lease.fields.any { (name, _) -> name == "file" }) {
            return RedditMediaBinaryUploadResult.InvalidLease
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                lease.fields.forEach { (name, value) -> addFormDataPart(name, value) }
                addFormDataPart(
                    "file",
                    staged.fileName,
                    staged.file.asRequestBody(staged.mimeType.toMediaType()),
                )
            }
            .build()
        val request = Request.Builder()
            .url(lease.uploadUrl)
            .post(body)
            .build()
        return withContext(ioDispatcher) {
            try {
                uploadCallFactory.newCall(request).awaitMediaResponse().use { response ->
                    if (response.isSuccessful) {
                        RedditMediaBinaryUploadResult.Uploaded(
                            mediaUrl = lease.uploadUrl.newBuilder()
                                .encodedPath("/")
                                .addPathSegments(key.trimStart('/'))
                                .build()
                                .toString(),
                            assetId = lease.assetId,
                        )
                    } else {
                        RedditMediaBinaryUploadResult.HttpFailure(
                            statusCode = response.code,
                            retryable = isRetryableHttpStatus(response.code),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                val kind = error.toMediaTransportFailureKind()
                RedditMediaBinaryUploadResult.TransportFailure(
                    kind = kind,
                    retryable = isRetryableTransportFailure(kind),
                )
            }
        }
    }

    suspend fun awaitPostCompletion(webSocketUrl: String): RedditMediaPostCompletionResult {
        val request = webSocketRequestOrNull(webSocketUrl)
        if (request == null) {
            return RedditMediaPostCompletionResult.OutcomeUnknown
        }

        val result = withTimeoutOrNull(REDDIT_MEDIA_POST_COMPLETION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                lateinit var socket: WebSocket

                fun finish(result: RedditMediaPostCompletionResult) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                socket = webSocketClient.newWebSocket(
                    request,
                    object : WebSocketListener() {

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val parsed = parseRedditMediaCompletionMessage(json, text)
                            if (parsed != null) {
                                finish(parsed)
                                webSocket.close(1000, null)
                            }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            finish(RedditMediaPostCompletionResult.OutcomeUnknown)
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            finish(RedditMediaPostCompletionResult.OutcomeUnknown)
                        }

                        // LOW_NET_SERVER_CLOSE_ENDS_THE_WAIT_V1 (OCB-633): OkHttp
                        // reports a server close frame only here (onClosed needs
                        // our own close first), so a close without a result
                        // no longer waits out the 20 s timeout.
                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            finish(RedditMediaPostCompletionResult.OutcomeUnknown)
                            webSocket.close(1000, null)
                        }
                    },
                )

                continuation.invokeOnCancellation { socket.cancel() }
            }
        }


        return result ?: RedditMediaPostCompletionResult.OutcomeUnknown
    }

}

internal fun parseRedditMediaCompletionMessage(
    json: Json,
    text: String,
): RedditMediaPostCompletionResult? {
    val root = runCatching {
        json.parseToJsonElement(text).jsonObject
    }.getOrNull() ?: return null
    val type = runCatching {
        root["type"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    if (type == "failed") {
        val payload = runCatching {
            root["payload"]?.jsonObject
        }.getOrNull()
        val payloadText = runCatching {
            root["payload"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val detail = sequenceOf(
            payload?.get("message"),
            payload?.get("reason"),
            payload?.get("error"),
            payload?.get("detail"),
            payload?.get("description"),
            root["message"],
            root["reason"],
            root["error"],
            root["detail"],
            root["description"],
        )
            .mapNotNull { element ->
                runCatching {
                    element?.jsonPrimitive?.contentOrNull
                }.getOrNull()
            }
            .plus(sequenceOf(payloadText))
            .mapNotNull(::sanitizeRedditMediaFailureDetail)
            .firstOrNull()

        return RedditMediaPostCompletionResult.Failed(detail)
    }


    val payload = runCatching {
        root["payload"]?.jsonObject
    }.getOrNull() ?: return null
    val redirect = runCatching {
        payload["redirect"]?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: return null

    return redirect.toMediaPostCompletionOrNull()
}

private fun sanitizeRedditMediaFailureDetail(value: String?): String? {
    val compact = value
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    if (
        compact.equals("failed", ignoreCase = true) ||
        compact.equals("failure", ignoreCase = true)
    ) {
        return null
    }
    return compact
        .replace(REDDIT_MEDIA_FAILURE_URL_PATTERN, "[media URL]")
        .trim()
        .take(REDDIT_MEDIA_FAILURE_DETAIL_MAX_LENGTH)
        .takeIf(String::isNotEmpty)
}

internal fun RedditMediaAssetLeaseResponseDto.toLeaseOrNull(): RedditMediaUploadLease? {
    val action = args?.action?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val absoluteAction = if (action.startsWith("//")) "https:$action" else action
    val uploadUrl = absoluteAction.toHttpUrlOrNull()?.takeIf { url ->
        url.isHttps &&
            url.host.endsWith(".amazonaws.com") &&
            REDDIT_POST_MEDIA_UPLOAD_HOST_PREFIXES.any(url.host::startsWith)
    } ?: return null
    val normalizedFields = args.fields.mapNotNull { field ->
        val name = field.name?.trim()?.takeIf { candidate ->
            candidate.length in 1..REDDIT_MEDIA_LEASE_FIELD_NAME_MAX_LENGTH
        } ?: return@mapNotNull null
        val value = field.value?.takeIf { candidate ->
            candidate.length <= REDDIT_MEDIA_LEASE_FIELD_VALUE_MAX_LENGTH
        } ?: return@mapNotNull null
        name to value
    }
    if (normalizedFields.size != args.fields.size || normalizedFields.isEmpty()) return null
    if (normalizedFields.map { it.first }.distinct().size != normalizedFields.size) return null
    val assetId = asset?.assetId
        ?.trim()
        ?.lowercase()
        ?.takeIf { candidate ->
            candidate.length in 1..REDDIT_MEDIA_ASSET_ID_MAX_LENGTH &&
                candidate.all(Char::isLetterOrDigit)
        }
    return RedditMediaUploadLease(uploadUrl, normalizedFields, assetId)
}

// CORRECT03_WEBSOCKET_URL_KEEPS_ITS_ENCODING_V1 (OCB-634): the URL was rebuilt
// from java.net.URI's decoded path and query, which turned %2B into '+' and
// %26 into '&'. The address Reddit sent is used as it is (OkHttp maps wss to
// https); only its scheme and host are checked.
internal fun webSocketRequestOrNull(value: String): Request? {
    val trimmed = value.trim()
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.scheme.equals("wss", ignoreCase = true)) return null
    uri.host?.lowercase()?.takeIf { candidate ->
        candidate == "redditmedia.com" || candidate.endsWith(".redditmedia.com")
    } ?: return null
    return runCatching { Request.Builder().url(trimmed).get().build() }.getOrNull()
}

private fun String.toMediaPostCompletionOrNull(): RedditMediaPostCompletionResult.Created? {
    val parsed = runCatching { URI(trim()) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("https", ignoreCase = true)) return null
    val host = parsed.host?.lowercase()
    if (host !in REDDIT_MEDIA_COMPLETION_HOSTS) return null
    val segments = parsed.path.orEmpty().split('/').filter(String::isNotEmpty)
    val commentsIndex = segments.indexOf("comments")
    val id = segments.getOrNull(commentsIndex + 1)
        ?.lowercase()
        ?.takeIf { candidate ->
            commentsIndex >= 0 &&
                candidate.length in 1..REDDIT_MEDIA_POST_ID_MAX_LENGTH &&
                candidate.all(Char::isLetterOrDigit)
        }
        ?: return null
    val permalink = parsed.path.takeIf { it.startsWith('/') } ?: return null
    return RedditMediaPostCompletionResult.Created(id = id, permalink = permalink)
}

private suspend fun Call.awaitMediaResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, responseToClose, _ -> responseToClose.close() }
            }
        },
    )
}

// NET03_TLS_ONLY_FOR_HANDSHAKE_FAILURES_V1
private fun IOException.toMediaTransportFailureKind(): NetworkTransportFailureKind =
    toNetworkTransportFailureKind()

private val REDDIT_POST_MEDIA_UPLOAD_HOST_PREFIXES = setOf(
    "reddit-uploaded-media.",
    "reddit-uploaded-video.",
)
private const val REDDIT_MEDIA_POST_COMPLETION_TIMEOUT_MS = 20_000L
private const val REDDIT_MEDIA_FAILURE_DETAIL_MAX_LENGTH = 140
private val REDDIT_MEDIA_FAILURE_URL_PATTERN =
    Regex("""(?i)\b(?:https?|wss?)://\S+""")
private const val REDDIT_MEDIA_LEASE_FIELD_NAME_MAX_LENGTH = 128
private const val REDDIT_MEDIA_LEASE_FIELD_VALUE_MAX_LENGTH = 16_384
private const val REDDIT_MEDIA_POST_ID_MAX_LENGTH = 16
private const val REDDIT_MEDIA_ASSET_ID_MAX_LENGTH = 64
private val REDDIT_MEDIA_COMPLETION_HOSTS = setOf(
    "reddit.com",
    "www.reddit.com",
    "old.reddit.com",
    "new.reddit.com",
)
