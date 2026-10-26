package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECAUDIT31_FAILED_REGISTRATION_REVOKES_ITS_GRANT_V1 (security audit
 * 2026-10-22, #31): a sign-in that exchanged its code but is not kept gives
 * the new grant back at Reddit, and only that grant; a failed revoke leaves
 * the sign-in's own failure unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit31FailedRegistrationRevokesItsGrantTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
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
    private val scopes = RedditOAuth.coreReadScopes.joinToString(" ")
    private val cipher = PlainCipher()
    private val store = EncryptedAuthSessionStore(application, cipher = cipher).also { it.clearAll() }
    private val revokes = mutableListOf<Map<String, String>>()
    private var grantedScopes = scopes
    private var revokeFails = false
    private val tokenClient = OAuthTokenEndpointClient(
        callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath == "/revoke") {
                    val form = request.body as FormBody
                    synchronized(revokes) { revokes += (0 until form.size).associate { form.name(it) to form.value(it) } }
                    if (revokeFails) throw IOException("no network")
                    answer(request, "")
                } else {
                    answer(
                        request,
                        """{"access_token":"access-new","token_type":"bearer","expires_in":3600,""" +
                            """"scope":"$grantedScopes","refresh_token":"refresh-new"}""",
                    )
                }
            }
            .build(),
        configuration = configuration,
        userAgent = "subshell-test/1.0",
    )
    private var identity: AuthIdentityResult = AuthIdentityResult.Success(AuthAccountIdentity("t2me", "me"))
    private val newGrant = mapOf("token" to "refresh-new", "token_type_hint" to "refresh_token")

    @Test
    fun aSignInThatIsNotKeptRevokesItsNewGrantOnly() = runBlocking {
        signedIn("t2you" to "you")
        val failures = mapOf(
            AuthIdentityResult.Failure(AuthIdentityFailureKind.PROTOCOL, retryable = false) to
                AuthAccountRegistrationFailureKind.IDENTITY,
            AuthIdentityResult.Success(AuthAccountIdentity("t2local", "Subshell")) to
                AuthAccountRegistrationFailureKind.LOCAL_FIXTURE_USERNAME_COLLISION,
            AuthIdentityResult.Success(AuthAccountIdentity("t2other", "YOU")) to
                AuthAccountRegistrationFailureKind.AUTHENTICATED_USERNAME_COLLISION,
        )
        failures.forEach { (answer, kind) ->
            revokes.clear()
            receivedCallback()
            identity = answer

            assertEquals(AuthAccountRegistrationResult.Failed(kind), coordinator().completePendingAccount())

            assertEquals("$kind", listOf(newGrant), revokes)
            assertNull("$kind", store.session())
            assertEquals("access-you", store.session(idFor("t2you"))?.tokens?.accessToken)
        }
    }

    @Test
    fun reauthorizingAsAnotherAccountRevokesTheNewGrantAndKeepsBothAccounts() = runBlocking {
        signedIn("t2me" to "me", "t2you" to "you")
        receivedCallback()
        identity = AuthIdentityResult.Success(AuthAccountIdentity("t2you", "you"))

        val result = coordinator().completePendingAccount(
            authorizationSessionManager = null,
            expectedAccountStableId = idFor("t2me"),
        )

        assertEquals(
            AuthAccountRegistrationResult.Failed(AuthAccountRegistrationFailureKind.REAUTHENTICATION_ACCOUNT_MISMATCH),
            result,
        )
        assertEquals(listOf(newGrant), revokes)
        assertEquals("access-me", store.session(idFor("t2me"))?.tokens?.accessToken)
        assertEquals("access-you", store.session(idFor("t2you"))?.tokens?.accessToken)
    }

    @Test
    fun aGrantWithoutTheRequestedScopesIsRevoked() = runBlocking {
        grantedScopes = "identity"
        receivedCallback()

        assertEquals(
            AuthAccountRegistrationResult.Failed(AuthAccountRegistrationFailureKind.CLIENT_CONFIGURATION),
            coordinator().completePendingAccount(),
        )
        assertEquals(listOf(newGrant), revokes)
        assertNull(store.session())
    }

    @Test
    fun aGrantThatCannotBeStoredIsRevoked() = runBlocking {
        receivedCallback()
        cipher.failEncrypt = true

        assertEquals(
            AuthAccountRegistrationResult.Failed(AuthAccountRegistrationFailureKind.STORAGE),
            coordinator().completePendingAccount(),
        )
        assertEquals(listOf(newGrant), revokes)
    }

    @Test
    fun aFailedRevokeLeavesTheSignInFailureAsItWas() = runBlocking {
        revokeFails = true
        receivedCallback()
        identity = AuthIdentityResult.Failure(AuthIdentityFailureKind.PROTOCOL, retryable = true)

        assertEquals(
            AuthAccountRegistrationResult.Failed(AuthAccountRegistrationFailureKind.IDENTITY, retryable = true),
            coordinator().completePendingAccount(),
        )
        assertEquals(listOf(newGrant), revokes)
        assertNull(store.session())
    }

    @Test
    fun aKeptSignInRevokesNothing() = runBlocking {
        receivedCallback()

        val result = coordinator().completePendingAccount()

        assertEquals(idFor("t2me"), (result as AuthAccountRegistrationResult.Registered).account.stableId)
        assertEquals(emptyList<Map<String, String>>(), revokes)
    }

    private fun answer(request: Request, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("answer")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun coordinator() =
        AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = OAuthAccountIdentityClient { identity },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )

    private fun receivedCallback() {
        store.saveCallback(
            StoredOAuthCallback(
                provider = OAuthProvider.REDDIT,
                state = "state",
                code = "code",
                error = null,
                receivedAtEpochMs = 1L,
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
                scopeFingerprint = configuration.scopeFingerprint,
            ),
        )
    }

    private fun signedIn(vararg accounts: Pair<String, String>) {
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = accounts.map { (providerId, name) ->
                    StoredAuthAccountRecord(
                        accountId = idFor(providerId),
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = providerId,
                        username = name,
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    )
                },
                activeAccountId = idFor(accounts.first().first),
            ),
        )
        accounts.forEach { (providerId, name) ->
            store.saveSession(
                idFor(providerId),
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("access-$name", "refresh-$name", "bearer", scopes, Long.MAX_VALUE),
                    identity = AuthAccountIdentity(providerId, name),
                    clientMode = configuration.clientMode,
                    clientIdFingerprint = configuration.clientIdFingerprint,
                ),
            )
        }
    }

    private fun idFor(providerId: String) =
        authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, providerId)

    private class PlainCipher : AuthCipher {
        var failEncrypt = false

        override fun encrypt(plaintext: ByteArray): ByteArray =
            if (failEncrypt) throw IllegalStateException("keystore unavailable") else plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
