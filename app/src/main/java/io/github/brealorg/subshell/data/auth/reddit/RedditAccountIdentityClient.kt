package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthIdentityFailureKind
import io.github.brealorg.subshell.data.auth.AuthIdentityResult
import io.github.brealorg.subshell.data.auth.OAuthAccountIdentityClient
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.network.JsonGetRequest
import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call

internal class RedditAccountIdentityClient(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val json: Json = SubshellNetworkJson.providerJson,
) : OAuthAccountIdentityClient {
    override suspend fun resolve(session: StoredOAuthSession): AuthIdentityResult {
        // CONC03_IDENTITY_TOKEN_PER_CALL_V1 (OCB-609): the token was passed through
        // a shared mutable field, so two overlapping resolves could send each
        // other's token. Each call now builds its client with its own token.
        val accessToken = session.tokens.accessToken
        val httpClient = SubshellHttpClient(
            callFactory = callFactory,
            endpoint = endpoint,
            json = json,
            userAgent = userAgent,
            bearerTokenProvider = NetworkBearerTokenProvider { accessToken },
            maxJsonBodyBytes = MAX_IDENTITY_BODY_BYTES,
        )
        return when (
            val result = httpClient.getJson(
                request = JsonGetRequest(pathSegments = listOf("api", "v1", "me")),
                deserializer = RedditIdentityDto.serializer(),
            )
        ) {
            is NetworkResult.Success -> {
                val id = result.value.id.trim()
                val username = result.value.name.trim()
                if (id.isEmpty() || username.isEmpty()) {
                    AuthIdentityResult.Failure(
                        kind = AuthIdentityFailureKind.INVALID_IDENTITY,
                        retryable = false,
                    )
                } else {
                    AuthIdentityResult.Success(
                        AuthAccountIdentity(
                            providerAccountId = id,
                            username = username,
                        ),
                    )
                }
            }
            is NetworkResult.HttpFailure -> AuthIdentityResult.Failure(
                kind = AuthIdentityFailureKind.HTTP,
                retryable = result.retryable,
                details = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> AuthIdentityResult.Failure(
                kind = AuthIdentityFailureKind.TRANSPORT,
                retryable = result.retryable,
                details = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure -> AuthIdentityResult.Failure(
                kind = AuthIdentityFailureKind.PROTOCOL,
                retryable = false,
                details = result.failureDetailsOrNull(),
            )
        }
    }

    companion object {
        const val MAX_IDENTITY_BODY_BYTES = 64L * 1024L
    }
}

@Serializable
private data class RedditIdentityDto(
    val id: String,
    val name: String,
)
