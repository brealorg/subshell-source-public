package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (auth01): the token endpoint client sends Reddit's installed-app
 * requests and nothing else: Basic credentials with the client ID and an
 * empty secret, one form POST per exchange, refresh or revoke; an answer
 * over 64 KiB or not JSON is refused; a TLS failure is final while a lost
 * connection may be retried. A local interceptor answers; nothing leaves the
 * test.
 */
class Step3OAuthTokenEndpointTest {
    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id-123",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.coreReadScopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val requests = mutableListOf<Request>()
    private var answer: () -> Response.Builder = { json(TOKENS) }
    private var failure: IOException? = null

    private val client = OAuthTokenEndpointClient(
        callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                synchronized(requests) { requests += chain.request() }
                failure?.let { throw it }
                answer().request(chain.request()).build()
            }
            .build(),
        configuration = configuration,
        userAgent = "subshell-test/1.0",
    )

    private fun json(body: String, type: String = "application/json", code: Int = 200) =
        Response.Builder()
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("answer")
            .body(body.toResponseBody(type.toMediaType()))

    private fun Request.form(): Map<String, String> {
        val form = body as FormBody
        return (0 until form.size).associate { index -> form.name(index) to form.value(index) }
    }

    @Test
    fun aCodeExchangeIsOneFormPostWithTheInstalledAppCredentials() = runBlocking {
        val result = client.exchangeAuthorizationCode("the-code")

        assertTrue("$result", result is OAuthEndpointResult.Success)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://example.test/token", request.url.toString())
        assertEquals(Credentials.basic("client-id-123", ""), request.header("Authorization"))
        assertEquals("subshell-test/1.0", request.header("User-Agent"))
        assertEquals(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to "the-code",
                "redirect_uri" to "subshell-auth-test://oauth/callback",
            ),
            request.form(),
        )
    }

    @Test
    fun aRefreshAndARevokeSendOnlyTheirOwnFields() = runBlocking {
        client.refresh("refresh-1")
        answer = { json("", type = "text/plain") }
        val revoked = client.revoke("refresh-1", "refresh_token")

        assertEquals(OAuthEndpointResult.Success(Unit), revoked)
        val (refresh, revoke) = requests
        assertEquals(mapOf("grant_type" to "refresh_token", "refresh_token" to "refresh-1"), refresh.form())
        assertEquals("https://example.test/revoke", revoke.url.toString())
        assertEquals(Credentials.basic("client-id-123", ""), revoke.header("Authorization"))
        assertEquals(mapOf("token" to "refresh-1", "token_type_hint" to "refresh_token"), revoke.form())
    }

    @Test
    fun redditsInstalledAppUsesRedditsOwnTokenAndRevokeEndpointsWithoutASecret() = runBlocking {
        val reddit = RedditOAuth.installedApplication(
            clientId = "client-id-123",
            redirectUri = "subshell-auth-test://oauth/callback",
            scopes = RedditOAuth.coreReadScopes,
        )
        val redditClient = OAuthTokenEndpointClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    synchronized(requests) { requests += chain.request() }
                    json(TOKENS).request(chain.request()).build()
                }
                .build(),
            configuration = reddit,
            userAgent = "subshell-test/1.0",
        )

        redditClient.exchangeAuthorizationCode("the-code")
        redditClient.revoke("refresh-1", "refresh_token")

        assertEquals(
            listOf("https://www.reddit.com/api/v1/access_token", "https://www.reddit.com/api/v1/revoke_token"),
            requests.map { it.url.toString() },
        )
        requests.forEach { assertEquals(Credentials.basic("client-id-123", ""), it.header("Authorization")) }
    }

    @Test
    fun anOversizedOrNonJsonAnswerIsRefused() = runBlocking {
        answer = { json(TOKENS + " ".repeat(64 * 1024 + 1 - TOKENS.length)) }
        assertEquals(
            OAuthEndpointResult.ProtocolFailure(OAuthProtocolFailureKind.BODY_TOO_LARGE),
            client.exchangeAuthorizationCode("the-code"),
        )

        answer = { json(TOKENS, type = "text/html") }
        assertEquals(
            OAuthEndpointResult.ProtocolFailure(OAuthProtocolFailureKind.UNEXPECTED_CONTENT_TYPE),
            client.exchangeAuthorizationCode("the-code"),
        )
    }

    @Test
    fun aTlsFailureIsFinalAndALostConnectionMayBeRetried() = runBlocking {
        failure = SSLException("handshake")
        assertEquals(OAuthEndpointResult.TransportFailure(retryable = false), client.refresh("refresh-1"))

        failure = IOException("connection reset")
        assertEquals(OAuthEndpointResult.TransportFailure(retryable = true), client.refresh("refresh-1"))
        assertEquals("each call is one request", 2, requests.size)
    }

    private companion object {
        val TOKENS = """{"access_token":"access-1","token_type":"bearer","expires_in":3600,""" +
            """"scope":"identity mysubreddits read","refresh_token":"refresh-1"}"""
    }
}
