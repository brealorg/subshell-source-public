package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.CLIENT_ACCESS_REFUSED_PROVIDER_CODE
import app.subshell.prototype.data.account.RedditAccessRefusal
import app.subshell.prototype.data.account.RedditAccessRefusalState
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.REDDIT_READ_ACCESS_REFUSED_REASON
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.error.redditCommunityInfoErrorPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE32_REDDIT_REFUSED_ACCESS_IS_ONE_STATE_V1 (#32): when the token endpoint
 * refuses the app's Client ID (not the account's grant), the sign-in is kept,
 * the app remembers the refusal and asks Reddit again only when a check is
 * allowed; a working token ends it. Every screen's own refresh error stays out
 * of the tray (one notice says it), and an empty screen says why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue32RedditRefusedAccessTest {
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
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val scopes = RedditOAuth.coreReadScopes.joinToString(" ")
    private val workingToken = Triple(
        200,
        "application/json",
        """{"access_token":"access-new","token_type":"bearer","expires_in":3600,"scope":"$scopes","refresh_token":"refresh-1"}""",
    )

    private var tokenAnswer: Triple<Int, String, String> = Triple(500, "text/plain", "")
    private var tokenRequests = 0

    @Before
    fun noRefusal() {
        RedditAccessRefusal.resetForTest()
    }

    @After
    fun clearRefusal() {
        RedditAccessRefusal.resetForTest()
    }

    @Test
    fun aRefusedClientIdKeepsTheSignInAndAsksRedditOnlyWhenACheckIsAllowed() = runBlocking {
        val coordinator = signedIn(expiresAt = 1L)
        tokenAnswer = Triple(401, "application/json", """{"error":"invalid_client"}""")

        val refused = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)

        assertEquals(AuthSessionResult.EndpointFailure(CLIENT_ACCESS_REFUSED_RESULT), refused)
        assertEquals("refresh-1", store.session(accountId)?.tokens?.refreshToken)
        assertEquals(configuration.clientIdFingerprint, RedditAccessRefusal.state.value?.clientFingerprint)
        assertEquals(
            AccountSessionState.CONNECTED,
            coordinator.hydratedSnapshot().accounts.single { it.stableId == accountId }.state,
        )
        assertEquals(1, tokenRequests)

        // No request while refused, even with a working answer waiting.
        tokenAnswer = workingToken
        val stillRefused = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)
        assertEquals(AuthSessionResult.EndpointFailure(CLIENT_ACCESS_REFUSED_RESULT), stillRefused)
        assertEquals(1, tokenRequests)

        // Try again: one request; Reddit lets the app in again.
        RedditAccessRefusal.allowCheck()
        val allowed = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)
        assertEquals("access-new", (allowed as AuthSessionResult.Authenticated).session.tokens.accessToken)
        assertEquals(2, tokenRequests)
        assertNull(RedditAccessRefusal.state.value)
    }

    @Test
    fun aCheckAsksTheTokenEndpointEvenWithAStoredAccessToken() = runBlocking {
        val coordinator = signedIn(expiresAt = Long.MAX_VALUE)
        RedditAccessRefusal.resetForTest(RedditAccessRefusalState(1L, configuration.clientIdFingerprint))
        tokenAnswer = Triple(200, "application/json", """{"error":"unauthorized_client"}""")

        // The first use in a process is the allowed check.
        val checked = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)
        val blocked = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)

        assertEquals(AuthSessionResult.EndpointFailure(CLIENT_ACCESS_REFUSED_RESULT), checked)
        assertEquals(AuthSessionResult.EndpointFailure(CLIENT_ACCESS_REFUSED_RESULT), blocked)
        assertEquals(1, tokenRequests)
        assertNotNull(store.session(accountId))
    }

    @Test
    fun anotherClientIdIsNotRefusedAndInvalidGrantStillEndsTheSession() = runBlocking {
        RedditAccessRefusal.resetForTest(RedditAccessRefusalState(1L, oauthClientIdFingerprint("another-client")))
        val coordinator = signedIn(expiresAt = 1L)
        tokenAnswer = Triple(400, "application/json", """{"error":"invalid_grant"}""")

        val result = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)

        assertEquals(AuthSessionResult.ReauthenticationRequired, result)
        assertNull(store.session(accountId))
        assertEquals(1, tokenRequests)
        assertEquals(oauthClientIdFingerprint("another-client"), RedditAccessRefusal.state.value?.clientFingerprint)
    }

    @Test
    fun aWorkingTokenWithAnotherClientIdEndsTheRefusal() = runBlocking {
        // The reader switched to another Client ID after the old one was refused.
        RedditAccessRefusal.resetForTest(RedditAccessRefusalState(1L, oauthClientIdFingerprint("refused-client")))
        val coordinator = signedIn(expiresAt = 1L)
        tokenAnswer = workingToken

        val result = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)

        assertEquals("access-new", (result as AuthSessionResult.Authenticated).session.tokens.accessToken)
        assertNull(RedditAccessRefusal.state.value)
    }

    @Test
    fun theTrayLeavesOutEachScreensRefusalAndAnEmptyScreenSaysWhy() {
        val refusedDetails = CLIENT_ACCESS_REFUSED_RESULT.toFailureDetails()
        val host = ViewportErrorHostState()
        val degraded = redditCommunityInfoErrorPresentation(
            failureKind = RedditCommunityInfoUiFailureKind.SESSION,
            details = refusedDetails,
            retryable = false,
            showingPartialContent = true,
        )

        host.present(scopeKey = "feed", presentation = degraded, failureDetails = refusedDetails, onRetry = {})
        assertNull(host.currentRequest)

        val other = ErrorPresentation(ErrorPresentationKind.DEGRADED, "Couldn't refresh", "Server problem.")
        host.present(scopeKey = "feed", presentation = other, failureDetails = FailureDetails(httpStatusCode = 500), onRetry = {})
        assertEquals("feed", host.currentRequest?.scopeKey)
        // The refusal replaces the screen's older error instead of leaving it up.
        host.present(scopeKey = "feed", presentation = degraded, failureDetails = refusedDetails, onRetry = {})
        assertNull(host.currentRequest)

        val empty = redditCommunityInfoErrorPresentation(
            failureKind = RedditCommunityInfoUiFailureKind.SESSION,
            details = refusedDetails,
            retryable = true,
            showingPartialContent = false,
        )
        assertEquals(ErrorPresentationKind.BLOCKING, empty.kind)
        assertEquals(REDDIT_READ_ACCESS_REFUSED_REASON, empty.message)
        assertNull(empty.primaryAction)
        // The reason is the message; no provider-code line under it.
        assertNull(empty.technicalDetail)
        assertTrue(empty.failureDetails?.providerCode?.value == CLIENT_ACCESS_REFUSED_PROVIDER_CODE)
    }

    private suspend fun signedIn(expiresAt: Long): AuthAccountSessionCoordinator {
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
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(
            accountId,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access-old", "refresh-1", "bearer", scopes, expiresAt),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val tokenEndpoint = OkHttpClient.Builder().addInterceptor { chain ->
            tokenRequests += 1
            val (code, type, body) = tokenAnswer
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }.build()
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = tokenEndpoint,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        return coordinator
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
