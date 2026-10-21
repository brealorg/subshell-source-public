package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (safety01a32): a capability authorization started for one account
 * and finished after the reader switched accounts is bound to the active
 * account only when Reddit says it is that account. Any other identity is
 * refused, and no session — the accounts' or the unbound exchange — is
 * replaced or left behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CapabilityRebindTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val safety = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = setOf("identity", "read", "report"),
        permanentAccess = true,
    )
    private val dailyDriver = "identity read vote save submit privatemessages"
    // ISSUE237: the authorization also asks for the whole sign-in set.
    private val granted = (RedditOAuth.dailyDriverScopes + "report").joinToString(" ")
    private val oldId = authenticatedAccountId(OAuthProvider.REDDIT, safety.clientMode, "t2old")
    private val meId = authenticatedAccountId(OAuthProvider.REDDIT, safety.clientMode, "t2me")
    private val owner = AuthCapabilityAuthorizationOwner.CONTENT_SAFETY

    @Before
    fun twoSignedInAccountsWithOldActive() {
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(oldId, "t2old", "old"), record(meId, "t2me", "me")),
                activeAccountId = oldId,
            ),
        )
        store.saveSession(oldId, session("t2old", "old", "old-access"))
        store.saveSession(meId, session("t2me", "me", "me-access"))
    }

    private fun record(accountId: String, providerAccountId: String, username: String) = StoredAuthAccountRecord(
        accountId = accountId,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerAccountId,
        username = username,
        createdAtEpochMs = 1L,
        lastUsedAtEpochMs = 1L,
        clientMode = safety.clientMode,
    )

    private fun session(providerAccountId: String, username: String, accessToken: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(accessToken, "refresh", "bearer", dailyDriver, Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = providerAccountId, username = username),
        clientMode = safety.clientMode,
        clientIdFingerprint = safety.clientIdFingerprint,
    )

    /** An authorizer whose callback identity is [identity]; the coordinator is returned to switch accounts. */
    private fun authorizer(identity: AuthAccountIdentity): Pair<ConfiguredAuthCapabilityAuthorizer, AuthAccountSessionCoordinator> {
        val tokenEndpoint = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    """{"access_token":"new","token_type":"bearer","expires_in":3600,"scope":"$granted","refresh_token":"new-refresh"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        val tokenClient = OAuthTokenEndpointClient(callFactory = tokenEndpoint, configuration = safety, userAgent = "subshell-test/1.0")
        val identityClient = OAuthAccountIdentityClient { AuthIdentityResult.Success(identity) }
        val coordinator = AuthAccountSessionCoordinator(
            configuration = safety,
            store = store,
            tokenClient = tokenClient,
            identityClient = identityClient,
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { coordinator.hydratedSnapshot() }
        return ConfiguredAuthCapabilityAuthorizer(
            configuration = safety,
            store = store,
            tokenClient = tokenClient,
            identityClient = identityClient,
            accountCoordinator = coordinator,
        ) to coordinator
    }

    /** Starts the authorization for old, switches to me, then the browser returns. */
    private suspend fun beginForOldThenSwitchToMe(
        authorizer: ConfiguredAuthCapabilityAuthorizer,
        coordinator: AuthAccountSessionCoordinator,
    ) {
        authorizer.beginAuthorization(oldId, owner)
        coordinator.selectAccount(meId)
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

    @Test
    fun theActiveAccountTakesTheGrantWhenRedditSaysItIsThatAccount() = runBlocking {
        val (authorizer, coordinator) = authorizer(AuthAccountIdentity(providerAccountId = "t2me", username = "me"))
        beginForOldThenSwitchToMe(authorizer, coordinator)

        val result = authorizer.completePendingAuthorization(owner)

        result as AuthCapabilityAuthorizationResult.Upgraded
        assertEquals(meId, result.accountStableId)
        assertEquals("new", store.session(meId)!!.tokens.accessToken)
        assertEquals("old-access", store.session(oldId)!!.tokens.accessToken)
        assertNull(store.session())
    }

    @Test
    fun anotherIdentityIsRefusedAndNoSessionChanges() = runBlocking {
        val (authorizer, coordinator) = authorizer(AuthAccountIdentity(providerAccountId = "t2other", username = "other"))
        beginForOldThenSwitchToMe(authorizer, coordinator)

        val result = authorizer.completePendingAuthorization(owner)

        assertEquals(
            AuthCapabilityAuthorizationFailureKind.ACCOUNT,
            (result as AuthCapabilityAuthorizationResult.Failed).kind,
        )
        assertEquals("me-access", store.session(meId)!!.tokens.accessToken)
        assertEquals("old-access", store.session(oldId)!!.tokens.accessToken)
        assertNull(store.session())
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
