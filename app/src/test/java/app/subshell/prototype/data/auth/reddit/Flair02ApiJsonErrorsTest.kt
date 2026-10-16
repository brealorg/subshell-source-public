package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.UserFlairFailureKind
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairMutationResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthAccountIdentityClient
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditApiTypeJsonError
import app.subshell.prototype.data.network.reddit.RedditPostManageMutationNetworkResult
import app.subshell.prototype.data.network.reddit.RedditPostManageNetworkDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
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
 * FLAIR02 (#140, #157): selectflair is sent with api_type=json, so Reddit
 * reports a refused flair as HTTP 200 with json.errors. Both flair writers
 * read that body; only an empty error list is a success.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Flair02ApiJsonErrorsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val endpoint = NetworkEndpoint.https("https://oauth.reddit.test/")
    private val accountA = "reddit:native_production:a"
    private val refusal =
        """{"json":{"errors":[["BAD_FLAIR_TARGET","that flair is not available","flair_template_id"]]}}"""
    private val accepted = """{"json":{"errors":[]}}"""

    @Test
    fun postFlairRefusedInA200BodyIsRejectedAndAnEmptyErrorListIsUpdated() = runBlocking {
        val refused = postManageSource(refusal).mutate(flairRequest())
        assertEquals(
            listOf(RedditApiTypeJsonError("BAD_FLAIR_TARGET", "that flair is not available")),
            (refused as RedditPostManageMutationNetworkResult.Rejected).errors,
        )
        assertTrue(
            postManageSource(accepted).mutate(flairRequest())
                is RedditPostManageMutationNetworkResult.Updated,
        )
        // Endpoints without api_type=json still count the status only.
        assertTrue(
            postManageSource("{}").mutate(
                PostManageMutationRequest(target(), PostManageOperation.SET_NSFW, enabled = true),
            ) is RedditPostManageMutationNetworkResult.Updated,
        )
    }

    @Test
    fun userFlairRefusedInA200BodyFailsAndAnEmptyErrorListIsAcknowledged() = runBlocking {
        assertEquals(
            AuthenticatedRedditUserFlairMutationResult.Failed(UserFlairFailureKind.HTTP),
            userFlairManager(refusal).mutate(UserFlairMutationRequest(accountA, "subshell", "template-1")),
        )
        assertEquals(
            AuthenticatedRedditUserFlairMutationResult.Acknowledged,
            userFlairManager(accepted).mutate(UserFlairMutationRequest(accountA, "subshell", "template-1")),
        )
    }

    private fun target() = PostManageTarget(
        accountStableId = accountA,
        postFullname = "t3_abc",
        postAuthor = "user-a",
        community = "subshell",
    )

    private fun flairRequest() = PostManageMutationRequest(
        target = target(),
        operation = PostManageOperation.SET_FLAIR,
        flairTemplateId = "template-1",
        flairText = "",
    )

    private fun postManageSource(body: String) = RedditPostManageNetworkDataSource(
        SubshellHttpClient(
            callFactory = answering(body),
            endpoint = endpoint,
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test",
        ),
    )

    private fun userFlairManager(body: String) = RedditUserFlairManager(
        capability = AuthorizedCapability(),
        readCallFactory = NoNetwork,
        writeCallFactory = answering(body),
        endpoint = endpoint,
        userAgent = "subshell-test",
        accountCoordinator = coordinator(),
        activeAccountStableId = { accountA },
    )

    private fun answering(body: String): Call.Factory = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun coordinator(): AuthAccountSessionCoordinator {
        val configuration = OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
            tokenEndpoint = "https://example.test/token".toHttpUrl(),
            revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
            clientId = "client-id",
            redirectUri = "subshell-auth-test://oauth/callback",
            scopes = setOf("read", "identity"),
            permanentAccess = true,
        )
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        return AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = NoNetwork,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
    }

    private object NoNetwork : Call.Factory {
        override fun newCall(request: okhttp3.Request): Call = error("no network in this test")
    }

    private class AuthorizedCapability : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("token-a", null, "bearer", "identity flair", Long.MAX_VALUE),
                    identity = AuthAccountIdentity("a", "user-a"),
                ),
            )
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
