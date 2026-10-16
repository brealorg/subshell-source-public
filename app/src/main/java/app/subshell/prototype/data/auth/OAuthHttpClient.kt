package app.subshell.prototype.data.auth

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

@Serializable
private data class OAuthTokenResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    val scope: String? = null,
    val error: JsonElement? = null,
)

internal class OAuthTokenEndpointClient(
    private val callFactory: Call.Factory,
    private val configuration: OAuthProviderConfiguration,
    private val userAgent: String,
    private val clock: AuthClock = SystemAuthClock,
    private val json: Json = OAUTH_JSON,
    private val maxBodyBytes: Long = DEFAULT_MAX_OAUTH_BODY_BYTES,
) {
    init {
        require(userAgent.isNotBlank()) { "OAuth User-Agent cannot be blank" }
        require(userAgent.length <= MAX_OAUTH_USER_AGENT_LENGTH) { "OAuth User-Agent is too long" }
        require(maxBodyBytes in 1..MAX_OAUTH_BODY_BYTES) { "OAuth body limit is invalid" }
    }

    suspend fun exchangeAuthorizationCode(code: String): OAuthEndpointResult<OAuthTokenSet> =
        tokenRequest(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", configuration.redirectUri)
                .build(),
            previousRefreshToken = null,
        )

    suspend fun refresh(refreshToken: String): OAuthEndpointResult<OAuthTokenSet> =
        tokenRequest(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build(),
            previousRefreshToken = refreshToken,
        )

    suspend fun revoke(token: String, tokenTypeHint: String): OAuthEndpointResult<Unit> {
        val request = Request.Builder()
            .url(configuration.revokeEndpoint)
            .post(
                FormBody.Builder()
                    .add("token", token)
                    .add("token_type_hint", tokenTypeHint)
                    .build(),
            )
            .oauthHeaders()
            .build()

        return execute(request) { response ->
            if (response.isSuccessful) {
                OAuthEndpointResult.Success(Unit)
            } else {
                OAuthEndpointResult.HttpFailure(
                    statusCode = response.code,
                    retryable = isRetryableAuthHttpStatus(response.code),
                )
            }
        }
    }

    private suspend fun tokenRequest(
        body: FormBody,
        previousRefreshToken: String?,
    ): OAuthEndpointResult<OAuthTokenSet> {
        val request = Request.Builder()
            .url(configuration.tokenEndpoint)
            .post(body)
            .oauthHeaders()
            .header("Accept", "application/json")
            .build()

        return execute(request) { response ->
            if (!response.isSuccessful) {
                val error = response.oauthJsonError()
                return@execute OAuthEndpointResult.HttpFailure(
                    statusCode = response.code,
                    retryable = isRetryableAuthHttpStatus(response.code),
                    jsonBody = error != null,
                    oauthError = error?.code,
                )
            }
            val responseBody = response.body
            val contentType = responseBody.contentType()
            if (contentType == null || contentType.type != "application" ||
                (contentType.subtype != "json" && !contentType.subtype.endsWith("+json"))
            ) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.UNEXPECTED_CONTENT_TYPE,
                )
            }
            if (responseBody.contentLength() == 0L) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.EMPTY_BODY,
                )
            }
            if (responseBody.contentLength() > maxBodyBytes) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.BODY_TOO_LARGE,
                )
            }
            val text = try {
                responseBody.readBoundedUtf8(maxBodyBytes)
            } catch (_: OAuthBodyTooLargeException) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.BODY_TOO_LARGE,
                )
            }
            if (text.isBlank()) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.EMPTY_BODY,
                )
            }
            val dto = try {
                json.decodeFromString(OAuthTokenResponseDto.serializer(), text)
            } catch (_: SerializationException) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.MALFORMED_JSON,
                )
            } catch (_: IllegalArgumentException) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.MALFORMED_JSON,
                )
            }
            // AUTH12_OK_STATUS_OAUTH_ERROR_V1 (#133): Reddit answers some OAuth
            // errors (invalid_grant for a revoked refresh token) with HTTP 200
            // and {"error": ...}; that is a rejection, not a protocol fault.
            val oauthError = (dto.error as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty)
            if (oauthError != null && dto.accessToken.isNullOrBlank()) {
                return@execute OAuthEndpointResult.HttpFailure(
                    statusCode = response.code,
                    retryable = false,
                    jsonBody = true,
                    oauthError = oauthError.takeIf { it.length <= 64 && it.all { c -> c.isLetterOrDigit() || c == '_' } },
                )
            }
            val accessToken = dto.accessToken?.trim().orEmpty()
            if (accessToken.isEmpty()) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.MISSING_ACCESS_TOKEN,
                )
            }
            val tokenType = dto.tokenType?.trim().orEmpty()
            if (!tokenType.equals("bearer", ignoreCase = true)) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.UNSUPPORTED_TOKEN_TYPE,
                )
            }
            val expiresInSeconds = dto.expiresInSeconds
            if (expiresInSeconds == null || expiresInSeconds <= 0L) {
                return@execute OAuthEndpointResult.ProtocolFailure(
                    OAuthProtocolFailureKind.INVALID_EXPIRY,
                )
            }
            val expiresAt = runCatching {
                Math.addExact(clock.epochMillis(), Math.multiplyExact(expiresInSeconds, 1_000L))
            }.getOrNull() ?: return@execute OAuthEndpointResult.ProtocolFailure(
                OAuthProtocolFailureKind.INVALID_EXPIRY,
            )

            OAuthEndpointResult.Success(
                OAuthTokenSet(
                    accessToken = accessToken,
                    refreshToken = dto.refreshToken
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: previousRefreshToken,
                    tokenType = tokenType,
                    scope = dto.scope?.trim().orEmpty(),
                    expiresAtEpochMs = expiresAt,
                ),
            )
        }
    }

    private suspend fun <T> execute(
        request: Request,
        responseMapper: (Response) -> OAuthEndpointResult<T>,
    ): OAuthEndpointResult<T> = try {
        callFactory.newCall(request).awaitAuthResponse().use(responseMapper)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IOException) {
        OAuthEndpointResult.TransportFailure(retryable = error !is SSLException)
    }

    private fun Request.Builder.oauthHeaders(): Request.Builder =
        header("Authorization", Credentials.basic(configuration.clientId, configuration.clientSecret))
            .header("User-Agent", userAgent)

    companion object {
        const val DEFAULT_MAX_OAUTH_BODY_BYTES = 64L * 1024L
        const val MAX_OAUTH_BODY_BYTES = 1024L * 1024L
        const val MAX_OAUTH_USER_AGENT_LENGTH = 512
        private val OAUTH_JSON = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }
    }
}

private suspend fun Call.awaitAuthResponse(): Response =
    suspendCancellableCoroutine { continuation ->
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

private class OAuthBodyTooLargeException : IOException()

private fun ResponseBody.readBoundedUtf8(maxBytes: Long): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    byteStream().use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > maxBytes) throw OAuthBodyTooLargeException()
            output.write(buffer, 0, count)
        }
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

private fun isRetryableAuthHttpStatus(statusCode: Int): Boolean =
    statusCode == 408 ||
        statusCode == 421 ||
        statusCode == 425 ||
        statusCode == 429 ||
        statusCode == 500 ||
        statusCode == 502 ||
        statusCode == 503 ||
        statusCode == 504

// AUTH11_REFRESH_REJECTION_NEEDS_OAUTH_JSON_V1 (#155): a token-endpoint error
// is only proof of a dead grant when Reddit answers with an OAuth JSON body.
// An HTML block page or another non-JSON 4xx (VPN, proxy, edge) is not.
internal class OAuthJsonError(val code: String?)

private fun Response.oauthJsonError(): OAuthJsonError? {
    val contentType = body.contentType() ?: return null
    if (contentType.type != "application" ||
        (contentType.subtype != "json" && !contentType.subtype.endsWith("+json"))
    ) {
        return null
    }
    val text = runCatching { peekBody(OAUTH_ERROR_BODY_MAX_BYTES).string() }.getOrNull() ?: return null
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    val code = (root["error"] as? JsonPrimitive)?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 64 && it.all { c -> c.isLetterOrDigit() || c == '_' } }
    return OAuthJsonError(code)
}

private const val OAUTH_ERROR_BODY_MAX_BYTES = 16L * 1024L
