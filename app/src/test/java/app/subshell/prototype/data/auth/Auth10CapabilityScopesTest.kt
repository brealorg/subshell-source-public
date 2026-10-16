package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTH10 (#59, audit OCB-328/337/342/357): an incremental capability
 * authorization asks for the capability's scopes plus every scope the
 * account's token already has, so the replacement token never drops earlier
 * grants; a replacement that does not cover them is refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth10CapabilityScopesTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val wikiRead = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = setOf("identity", "read", "wikiread"),
        permanentAccess = true,
    )
    private val dailyDriver = "identity read vote save submit privatemessages"
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, wikiRead.clientMode, "t2me")

    @Before
    fun signedInWithDailyDriverScopes() {
        store.clearAll()
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
                        clientMode = wikiRead.clientMode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(accountId, session(dailyDriver))
    }

    private fun session(scope: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", scope, Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
        clientMode = wikiRead.clientMode,
        clientIdFingerprint = wikiRead.clientIdFingerprint,
    )

    private fun authorizer(grantedScope: String = ""): ConfiguredAuthCapabilityAuthorizer {
        val tokenEndpoint = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    """{"access_token":"new","token_type":"bearer","expires_in":3600,"scope":"$grantedScope","refresh_token":"new-refresh"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        val tokenClient = OAuthTokenEndpointClient(callFactory = tokenEndpoint, configuration = wikiRead, userAgent = "subshell-test/1.0")
        val identity = OAuthAccountIdentityClient {
            AuthIdentityResult.Success(AuthAccountIdentity(providerAccountId = "t2me", username = "me"))
        }
        val coordinator = AuthAccountSessionCoordinator(
            configuration = wikiRead,
            store = store,
            tokenClient = tokenClient,
            identityClient = identity,
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { coordinator.hydratedSnapshot() }
        return ConfiguredAuthCapabilityAuthorizer(
            configuration = wikiRead,
            store = store,
            tokenClient = tokenClient,
            identityClient = identity,
            accountCoordinator = coordinator,
        )
    }

    private fun requestedScopes(url: String): Set<String> =
        url.toHttpUrl().queryParameter("scope").orEmpty().split(' ', '+').filter(String::isNotBlank).toSet()

    /** The browser returns to the app with a code for the pending authorization. */
    private fun storeCallbackForPending() {
        val pending = store.pendingAuthorization()!!
        store.saveCallback(
            StoredOAuthCallback(
                provider = pending.provider,
                state = pending.state,
                code = "code",
                error = null,
                receivedAtEpochMs = 2L,
                clientMode = pending.clientMode,
                clientIdFingerprint = pending.clientIdFingerprint,
                scopeFingerprint = pending.scopeFingerprint,
                accountStableId = pending.accountStableId,
                authorizationOwnerKey = pending.authorizationOwnerKey,
                authorizationContextKey = pending.authorizationContextKey,
            ),
        )
    }

    private val owner = AuthCapabilityAuthorizationOwner.entries.first()

    @Test
    fun anIncrementalAuthorizationAlsoRequestsTheScopesTheAccountAlreadyHas() = runBlocking<Unit> {
        val url = authorizer().beginAuthorization(accountId, owner)

        val requested = requestedScopes(url)
        assertTrue(requested.toString(), requested.containsAll(setOf("vote", "save", "submit", "privatemessages", "wikiread")))
    }

    @Test
    fun theUpgradedSessionKeepsEveryEarlierScope() = runBlocking {
        val granted = "identity read vote save submit privatemessages wikiread"
        val authorizer = authorizer(grantedScope = granted)
        authorizer.beginAuthorization(accountId, owner)
        storeCallbackForPending()

        val result = authorizer.completePendingAuthorization(owner)

        assertTrue(result.toString(), result is AuthCapabilityAuthorizationResult.Upgraded)
        assertEquals(granted.split(' ').toSet(), store.session(accountId)!!.tokens.scope.split(' ').toSet())
    }

    @Test
    fun aTokenThatWouldDropEarlierScopesIsRefusedAndTheSessionIsKept() = runBlocking {
        val authorizer = authorizer(grantedScope = "identity read wikiread")
        authorizer.beginAuthorization(accountId, owner)
        storeCallbackForPending()

        val result = authorizer.completePendingAuthorization(owner)

        assertEquals(AuthCapabilityAuthorizationFailureKind.SCOPE, (result as AuthCapabilityAuthorizationResult.Failed).kind)
        assertEquals(dailyDriver.split(' ').toSet(), store.session(accountId)!!.tokens.scope.split(' ').toSet())
    }

    // STEP3 (mod01a21, modmail01a1): a session without a capability's scope
    // asks for that scope and stays signed in; nothing about the account is
    // dropped.
    @Test
    fun aMissingCapabilityScopeAsksForItAndKeepsTheSession() = runBlocking {
        val result = authorizer().ensureAuthorized(accountId)

        assertEquals(AuthSessionResult.AuthorizationDenied("missing_incremental_scope"), result)
        assertEquals(dailyDriver.split(' ').toSet(), store.session(accountId)!!.tokens.scope.split(' ').toSet())
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
