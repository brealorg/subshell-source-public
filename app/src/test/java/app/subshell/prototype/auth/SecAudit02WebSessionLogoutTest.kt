package app.subshell.prototype.auth

import android.app.Application
import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountRegistrationResult
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthIdentityResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthAccountIdentityClient
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.auth.RedditWebSessionOwner
import app.subshell.prototype.data.auth.RedditWebSessionOwnership
import app.subshell.prototype.data.auth.StoredOAuthCallback
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.redditWebSessionSurvivesRemoval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECAUDIT02 (security audit #2): Reddit's web login from the RedReader sign-in
 * window survived logout and account removal; only a new sign-in cleared it. It
 * now belongs to the account whose sign-in created it and goes when that
 * account is logged out. A web login known to belong to another signed-in
 * account is kept, so Direct Chat keeps working for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit02WebSessionLogoutTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val cookies = CookieManager.getInstance()

    @Before
    fun clearCookies() {
        cookies.removeAllCookies(null)
        application.getSharedPreferences("subshell_reddit_web_session", 0).edit().clear().commit()
    }

    @Test
    fun onlyAWebLoginKnownToBelongToAnotherAccountSurvivesALogout() {
        val owner = RedditWebSessionOwner(accountStableId = "a", sessionFingerprint = "f")

        assertTrue(redditWebSessionSurvivesRemoval(owner, currentFingerprint = "f", removedAccountStableId = "b"))
        assertFalse(redditWebSessionSurvivesRemoval(owner, currentFingerprint = "f", removedAccountStableId = "a"))
        assertFalse(redditWebSessionSurvivesRemoval(owner, currentFingerprint = "g", removedAccountStableId = "b"))
        assertFalse(redditWebSessionSurvivesRemoval(null, currentFingerprint = "f", removedAccountStableId = "b"))
        assertFalse(redditWebSessionSurvivesRemoval(owner, currentFingerprint = null, removedAccountStableId = "b"))
    }

    @Test
    fun loggingOutTheOwnerDeletesTheWebLogin() = runBlocking {
        val webSession = WebViewRedditSessionOwnership(application, recordsOwner = true)
        cookies.setCookie(REDDIT, "reddit_session=account-a")
        webSession.signedInAs("a")

        webSession.accountRemoved("b")
        assertEquals("reddit_session=account-a", cookies.getCookie(REDDIT))

        webSession.accountRemoved("a")
        assertNull(cookies.getCookie(REDDIT))
    }

    @Test
    fun aWebLoginWithoutAKnownOwnerIsDeletedWithAnyAccount() = runBlocking {
        // Installs from before this change, a native Client ID (whose sign-in
        // never touches the WebView), or a web login replaced after the sign-in.
        cookies.setCookie(REDDIT, "reddit_session=account-a")
        WebViewRedditSessionOwnership(application, recordsOwner = false).run {
            signedInAs("a")
            accountRemoved("b")
        }
        assertNull(cookies.getCookie(REDDIT))

        val recording = WebViewRedditSessionOwnership(application, recordsOwner = true)
        cookies.setCookie(REDDIT, "reddit_session=account-a")
        recording.signedInAs("a")
        cookies.setCookie(REDDIT, "reddit_session=someone-else")
        recording.accountRemoved("b")
        assertNull(cookies.getCookie(REDDIT))
    }

    @Test
    fun theCoordinatorRecordsTheSignInAndReleasesItOnLogout() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(
            object : RedditWebSessionOwnership {
                override suspend fun signedInAs(accountStableId: String) {
                    calls += "signedIn:$accountStableId"
                }

                override suspend fun accountRemoved(accountStableId: String) {
                    calls += "removed:$accountStableId"
                }
            },
        )

        assertTrue(coordinator.completePendingAccount() is AuthAccountRegistrationResult.Registered)
        val logout = coordinator.logoutActive()

        assertTrue(logout.localSessionCleared)
        assertEquals(listOf("signedIn:$accountId", "removed:$accountId"), calls)
    }

    @Test
    fun aFailingWebSessionStoreNeverFailsASignInOrALogout() = runBlocking {
        val coordinator = coordinator(
            object : RedditWebSessionOwnership {
                override suspend fun signedInAs(accountStableId: String) = error("store")

                override suspend fun accountRemoved(accountStableId: String) = error("store")
            },
        )

        assertTrue(coordinator.completePendingAccount() is AuthAccountRegistrationResult.Registered)
        assertTrue(coordinator.logoutActive().removedAccountStableId == accountId)
    }

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
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")

    private suspend fun coordinator(webSession: RedditWebSessionOwnership): AuthAccountSessionCoordinator {
        val store = EncryptedAuthSessionStore(application, cipher = PlainCipher)
        store.clearAll()
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
        val endpoint = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(
                    """{"access_token":"access","token_type":"bearer","expires_in":3600,"scope":"read identity","refresh_token":"refresh"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        return AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = endpoint, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient {
                AuthIdentityResult.Success(AuthAccountIdentity(providerAccountId = "t2me", username = "me"))
            },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
            webSession = webSession,
        ).also { it.hydratedSnapshot() }
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private companion object {
        const val REDDIT = "https://www.reddit.com/"
    }
}
