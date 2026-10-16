package app.subshell.prototype.data.auth

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.ConnectAccountFailureKind
import app.subshell.prototype.data.account.ConnectAccountPhase
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth03): the connect flow on the real store. A failed token
 * exchange or identity read ends in an error that names the stage, keeps no
 * account and leaves no sign-in records or token behind; a name already in
 * use is an account conflict; the same callback delivered again after the
 * account connected changes nothing and asks Reddit nothing; a retry after
 * a failure connects and the result carries only the account's name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ConnectFlowFailuresTest {
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
    private var tokenRequests = 0
    private var tokenAnswer: Pair<Int, String> = 200 to
        """{"access_token":"access-1","token_type":"bearer","expires_in":3600,"scope":"$scopes","refresh_token":"refresh-1"}"""
    private var identity: AuthIdentityResult = AuthIdentityResult.Success(AuthAccountIdentity("t2me", "me"))

    private val tokenClient = OAuthTokenEndpointClient(
        callFactory = OkHttpClient.Builder()
            .addInterceptor { chain ->
                tokenRequests += 1
                val (code, body) = tokenAnswer
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("answer")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build(),
        configuration = configuration,
        userAgent = "subshell-test/1.0",
    )
    private val sessionManager = OAuthSessionManager(
        configuration = configuration,
        store = store,
        tokenClient = tokenClient,
        stateGenerator = { STATE },
    )
    private val flow = ConfiguredAuthConnectFlowController(
        sessionManager = sessionManager,
        accountCoordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = OAuthAccountIdentityClient { identity },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        ),
        store = store,
        providerLabel = "Reddit",
        clientMode = configuration.clientMode,
        storageDispatcher = Dispatchers.Unconfined,
    )

    private suspend fun signInAndReturn() {
        flow.beginAuthorization()
        val accepted = OAuthCallbackProcessor(store).process(
            Uri.parse("${configuration.redirectUri}?state=$STATE&code=the-code"),
        )
        assertTrue("$accepted", accepted is AuthCallbackResult.Accepted)
        flow.handleCallbackResultToken("accepted")
    }

    private fun assertNothingLeftBehind() {
        assertNull("pending", store.pendingAuthorization())
        assertNull("callback", store.callback())
        assertNull("unbound session", store.session())
        assertTrue("no account", store.accountRegistry()?.accounts.orEmpty().isEmpty())
    }

    @Test
    fun aRefusedTokenExchangeEndsInAnErrorAndLeavesNothingBehind() = runBlocking {
        tokenAnswer = 400 to """{"error":"invalid_grant"}"""

        signInAndReturn()

        assertEquals(ConnectAccountPhase.ERROR, flow.uiState.value.phase)
        assertEquals(ConnectAccountFailureKind.TOKEN_EXCHANGE, flow.uiState.value.failureKind)
        assertNothingLeftBehind()
    }

    @Test
    fun aFailedIdentityReadDeletesTheNewToken() = runBlocking {
        identity = AuthIdentityResult.Failure(AuthIdentityFailureKind.TRANSPORT, retryable = true)

        signInAndReturn()

        assertEquals(ConnectAccountFailureKind.IDENTITY, flow.uiState.value.failureKind)
        assertTrue(flow.uiState.value.retryable)
        assertNothingLeftBehind()
    }

    @Test
    fun aNameAlreadyInUseIsAnAccountConflict() = runBlocking {
        identity = AuthIdentityResult.Success(AuthAccountIdentity("t2local", "subshell"))

        signInAndReturn()

        assertEquals(ConnectAccountFailureKind.ACCOUNT_CONFLICT, flow.uiState.value.failureKind)
        assertNothingLeftBehind()
    }

    @Test
    fun theSameCallbackAgainAfterConnectingChangesNothing() = runBlocking {
        signInAndReturn()
        assertEquals(ConnectAccountPhase.CONNECTED, flow.uiState.value.phase)

        flow.handleCallbackResultToken("accepted")

        assertEquals(ConnectAccountPhase.CONNECTED, flow.uiState.value.phase)
        assertEquals(1, tokenRequests)
        assertEquals(1, store.accountRegistry()?.accounts?.size)
    }

    @Test
    fun aRetryAfterAFailureConnectsWithOnlyTheAccountsName() = runBlocking {
        tokenAnswer = 503 to """{"error":"temporarily_unavailable"}"""
        signInAndReturn()
        assertTrue(flow.uiState.value.retryable)

        tokenAnswer = 200 to
            """{"access_token":"access-1","token_type":"bearer","expires_in":3600,"scope":"$scopes","refresh_token":"refresh-1"}"""
        signInAndReturn()

        val state = flow.uiState.value
        assertEquals(ConnectAccountPhase.CONNECTED, state.phase)
        assertEquals("me", state.username)
        assertNull(state.failureKind)
        assertEquals(listOf("me"), store.accountRegistry()?.accounts?.map { it.username })
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
