package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostVoter
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTH17 (#167/#168/#169 low checklists, OCB-480/491/499/501/518/521): about
 * 40 readers and writers built their client with a plain bearer provider, so
 * Reddit's 401 never marked the token expired and the same dead token was
 * sent until its stored expiry. They now take the provider from their
 * capability, which is the account coordinator's session-bound one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth17SessionBoundBearerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access-old", "refresh-1", "bearer", "vote", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    @Test
    fun aWriterReportsReddits401ToItsCapabilitysProvider() = runBlocking {
        var rejected: String? = null
        val capability = object : AuthCommentWriteCapability {
            override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
            override fun hasPendingAuthorization() = false
            override fun hasStoredCallback() = false
            override fun cancelPendingAuthorization() = Unit
            override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
            override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                AuthSessionResult.Authenticated(session)
            override fun bearerTokenProvider(accessToken: String) = object : NetworkBearerTokenProvider {
                override fun bearerToken() = accessToken
                override fun onUnauthorized() {
                    rejected = accessToken
                }
            }
        }
        val voter = ConfiguredRedditPostVoter(
            capability = capability,
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(401).message("Unauthorized")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            ioDispatcher = Dispatchers.Unconfined,
        )

        voter.vote(PostVoteRequest(accountStableId = "reddit:me", postFullname = "t3_abc", direction = 1))

        assertEquals("access-old", rejected)
    }

    @Test
    fun ownerBoundCapabilitiesTakeTheAuthorizersProvider() {
        val provider = NetworkBearerTokenProvider { "x" }
        val authorizer = object : AuthCapabilityAuthorizer {
            override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner) = ""
            override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = false
            override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner) = false
            override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = Unit
            override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner) =
                error("unused")
            override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("unused")
            override fun bearerTokenProvider(accessToken: String) = provider
        }

        assertSame(
            provider,
            OwnerBoundAuthCommentWriteCapability(authorizer, AuthCapabilityAuthorizationOwner.COMMENT_WRITE)
                .bearerTokenProvider("t"),
        )
    }

    @Test
    fun theConfiguredProviderMarksTheStoredTokenExpiredAndKeepsTheRefreshToken() = runBlocking {
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
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = accountId,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(accountId, session.copy(clientIdFingerprint = configuration.clientIdFingerprint))
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OkHttpClient(),
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()

        ConfiguredAuthProfileHistoryCapability(configuration, coordinator)
            .bearerTokenProvider("access-old")
            .onUnauthorized()

        val kept = store.session(accountId)
        assertEquals("refresh-1", kept?.tokens?.refreshToken)
        assertTrue(kept!!.tokens.expiresAtEpochMs < Long.MAX_VALUE)
    }

    @Test
    fun noRedditReaderOrWriterBuildsAPlainBearerProvider() {
        val dir = File("src/main/java/app/subshell/prototype/data/auth/reddit")
        val plain = dir.listFiles().orEmpty()
            // Sign-in steps that run before or inside the account coordinator.
            .filter { file -> file.name !in setOf("RedditAccountIdentityClient.kt", "RedditBootstrapper.kt") }
            // A plain lambda provider; `object : NetworkBearerTokenProvider {` wraps one.
            .filter { file -> Regex("""(?<!: )NetworkBearerTokenProvider \{""").containsMatchIn(file.readText()) }
            .map(File::getName)

        assertEquals(emptyList<String>(), plain)
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
