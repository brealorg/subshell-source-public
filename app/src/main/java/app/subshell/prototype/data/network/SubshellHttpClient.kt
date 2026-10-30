package app.subshell.prototype.data.network

import app.subshell.prototype.data.diagnostics.REDDIT_REFUSAL_API
import app.subshell.prototype.data.diagnostics.RedditReadDiagnostics
import app.subshell.prototype.data.diagnostics.RedditRefusals
import app.subshell.prototype.data.diagnostics.isRedditApiRefusalStatus
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

fun interface NetworkBearerTokenProvider {
    fun bearerToken(): String?

    fun onUnauthorized() = Unit
}

fun interface NetworkClock {
    fun epochMillis(): Long
}

object SystemNetworkClock : NetworkClock {
    override fun epochMillis(): Long = System.currentTimeMillis()
}

object NoNetworkBearerToken : NetworkBearerTokenProvider {
    override fun bearerToken(): String? = null
}

data class JsonGetRequest(
    val pathSegments: List<String>,
    val queryParameters: List<Pair<String, String>> = emptyList(),
)

data class FormPostRequest(
    val pathSegments: List<String>,
    val fields: List<Pair<String, String>>,
)

data class JsonPostRequest<T>(
    val pathSegments: List<String>,
    val body: T,
)

// USERSTATUS01A1_JSON_PATCH_BOUNDARY_V1
data class JsonPatchRequest<T>(
    val pathSegments: List<String>,
    val body: T,
)

object SubshellNetworkJson {
    val providerJson: Json by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Json {
            ignoreUnknownKeys = true
            isLenient = false
        }
    }
}

object SubshellNetworkTransport {
    val sharedCallFactory: Call.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SubshellOkHttpClientFactory.create()
    }

    val nonRetryingWriteCallFactory: Call.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SubshellOkHttpClientFactory.createNonRetryingWriteClient()
    }

    val nonRetryingMediaWriteClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SubshellOkHttpClientFactory.createNonRetryingMediaWriteClient()
    }

    val mediaReadCallFactory: Call.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SubshellOkHttpClientFactory.createMediaReadClient(sharedCallFactory as OkHttpClient)
    }
}

object SubshellOkHttpClientFactory {
    const val CALL_TIMEOUT_SECONDS = 30L
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 20L
    const val WRITE_TIMEOUT_SECONDS = 20L
    const val MEDIA_CALL_TIMEOUT_SECONDS = 90L
    const val MEDIA_READ_TIMEOUT_SECONDS = 30L
    const val MEDIA_WRITE_TIMEOUT_SECONDS = 60L

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()

    fun createNonRetryingWriteClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    // MEDIA06_NO_CALL_TIMEOUT_FOR_MEDIA_READS_V1 (#149): OkHttp's call timeout
    // also covers reading the body, so the shared 30 s limit aborted any image
    // share/save or large display load slower than that, every retry alike.
    // Media reads share the connection pool but have no whole-call limit; a
    // stalled transfer still ends on the read (idle) timeout, and leaving the
    // screen cancels the call.
    fun createMediaReadClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(MEDIA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun createNonRetryingMediaWriteClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(MEDIA_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(MEDIA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(MEDIA_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
}

class SubshellHttpClient(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val json: Json,
    private val userAgent: String,
    private val bearerTokenProvider: NetworkBearerTokenProvider = NoNetworkBearerToken,
    private val clock: NetworkClock = SystemNetworkClock,
    private val maxJsonBodyBytes: Long = DEFAULT_MAX_JSON_BODY_BYTES,
    private val maxFormBodyBytes: Long = DEFAULT_MAX_FORM_BODY_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        require(userAgent.isNotBlank()) { "User-Agent cannot be blank" }
        require(userAgent.length <= MAX_USER_AGENT_LENGTH) { "User-Agent is too long" }
        require(maxJsonBodyBytes in 1..MAX_ALLOWED_JSON_BODY_BYTES) {
            "JSON body limit is outside the supported range"
        }
        require(maxFormBodyBytes in 1..MAX_ALLOWED_FORM_BODY_BYTES) {
            "Form body limit is outside the supported range"
        }
    }

    suspend fun <T> getJson(
        request: JsonGetRequest,
        deserializer: DeserializationStrategy<T>,
    ): NetworkResult<T> {
        val httpRequest = requestBuilder(
            endpoint.url(request.pathSegments, request.queryParameters),
        )
            .get()
            .build()
        return executeJson(httpRequest, deserializer)
    }

    suspend fun <T> postFormJson(
        request: FormPostRequest,
        deserializer: DeserializationStrategy<T>,
    ): NetworkResult<T> {
        require(request.fields.size in 1..MAX_FORM_FIELD_COUNT) {
            "Form request field count is outside the supported range"
        }
        val normalizedFields = request.fields.map { (rawName, value) ->
            val name = rawName.trim()
            require(name.length in 1..MAX_FORM_FIELD_NAME_LENGTH) {
                "Form field name length is outside the supported range"
            }
            require(name.all { character ->
                character.isLetterOrDigit() || character == '_'
            }) {
                "Form field name contains unsupported characters"
            }
            name to value
        }
        require(normalizedFields.map { it.first }.distinct().size == normalizedFields.size
        ) {
            "Form field names must be unique"
        }
        val formBody = FormBody.Builder().apply {
            normalizedFields.forEach { (name, value) -> add(name, value) }
        }.build()
        require(formBody.contentLength() in 1..maxFormBodyBytes) {
            "Encoded form body is outside the supported range"
        }
        val httpRequest = requestBuilder(endpoint.url(request.pathSegments))
            .post(formBody)
            .build()
        return executeJson(httpRequest, deserializer)
    }

    suspend fun <B, T> postJson(
        request: JsonPostRequest<B>,
        serializer: SerializationStrategy<B>,
        deserializer: DeserializationStrategy<T>,
    ): NetworkResult<T> {
        require(request.pathSegments.isNotEmpty()) { "JSON request path cannot be empty" }
        val encoded = try {
            json.encodeToString(serializer, request.body)
        } catch (error: SerializationException) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, error.safeJsonFieldPath())
        } catch (error: IllegalArgumentException) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, error.safeJsonFieldPath())
        }
        val bytes = encoded.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size.toLong() in 2..maxJsonBodyBytes) {
            "Encoded JSON request body is outside the supported range"
        }
        val body = bytes.toRequestBody(JSON_REQUEST_MEDIA_TYPE)
        val httpRequest = requestBuilder(endpoint.url(request.pathSegments))
            .post(body)
            .build()
        return executeJson(httpRequest, deserializer)
    }

    // Reuse the existing JSON encoding, bounded response and cancellation policy.
    // Callers performing account writes must supply nonRetryingWriteCallFactory.
    suspend fun <B, T> patchJson(
        request: JsonPatchRequest<B>,
        serializer: SerializationStrategy<B>,
        deserializer: DeserializationStrategy<T>,
    ): NetworkResult<T> {
        require(request.pathSegments.isNotEmpty()) { "JSON request path cannot be empty" }
        val encoded = try {
            json.encodeToString(serializer, request.body)
        } catch (error: SerializationException) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, error.safeJsonFieldPath())
        } catch (error: IllegalArgumentException) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, error.safeJsonFieldPath())
        }
        val bytes = encoded.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size.toLong() in 2..maxJsonBodyBytes) {
            "Encoded JSON request body is outside the supported range"
        }
        val requestBody = bytes.toRequestBody(JSON_REQUEST_MEDIA_TYPE)
        val httpRequest = requestBuilder(endpoint.url(request.pathSegments))
            .patch(requestBody)
            .build()
        return executeJson(httpRequest, deserializer)
    }

    private fun requestBuilder(url: okhttp3.HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", JSON_ACCEPT_HEADER)
            .header("User-Agent", userAgent)
            .apply {
                bearerTokenProvider.bearerToken()
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { token -> header("Authorization", "Bearer $token") }
            }

    private suspend fun <T> executeJson(
        request: Request,
        deserializer: DeserializationStrategy<T>,
    ): NetworkResult<T> = withContext(ioDispatcher) {
        try {
            callFactory.newCall(request).awaitResponse().use { response ->
                response.toNetworkResult(deserializer)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            val kind = error.toTransportFailureKind()
            NetworkResult.TransportFailure(
                kind = kind,
                retryable = isRetryableTransportFailure(kind),
            )
        }
    }


    suspend fun putFormStatus(
        request: FormPostRequest,
    ): NetworkResult<Unit> {
        require(request.fields.size in 1..MAX_FORM_FIELD_COUNT) {
            "Form request field count is outside the supported range"
        }
        val normalizedFields = request.fields.map { (rawName, value) ->
            val name = rawName.trim()
            require(name.length in 1..MAX_FORM_FIELD_NAME_LENGTH) {
                "Form field name length is outside the supported range"
            }
            require(name.all { character -> character.isLetterOrDigit() || character == '_' }) {
                "Form field name contains unsupported characters"
            }
            name to value
        }
        require(normalizedFields.map { it.first }.distinct().size == normalizedFields.size) {
            "Form field names must be unique"
        }
        val formBody = FormBody.Builder().apply {
            normalizedFields.forEach { (name, value) -> add(name, value) }
        }.build()
        require(formBody.contentLength() in 1..maxFormBodyBytes) {
            "Encoded form body is outside the supported range"
        }
        val httpRequest = Request.Builder()
            .url(endpoint.url(request.pathSegments))
            .put(formBody)
            .header("Accept", JSON_ACCEPT_HEADER)
            .header("User-Agent", userAgent)
        return statusOnly(httpRequest)
    }

    suspend fun deleteStatus(
        pathSegments: List<String>,
    ): NetworkResult<Unit> {
        require(pathSegments.isNotEmpty()) { "Delete request path cannot be empty" }
        val httpRequest = Request.Builder()
            .url(endpoint.url(pathSegments))
            .delete()
            .header("Accept", JSON_ACCEPT_HEADER)
            .header("User-Agent", userAgent)
        return statusOnly(httpRequest)
    }

    // LOW_NET_STATUS_CALLS_RUN_ON_IO_V1 (OCB-539): the token read, the
    // 401 callback (it may decrypt the account store) and the response close
    // run on the IO dispatcher whatever the caller's dispatcher, like
    // executeJson; postFormStatus no longer keeps its own copy of this.
    private suspend fun statusOnly(
        httpRequestBuilder: Request.Builder,
    ): NetworkResult<Unit> = withContext(ioDispatcher) {
        val httpRequest = httpRequestBuilder
            .apply {
                bearerTokenProvider.bearerToken()
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { token -> header("Authorization", "Bearer $token") }
            }
            .build()
        statusOnlyOnIo(httpRequest)
    }

    private suspend fun statusOnlyOnIo(httpRequest: Request): NetworkResult<Unit> = try {
        callFactory.newCall(httpRequest).awaitResponse().use { response ->
            val metadata = response.responseMetadata()
            if (response.isSuccessful) {
                NetworkResult.Success(value = Unit, metadata = metadata)
            } else {
                if (response.code == 401) {
                    bearerTokenProvider.onUnauthorized()
                }
                val providerReason = if (readsProviderReason(response.code)) response.forbiddenReasonOrNull() else null
                response.reportRefusal(providerReason)
                NetworkResult.HttpFailure(
                    statusCode = response.code,
                    retryable = isRetryableHttpStatus(response.code),
                    metadata = metadata,
                    providerReason = providerReason,
                )
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IOException) {
        val kind = error.toTransportFailureKind()
        NetworkResult.TransportFailure(
            kind = kind,
            retryable = isRetryableTransportFailure(kind),
        )
    }

    suspend fun postEmptyFormStatus(
        pathSegments: List<String>,
    ): NetworkResult<Unit> {
        require(pathSegments.isNotEmpty()) { "Post request path cannot be empty" }
        val httpRequest = Request.Builder()
            .url(endpoint.url(pathSegments))
            .post(FormBody.Builder().build())
            .header("Accept", JSON_ACCEPT_HEADER)
            .header("User-Agent", userAgent)
        return statusOnly(httpRequest)
    }

    suspend fun postFormStatus(
        request: FormPostRequest,
    ): NetworkResult<Unit> {
        require(request.fields.size in 1..MAX_FORM_FIELD_COUNT) {
            "Form request field count is outside the supported range"
        }
        val normalizedFields = request.fields.map { (rawName, value) ->
            val name = rawName.trim()
            require(name.length in 1..MAX_FORM_FIELD_NAME_LENGTH) {
                "Form field name length is outside the supported range"
            }
            require(name.all { character ->
                character.isLetterOrDigit() || character == '_'
            }) {
                "Form field name contains unsupported characters"
            }
            name to value
        }
        require(normalizedFields.map { it.first }.distinct().size == normalizedFields.size) {
            "Form field names must be unique"
        }
        val formBody = FormBody.Builder().apply {
            normalizedFields.forEach { (name, value) -> add(name, value) }
        }.build()
        require(formBody.contentLength() in 1..maxFormBodyBytes) {
            "Encoded form body is outside the supported range"
        }
        val httpRequest = Request.Builder()
            .url(endpoint.url(request.pathSegments))
            .post(formBody)
            .header("Accept", JSON_ACCEPT_HEADER)
            .header("User-Agent", userAgent)
        return statusOnly(httpRequest)
    }

    // ISSUE211_FORBIDDEN_REASON_IS_READ_V1: a 403 or 404 says why in its body
    // ({"reason": "quarantined"}, {"reason": "banned"} …); at most FORBIDDEN_REASON_PEEK_BYTES are
    // read, and anything not a short JSON reason is ignored.
    private fun Response.forbiddenReasonOrNull(): String? = runCatching {
        val text = peekBody(FORBIDDEN_REASON_PEEK_BYTES).string()
        forbiddenReasonFromBody(text)
    }.getOrNull()

    // ISSUE32_REDDIT_REFUSALS_IN_THE_REPORT_V1 (#32): a refusal's status and
    // Reddit's reason token go to the diagnostic report; nothing else of the
    // request or answer does.
    private fun Response.reportRefusal(providerReason: String?) {
        if (!isRedditApiRefusalStatus(code)) return
        RedditRefusals.report(REDDIT_REFUSAL_API, code, providerReason ?: forbiddenReasonOrNull())
    }

    private fun <T> Response.toNetworkResult(
        deserializer: DeserializationStrategy<T>,
    ): NetworkResult<T> {
        val metadata = responseMetadata()
        if (!isSuccessful) {
            if (code == 401) {
                bearerTokenProvider.onUnauthorized()
            }
            val providerReason = if (readsProviderReason(code)) forbiddenReasonOrNull() else null
            reportRefusal(providerReason)
            return NetworkResult.HttpFailure(
                statusCode = code,
                retryable = isRetryableHttpStatus(code),
                metadata = metadata,
                providerReason = providerReason,
            )
        }

        val responseBody = body
        val contentType = responseBody.contentType()
        if (!contentType.isJson()) {
            return NetworkResult.ProtocolFailure(
                NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE,
            )
        }
        if (responseBody.contentLength() == 0L) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.EMPTY_BODY)
        }
        if (responseBody.contentLength() > maxJsonBodyBytes) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.BODY_TOO_LARGE)
        }

        val bodyText = try {
            responseBody.readBoundedUtf8(maxJsonBodyBytes)
        } catch (_: JsonBodyTooLargeException) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.BODY_TOO_LARGE)
        }
        if (bodyText.isBlank()) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.EMPTY_BODY)
        }

        val value = try {
            json.decodeFromString(deserializer, bodyText)
        } catch (error: SerializationException) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, error.safeJsonFieldPath())
        } catch (error: IllegalArgumentException) {
            return NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, error.safeJsonFieldPath())
        }
        return NetworkResult.Success(value = value, metadata = metadata)
    }

    private fun Response.responseMetadata(): NetworkResponseMetadata {
        val now = clock.epochMillis()
        return NetworkResponseMetadata(
            statusCode = code,
            receivedAtEpochMs = now,
            rateLimit = NetworkRateLimit(
                remaining = header("X-Ratelimit-Remaining")?.toDoubleOrNull(),
                used = header("X-Ratelimit-Used")?.toDoubleOrNull(),
                // LOWCRASH01_UNTRUSTED_HEADER_VALUES_CLAMPED_V1 (OCB-627): a
                // negative or non-finite reset from a proxy made FailureDetails'
                // retryAfterMs >= 0 check throw while an error was being built.
                resetAfterMs = header("X-Ratelimit-Reset")
                    ?.toDoubleOrNull()
                    ?.takeIf(Double::isFinite)
                    ?.times(1_000.0)
                    ?.toLong()
                    ?.coerceAtLeast(0L),
            ),
            retryAfterMs = header("Retry-After")?.toRetryAfterMs(now),
        ).also { metadata ->
            // ISSUE281_THREAD_READS_IN_THE_REPORT_V1: the headers as last seen go to the report.
            val limit = metadata.rateLimit
            RedditReadDiagnostics.observeRateLimit(limit.remaining, limit.used, limit.resetAfterMs, now)
        }
    }

    companion object {
        const val DEFAULT_MAX_JSON_BODY_BYTES = 2L * 1024L * 1024L
        internal const val FORBIDDEN_REASON_PEEK_BYTES = 4L * 1024L
        const val MAX_ALLOWED_JSON_BODY_BYTES = 16L * 1024L * 1024L
        // NET04_FORM_BODY_FITS_ALLOWED_TEXT_V1 (#144): form encoding turns a CJK
        // character into 9 bytes, so 128 KiB stopped a 15 000-character Japanese
        // post (40 000 allowed) or a 100 000-character richtext_json (≈ 900 KB)
        // before sending. The default now covers every text the app allows.
        const val DEFAULT_MAX_FORM_BODY_BYTES = 2L * 1024L * 1024L
        const val MAX_ALLOWED_FORM_BODY_BYTES = 2L * 1024L * 1024L
        const val MAX_USER_AGENT_LENGTH = 512
        const val MAX_FORM_FIELD_COUNT = 32
        const val MAX_FORM_FIELD_NAME_LENGTH = 64
        private const val JSON_ACCEPT_HEADER = "application/json"
        private val JSON_REQUEST_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, responseToClose, _ ->
                    responseToClose.close()
                }
            }
        },
    )
}

private class JsonBodyTooLargeException : IOException()

private fun ResponseBody.readBoundedUtf8(maxBytes: Long): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    byteStream().use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > maxBytes) throw JsonBodyTooLargeException()
            output.write(buffer, 0, count)
        }
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

private fun MediaType?.isJson(): Boolean =
    this != null && type == "application" && (subtype == "json" || subtype.endsWith("+json"))

private fun String.toRetryAfterMs(nowEpochMs: Long): Long? {
    trim().toLongOrNull()?.let { seconds ->
        return seconds.coerceIn(0L, Long.MAX_VALUE / 1_000L).times(1_000L)
    }
    return runCatching {
        val target = ZonedDateTime.parse(trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        (target - nowEpochMs).coerceAtLeast(0L)
    }.getOrNull()
}

// NET03_TLS_ONLY_FOR_HANDSHAKE_FAILURES_V1
private fun IOException.toTransportFailureKind(): NetworkTransportFailureKind =
    toNetworkTransportFailureKind()

// DIAG04_REDDIT_ERROR_CODE_ON_400_V1: a refused request (400, 403, 404, 409,
// 422) says why in its body; the reason is read for each of them, from both
// the write and the read path, so diagnostics can tell "too old" from "locked".
private fun readsProviderReason(code: Int): Boolean = code == 400 || code == 403 || code == 404 || code == 409 || code == 422

/**
 * Reddit's reason from a refused request's body, as a lowercase token
 * (letters, digits, underscore, ≤ 32 chars); else null. Reddit answers
 * {"reason": "quarantined"} (403/404), {"json": {"errors": [["TOO_OLD", "…", "parent"]]}}
 * or {"error": "THREAD_LOCKED"}; only the code is kept, never the message.
 */
internal fun forbiddenReasonFromBody(text: String): String? {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        as? kotlinx.serialization.json.JsonObject ?: return null
    val firstJsonError = ((root["json"] as? kotlinx.serialization.json.JsonObject)?.get("errors")
        as? kotlinx.serialization.json.JsonArray)
        ?.firstOrNull()
        .let { it as? kotlinx.serialization.json.JsonArray }
        ?.firstOrNull()
    return listOfNotNull(root["reason"], firstJsonError, root["error"])
        .firstNotNullOfOrNull { element -> element.reasonTokenOrNull() }
}

private fun kotlinx.serialization.json.JsonElement.reasonTokenOrNull(): String? {
    val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (!primitive.isString) return null
    val reason = primitive.content.trim().lowercase()
    return reason.takeIf { it.isNotEmpty() && it.length <= 32 && it.all { c -> c.isLetterOrDigit() || c == '_' } }
}
