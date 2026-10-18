package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthAccountIdentityClient
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredAuthAccountRecord
import app.subshell.prototype.data.auth.StoredAuthAccountRegistry
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a2): a read Reddit did not answer keeps the reason for the
 * screen — a rate limit its status and wait, an unreachable host its network
 * kind, a broken answer its response kind — for subscriptions and comments;
 * and a session that could not be refreshed says how the sign-in service
 * answered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ReadFailureDetailsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cacheStore = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.coreReadScopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "t2me")
    private val endpoint = NetworkEndpoint.https("https://oauth.reddit.test/")

    @After
    fun close() {
        database.close()
    }

    private fun answering(code: Int, type: String = "application/json", body: String = "{}", retryAfter: String? = null) =
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("answer")
                    .apply { retryAfter?.let { header("Retry-After", it) } }
                    .body(body.toResponseBody(type.toMediaType()))
                    .build()
            },
        ).build()

    private val unreachable = OkHttpClient.Builder().addInterceptor { throw UnknownHostException("oauth.reddit.test") }.build()

    private suspend fun coordinator(expiresAt: Long = Long.MAX_VALUE, tokenEndpoint: Call.Factory = answering(500)): AuthAccountSessionCoordinator {
        val store = EncryptedAuthSessionStore(application, cipher = PlainCipher)
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = account,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = account,
            ),
        )
        store.saveSession(
            account,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), expiresAt),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = tokenEndpoint, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no identity in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        return coordinator
    }

    private suspend fun subscriptionsFailure(network: Call.Factory, coordinator: AuthAccountSessionCoordinator? = null): FailureDetails? {
        val loader = RedditSubscriptionsLoader(
            callFactory = network,
            endpoint = endpoint,
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cacheStore,
            accountCoordinator = coordinator ?: coordinator(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val result = loader.synchronize(account)
        assertTrue("$result", result is AuthenticatedRedditSubscriptionsResult.Failed)
        return (result as AuthenticatedRedditSubscriptionsResult.Failed).details
    }

    private suspend fun commentsFailure(network: Call.Factory): FailureDetails? {
        val loader = RedditCommentLoader(
            callFactory = network,
            endpoint = endpoint,
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cacheStore,
            accountCoordinator = coordinator(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val result = loader.synchronize(account, "abc123", RedditCommentSort.BEST)
        assertTrue("$result", result is AuthenticatedRedditCommentResult.Failed)
        return (result as AuthenticatedRedditCommentResult.Failed).details
    }

    @Test
    fun aRateLimitKeepsItsStatusAndWait() = runBlocking {
        for (details in listOf(
            subscriptionsFailure(answering(429, retryAfter = "30")),
            commentsFailure(answering(429, retryAfter = "30")),
        )) {
            assertEquals(429, details?.httpStatusCode)
            assertEquals(30_000L, details?.retryAfterMs)
        }
    }

    @Test
    fun anUnreachableHostKeepsItsNetworkKind() = runBlocking {
        assertEquals(NetworkTransportFailureKind.DNS, subscriptionsFailure(unreachable)?.transportKind)
        assertEquals(NetworkTransportFailureKind.DNS, commentsFailure(unreachable)?.transportKind)
    }

    @Test
    fun aBrokenAnswerKeepsItsResponseKind() = runBlocking {
        assertEquals(NetworkProtocolFailureKind.MALFORMED_JSON, subscriptionsFailure(answering(200, body = "{broken"))?.protocolKind)
        assertEquals(NetworkProtocolFailureKind.MALFORMED_JSON, commentsFailure(answering(200, body = "{broken"))?.protocolKind)
    }

    // DIAG04_JSON_PATH_REACHES_THE_REPORT_V1: the focused-comment read kept no details at all.
    @Test
    fun aBrokenFocusedThreadAnswerKeepsItsResponseKind() = runBlocking {
        val loader = RedditCommentLoader(
            callFactory = answering(200, body = "{broken"),
            endpoint = endpoint,
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cacheStore,
            accountCoordinator = coordinator(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val result = loader.synchronizeFocused(account, "abc123", RedditCommentSort.BEST, "def456", context = 3)

        assertTrue("$result", result is AuthenticatedRedditCommentResult.Failed)
        assertEquals(
            NetworkProtocolFailureKind.MALFORMED_JSON,
            (result as AuthenticatedRedditCommentResult.Failed).details?.protocolKind,
        )
    }

    @Test
    fun aSessionThatCouldNotBeRefreshedSaysHowTheSignInServiceAnswered() = runBlocking {
        val unavailable = coordinator(expiresAt = 1L, tokenEndpoint = answering(503, type = "text/plain", body = "down"))
        val details = subscriptionsFailure(answering(200), unavailable)

        assertEquals(503, details?.httpStatusCode)
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
