package app.subshell.prototype.data.auth

import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.auth.reddit.RedditAccountIdentityClient
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.NetworkEndpoint
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth01, auth02): the account coordinator on the real store.
 * A signed-in account gets a provider-qualified stable id; the identity is
 * read once from /api/v1/me with that sign-in's own token and an oversized
 * answer is refused; a name that belongs to a local account or to another
 * signed-in account is refused and nothing is kept; a local active account
 * has no bearer token; logging out revokes the grant at Reddit once and
 * leaves the local account active, the other account untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3AccountSessionCoordinatorTest {
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
    private val store = EncryptedAuthSessionStore(application, cipher = PlainCipher).also { it.clearAll() }
    private val tokenRequests = mutableListOf<Request>()
    private val tokenClient = OAuthTokenEndpointClient(
        callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                synchronized(tokenRequests) { tokenRequests += chain.request() }
                answer(
                    chain.request(),
                    """{"access_token":"access-new","token_type":"bearer","expires_in":3600,""" +
                        """"scope":"$scopes","refresh_token":"refresh-new"}""",
                )
            }
            .build(),
        configuration = configuration,
        userAgent = "subshell-test/1.0",
    )
    private var identity: AuthIdentityResult = AuthIdentityResult.Success(AuthAccountIdentity("t2me", "me"))

    private fun answer(request: Request, body: String, type: String = "application/json"): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("answer")
            .body(body.toResponseBody(type.toMediaType()))
            .build()

    private fun coordinator(identityClient: OAuthAccountIdentityClient = OAuthAccountIdentityClient { identity }) =
        AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = identityClient,
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )

    private fun receivedCallback() = runBlocking {
        OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            stateGenerator = { STATE },
        ).beginAuthorization()
        val accepted = OAuthCallbackProcessor(store).process(
            Uri.parse("${configuration.redirectUri}?state=$STATE&code=the-code"),
        )
        assertTrue("$accepted", accepted is AuthCallbackResult.Accepted)
    }

    private fun signedIn(vararg accounts: Pair<String, String>, active: String) {
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
                activeAccountId = active,
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

    private fun idFor(providerId: String) = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, providerId)

    @Test
    fun aSignedInAccountGetsAProviderQualifiedStableId() = runBlocking {
        receivedCallback()

        val result = coordinator().completePendingAccount()

        val account = (result as AuthAccountRegistrationResult.Registered).account
        assertEquals("reddit:redreader_compatibility:t2me", account.stableId)
        assertEquals(AccountSessionSource.AUTHENTICATED, account.source)
        assertEquals("access-new", store.session(account.stableId)?.tokens?.accessToken)
        assertNull("the in-progress sign-in session is gone", store.session())
    }

    @Test
    fun theIdentityIsOneReadOfApiV1MeWithThatSignInsTokenAndABoundedAnswer() = runBlocking {
        val identityRequests = mutableListOf<Request>()
        var identityBody = """{"id":"t2me","name":"me"}"""
        val client = RedditAccountIdentityClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    synchronized(identityRequests) { identityRequests += chain.request() }
                    answer(chain.request(), identityBody)
                }
                .build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test/1.0",
        )
        val session = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet("this-sign-in", null, "bearer", scopes, Long.MAX_VALUE),
            clientMode = configuration.clientMode,
        )

        assertEquals(AuthIdentityResult.Success(AuthAccountIdentity("t2me", "me")), client.resolve(session))
        val request = identityRequests.single()
        assertEquals("GET", request.method)
        assertEquals("https://oauth.reddit.test/api/v1/me", request.url.toString())
        assertEquals("Bearer this-sign-in", request.header("Authorization"))

        identityBody = """{"id":"t2me","name":"me"}""" + " ".repeat(64 * 1024)
        val failure = client.resolve(session) as AuthIdentityResult.Failure
        assertEquals(AuthIdentityResult.Failure(AuthIdentityFailureKind.PROTOCOL, retryable = false), failure.copy(details = null))
        // DIAG05_SIGN_IN_FAILURES_ARE_RECORDED_V1
        assertEquals(NetworkProtocolFailureKind.BODY_TOO_LARGE, failure.details?.protocolKind)
    }

    @Test
    fun aNameThatIsAlreadyTakenIsRefusedAndNothingIsKept() = runBlocking {
        receivedCallback()
        identity = AuthIdentityResult.Success(AuthAccountIdentity("t2local", "Subshell"))
        assertEquals(
            AuthAccountRegistrationResult.Failed(AuthAccountRegistrationFailureKind.LOCAL_FIXTURE_USERNAME_COLLISION),
            coordinator().completePendingAccount(),
        )
        assertNull(store.session())
        assertNull(store.session(idFor("t2local")))

        signedIn("t2me" to "me", active = idFor("t2me"))
        receivedCallback()
        identity = AuthIdentityResult.Success(AuthAccountIdentity("t2other", "ME"))
        assertEquals(
            AuthAccountRegistrationResult.Failed(AuthAccountRegistrationFailureKind.AUTHENTICATED_USERNAME_COLLISION),
            coordinator().completePendingAccount(),
        )
        assertNull(store.session())
        assertNull(store.session(idFor("t2other")))
        assertEquals(listOf(idFor("t2me")), store.accountRegistry()?.accounts?.map { it.accountId })
    }

    @Test
    fun aLocalActiveAccountHasNoBearerToken() = runBlocking {
        signedIn("t2me" to "me", active = idFor("t2me"))
        val coordinator = coordinator()
        coordinator.hydratedSnapshot()
        assertEquals("access-me", coordinator.bearerToken())

        coordinator.selectAccount("local:subshell")

        assertNull(coordinator.bearerToken())
    }

    @Test
    fun loggingOutRevokesTheGrantOnceAndLeavesTheOtherAccountAlone() = runBlocking {
        signedIn("t2me" to "me", "t2you" to "you", active = idFor("t2me"))
        val coordinator = coordinator()

        val result = coordinator.logoutActive()
        coordinator.awaitDirectoryPersistence()

        val revoke = tokenRequests.single()
        assertEquals("https://example.test/revoke", revoke.url.toString())
        val form = revoke.body as FormBody
        assertEquals(
            mapOf("token" to "refresh-me", "token_type_hint" to "refresh_token"),
            (0 until form.size).associate { form.name(it) to form.value(it) },
        )
        assertEquals(idFor("t2me"), result.removedAccountStableId)
        assertNull(store.session(idFor("t2me")))
        assertNotNull(store.session(idFor("t2you")))
        assertEquals("local:subshell", coordinator.snapshot().activeAccountId)
        assertEquals(listOf(idFor("t2you")), store.accountRegistry()?.accounts?.map { it.accountId })
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private companion object {
        const val STATE = "abcdefghijklmnopqrstuvwxyz012345"
    }
}
