package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditContentSafetyWriter
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.NetworkEndpoint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW3: a slow network call held one lock shared by unrelated work. A token
 * refresh held up every account (OCB-469), one feed or history load held up
 * every other (OCB-608/610), and a hide waited for the previous hide's
 * hidden-history refresh (OCB-498).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low3LocksPerKeyTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun differentKeysDoNotWaitForEachOtherAndTheSameKeyDoes() = runBlocking {
        val locks = KeyedMutex()
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = async(Dispatchers.IO) {
            locks.withLock("home") { held.countDown(); release.await(5, TimeUnit.SECONDS) }
        }
        assertTrue(held.await(5, TimeUnit.SECONDS))

        val other = withTimeoutOrNull(2_000) {
            withContext(Dispatchers.IO) { locks.withLock("community") { "ran" } }
        }
        val same = withTimeoutOrNull(300) {
            withContext(Dispatchers.IO) { locks.withLock("home") { "ran" } }
        }
        release.countDown()
        first.await()

        assertEquals("ran", other)
        assertEquals(null, same)
        assertEquals("ran", locks.withLock("home") { "ran" })
    }

    @Test
    fun aTokenRefreshForOneAccountDoesNotHoldUpAnotherAccount() = runBlocking {
        val configuration = OAuthProviderConfiguration(
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
        val slow = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2slow")
        val fast = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2fast")
        val store = EncryptedAuthSessionStore(application, cipher = PlainCipher)
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(slow, "t2slow", "slow", configuration), record(fast, "t2fast", "fast", configuration)),
                activeAccountId = slow,
            ),
        )
        // The slow account's token has expired (refresh over the network), the fast one's is valid.
        store.saveSession(slow, session("t2slow", "slow", expiresAt = 1L, configuration))
        store.saveSession(fast, session("t2fast", "fast", expiresAt = Long.MAX_VALUE, configuration))
        val refreshing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val tokenEndpoint = OkHttpClient.Builder().addInterceptor { chain ->
            refreshing.countDown()
            release.await(10, TimeUnit.SECONDS)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(400).message("answer")
                .body("""{"error":"invalid_grant"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = tokenEndpoint, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.IO,
        )
        coordinator.hydratedSnapshot()

        val slowResult = async(Dispatchers.IO) {
            coordinator.ensureAccessTokenForAccount(slow, RedditOAuth.coreReadScopes)
        }
        assertTrue(refreshing.await(5, TimeUnit.SECONDS))
        val fastResult = withTimeoutOrNull(3_000) {
            coordinator.ensureAccessTokenForAccount(fast, RedditOAuth.coreReadScopes)
        }
        release.countDown()
        slowResult.await()

        assertTrue("$fastResult", fastResult is AuthSessionResult.Authenticated)
    }

    @Test
    fun aHideDoesNotWaitForThePreviousHidesHistoryRefresh() = runBlocking {
        val refreshCalls = AtomicInteger()
        val refreshing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer = ConfiguredRedditContentSafetyWriter(
            capability = AuthorizedCapability(
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("access", "refresh", "bearer", "report", Long.MAX_VALUE),
                    identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                ),
            ),
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("answer")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            hiddenHistoryUpdater = AuthenticatedRedditAccountHistoryUpdater { _, _, _, _ ->
                if (refreshCalls.incrementAndGet() == 1) {
                    refreshing.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
                AuthenticatedRedditAccountHistoryUpdateResult.AuthorizationRequired
            },
            ioDispatcher = Dispatchers.IO,
        )

        val first = async(Dispatchers.IO) {
            writer.setPostHidden(RedditPostVisibilityRequest("reddit:me", "t3_first", hidden = true))
        }
        assertTrue(refreshing.await(5, TimeUnit.SECONDS))
        val second = withTimeoutOrNull(3_000) {
            writer.setPostHidden(RedditPostVisibilityRequest("reddit:me", "t3_second", hidden = true))
        }
        release.countDown()
        val firstResult = first.await()

        assertTrue("$second", second is AuthenticatedContentSafetyResult.Updated)
        // The refresh still runs and is still reported, only outside the lock.
        firstResult as AuthenticatedContentSafetyResult.Updated
        assertEquals(HiddenHistoryReconciliation.AUTHORIZATION_REQUIRED, firstResult.hiddenHistoryReconciliation)
        assertEquals(2, refreshCalls.get())
    }

    private fun record(id: String, providerId: String, name: String, configuration: OAuthProviderConfiguration) =
        StoredAuthAccountRecord(
            accountId = id,
            provider = OAuthProvider.REDDIT,
            providerAccountId = providerId,
            username = name,
            createdAtEpochMs = 1L,
            lastUsedAtEpochMs = 1L,
            clientMode = configuration.clientMode,
        )

    private fun session(providerId: String, name: String, expiresAt: Long, configuration: OAuthProviderConfiguration) =
        StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet("access-$name", "refresh-$name", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), expiresAt),
            identity = AuthAccountIdentity(providerAccountId = providerId, username = name),
            clientMode = configuration.clientMode,
            clientIdFingerprint = configuration.clientIdFingerprint,
        )

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private class AuthorizedCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(session)
    }
}
