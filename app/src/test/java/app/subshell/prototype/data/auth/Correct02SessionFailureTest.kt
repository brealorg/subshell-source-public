package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipRequest
import app.subshell.prototype.data.account.OnlineVisibilityConfirmation
import app.subshell.prototype.data.account.OnlineVisibilityIssue
import app.subshell.prototype.data.account.OnlineVisibilityResult
import app.subshell.prototype.data.account.OnlineVisibilityWriteResult
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommunityMembershipResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageMutationResult
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditCommunityMembershipWriter
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditOnlineVisibilityService
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostEditor
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostManager
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.reddit.OnlineVisibilityNetwork
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CORRECT02: a token refresh that failed on the network, or a store read that
 * failed, is a retryable session problem, not "authorize again" (OCB-512,
 * 514, 516); a membership 403 asks to authorize only without the scope
 * (OCB-614).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Correct02SessionFailureTest {
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
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val endpoint = NetworkEndpoint.https("https://oauth.reddit.test/")

    @Test
    fun onlineVisibilityReportsAFailedStoreReadAsAConnectionProblem() = runBlocking {
        val service = ConfiguredRedditOnlineVisibilityService(
            configuration = configuration,
            activeAccountId = { accountId },
            ensureSession = { _, _ -> AuthSessionResult.StorageFailure },
            networkForSession = {
                object : OnlineVisibilityNetwork {
                    override suspend fun read() = OnlineVisibilityResult.Known(true)
                    override suspend fun patch(value: Boolean) = OnlineVisibilityResult.Known(value)
                }
            },
        )

        val read = service.read(accountId) { true }
        val change = service.change(OnlineVisibilityConfirmation(accountId, true, false, 1)) { true }

        assertEquals(OnlineVisibilityIssue.TRANSPORT, (read as OnlineVisibilityResult.Failed).issue)
        assertEquals(OnlineVisibilityIssue.TRANSPORT, (change as OnlineVisibilityWriteResult.Failed).issue)
    }

    @Test
    fun aPostEditWhoseStoreReadFailedIsARetryableSessionFailure() = runBlocking {
        val editor = ConfiguredRedditPostEditor(
            capability = object : AuthCommentWriteCapability {
                override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
                override fun hasPendingAuthorization() = false
                override fun hasStoredCallback() = false
                override fun cancelPendingAuthorization() = Unit
                override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
                override suspend fun ensureAuthorized(accountStableId: String) = AuthSessionResult.StorageFailure
            },
            callFactory = OkHttpClient(),
            endpoint = endpoint,
            userAgent = "subshell-test",
        )

        val result = editor.edit(AuthenticatedPostEditRequest(accountId, "t3_abc", "new text"))

        result as AuthenticatedPostEditResult.Failed
        assertEquals(AuthenticatedPostEditFailureKind.SESSION, result.kind)
        assertTrue(result.retryable)
    }

    @Test
    fun aPostManageChangeWhoseTokenRefreshFailedIsASessionFailure() = runBlocking {
        val manager = ConfiguredRedditPostManager(
            accountCoordinator = signedInWithExpiredToken(),
            callFactory = OkHttpClient(),
            endpoint = endpoint,
            userAgent = "subshell-test",
        )

        val result = manager.mutate(
            PostManageMutationRequest(
                target = PostManageTarget(accountId, "t3_abc", "me", "kotlin"),
                operation = PostManageOperation.SET_NSFW,
                enabled = true,
            ),
        )

        result as AuthenticatedPostManageMutationResult.Failed
        assertEquals(AuthenticatedPostManageFailureKind.SESSION, result.kind)
        assertTrue(result.retryable)
    }

    @Test
    fun aMembership403WithTheScopeIsARefusalNotAnAuthorizationLoop() = runBlocking {
        val withScope = membershipWriter(RedditOAuth.liveCustomFeedWriteScopes).update(join())
        val withoutScope = membershipWriter(RedditOAuth.coreReadScopes).update(join())

        withScope as AuthenticatedCommunityMembershipResult.Failed
        assertEquals(CommunityMembershipFailureKind.HTTP, withScope.kind)
        assertEquals(403, withScope.statusCode)
        assertEquals(AuthenticatedCommunityMembershipResult.AuthorizationRequired, withoutScope)
    }

    private fun join() = CommunityMembershipRequest(accountId, "kotlin", CommunityMembershipAction.JOIN)

    private fun membershipWriter(scopes: Set<String>): ConfiguredRedditCommunityMembershipWriter {
        val session = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet("token", null, "bearer", scopes.joinToString(" "), Long.MAX_VALUE),
            identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
            clientMode = configuration.clientMode,
            clientIdFingerprint = configuration.clientIdFingerprint,
        )
        return ConfiguredRedditCommunityMembershipWriter(
            capability = object : AuthCustomFeedWriteCapability {
                override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
                override suspend fun hasScopeGrant(accountStableId: String) = true
                override suspend fun authorizedSession(accountStableId: String) = session
                override fun hasPendingAuthorization() = false
                override fun hasStoredCallback() = false
                override fun cancelPendingAuthorization() = Unit
                override suspend fun completePendingAuthorization(): AuthCustomFeedWriteCapabilityResult = error("unused")
                override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                    AuthSessionResult.Authenticated(session)
            },
            callFactory = answering(403),
            endpoint = endpoint,
            userAgent = "subshell-test",
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun answering(code: Int) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("answer")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private suspend fun signedInWithExpiredToken(): AuthAccountSessionCoordinator {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
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
                tokens = OAuthTokenSet(
                    "access-old",
                    "refresh-1",
                    "bearer",
                    (RedditOAuth.coreReadScopes + "modposts").joinToString(" "),
                    1L,
                ),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = answering(503),
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
