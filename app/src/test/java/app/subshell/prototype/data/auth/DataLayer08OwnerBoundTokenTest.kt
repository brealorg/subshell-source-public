package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DATALAYER08: a load started for account A asks for A's token. Before, the
 * five cache loaders checked that A was active and then took the active
 * account's token; after a switch to B in between, B's data was stored
 * under A's cache key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataLayer08OwnerBoundTokenTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = setOf("read", "identity"),
        permanentAccess = true,
    )

    private val accountA = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "a")
    private val accountB = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "b")

    private fun session(providerAccountId: String, token: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, "refresh-$token", "bearer", "read identity", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId, "user-$providerAccountId"),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    private fun record(accountId: String, providerAccountId: String) = StoredAuthAccountRecord(
        accountId = accountId,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerAccountId,
        username = "user-$providerAccountId",
        createdAtEpochMs = 1,
        lastUsedAtEpochMs = 1,
        clientMode = configuration.clientMode,
    )

    private fun coordinatorWithBActive(): AuthAccountSessionCoordinator {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        store.saveSession(accountA, session("a", "token-a"))
        store.saveSession(accountB, session("b", "token-b"))
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(accountA, "a"), record(accountB, "b")),
                activeAccountId = accountB,
            ),
        )
        return AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OfflineCallFactory,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun AuthSessionResult.token(): String =
        (this as AuthSessionResult.Authenticated).session.tokens.accessToken

    @Test
    fun aLoadForAGetsAsTokenWhileBIsActive() = runBlocking {
        val coordinator = coordinatorWithBActive()

        val forA = coordinator.ensureConfiguredAccessTokenForAccount(accountA).token()
        // What the loaders used to ask for: the active account's token.
        val active = coordinator.ensureActiveAccessToken().token()

        assertEquals(accountB, coordinator.activeAuthenticatedAccountId())
        assertEquals("token-a", forA)
        assertEquals("token-b", active)
    }

    /** Boundary: loaders take the token of the account they load for, never the active one. */
    @Test
    fun noRedditLoaderAsksForTheActiveAccountsToken() {
        val root = java.io.File("src/main/java/app/subshell/prototype/data/auth/reddit")
        val offenders = root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filter { file -> file.readText().contains("ensureActiveAccessToken(") }
            .map { file -> file.name }
            .toList()
        assertEquals(true, root.isDirectory)
        assertEquals(emptyList<String>(), offenders)
    }

    private object OfflineCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = error("no network in this test")
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
