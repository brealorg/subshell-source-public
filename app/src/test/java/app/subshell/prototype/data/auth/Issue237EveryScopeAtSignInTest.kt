package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
 * ISSUE237 (#237): sign-in asks once for every scope a capability uses, so a
 * fresh account opens Modmail, mod log, user management, the moderator team
 * and the wiki without another approval; an account signed in before approves
 * at most once, and that one approval covers every other capability.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue237EveryScopeAtSignInTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val laterScopes = setOf("modmail", "modlog", "modcontributors", "modothers", "wikiread")
    private val askedBefore = (RedditOAuth.dailyDriverScopes - laterScopes).joinToString(" ")
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.NATIVE_PRODUCTION, "t2me")

    private fun configuration(capability: RedditOAuthCapability) = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.scopesFor(capability),
        permanentAccess = true,
    )

    private val laterCapabilities = listOf(
        RedditOAuthCapability.MODMAIL_READ,
        RedditOAuthCapability.MODERATION_LOG,
        RedditOAuthCapability.MODERATION_USERS,
        RedditOAuthCapability.MODERATION_TEAM,
        RedditOAuthCapability.COMMUNITY_WIKI_READ,
    )

    @Before
    fun clearStore() {
        store.clearAll()
    }

    private fun signedInWith(scope: String) {
        val clientFingerprint = configuration(RedditOAuthCapability.CORE_READ).clientIdFingerprint
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
                        clientMode = OAuthClientMode.NATIVE_PRODUCTION,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(
            accountId,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access", "refresh", "bearer", scope, Long.MAX_VALUE),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = OAuthClientMode.NATIVE_PRODUCTION,
                clientIdFingerprint = clientFingerprint,
            ),
        )
    }

    private fun authorizer(
        capability: RedditOAuthCapability,
        grantedScope: () -> String = { "" },
    ): ConfiguredAuthCapabilityAuthorizer {
        val configuration = configuration(capability)
        val tokenEndpoint = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    """{"access_token":"new","token_type":"bearer","expires_in":3600,"scope":"${grantedScope()}","refresh_token":"new-refresh"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        val tokenClient = OAuthTokenEndpointClient(callFactory = tokenEndpoint, configuration = configuration, userAgent = "subshell-test/1.0")
        val identity = OAuthAccountIdentityClient {
            AuthIdentityResult.Success(AuthAccountIdentity(providerAccountId = "t2me", username = "me"))
        }
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = identity,
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { coordinator.hydratedSnapshot() }
        return ConfiguredAuthCapabilityAuthorizer(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = identity,
            accountCoordinator = coordinator,
        )
    }

    private fun requestedScopes(url: String): Set<String> =
        url.toHttpUrl().queryParameter("scope").orEmpty().split(' ', '+').filter(String::isNotBlank).toSet()

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

    @Test
    fun signInAsksForEveryScopeACapabilityUses() {
        val everyCapabilityScope = RedditOAuthCapability.entries.flatMap(RedditOAuth::scopesFor).toSet() +
            RedditOAuth.liveTextPostWriteScopes

        assertEquals(everyCapabilityScope, RedditOAuth.dailyDriverScopes)
    }

    @Test
    fun aFreshSignInOpensEveryModeratorAndWikiCapabilityWithoutAskingAgain() = runBlocking {
        signedInWith(RedditOAuth.dailyDriverScopes.joinToString(" "))

        laterCapabilities.forEach { capability ->
            val result = authorizer(capability).ensureAuthorized(accountId)
            assertTrue("$capability: $result", result is AuthSessionResult.Authenticated)
        }
    }

    @Test
    fun anAccountSignedInBeforeAsksOnceForTheWholeSignInSet() = runBlocking {
        signedInWith(askedBefore)
        val modmail = authorizer(RedditOAuthCapability.MODMAIL_READ)
        assertEquals(
            AuthSessionResult.AuthorizationDenied("missing_incremental_scope"),
            modmail.ensureAuthorized(accountId),
        )

        val url = modmail.beginAuthorization(accountId, AuthCapabilityAuthorizationOwner.MODMAIL_READ)

        assertEquals(RedditOAuth.dailyDriverScopes, requestedScopes(url))
    }

    @Test
    fun thatOneApprovalCoversEveryOtherCapability() = runBlocking {
        signedInWith(askedBefore)
        // Reddit grants what the authorization asked for.
        var requested = emptySet<String>()
        val modmail = authorizer(RedditOAuthCapability.MODMAIL_READ, grantedScope = { requested.joinToString(" ") })
        requested = requestedScopes(modmail.beginAuthorization(accountId, AuthCapabilityAuthorizationOwner.MODMAIL_READ))
        storeCallbackForPending()
        val upgraded = modmail.completePendingAuthorization(AuthCapabilityAuthorizationOwner.MODMAIL_READ)
        assertTrue(upgraded.toString(), upgraded is AuthCapabilityAuthorizationResult.Upgraded)

        laterCapabilities.forEach { capability ->
            val result = authorizer(capability).ensureAuthorized(accountId)
            assertTrue("$capability: $result", result is AuthSessionResult.Authenticated)
        }
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
