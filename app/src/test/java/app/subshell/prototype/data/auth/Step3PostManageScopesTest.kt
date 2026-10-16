package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageMutationResult
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostManager
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.NetworkEndpoint
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
 * STEP3 (write02f): each post-management change asks only for its own Reddit
 * permission — flair needs `flair`, NSFW and spoiler need `modposts`, reply
 * notifications need `edit` — and a grant without it sends nothing and asks
 * to authorize instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3PostManageScopesTest {
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
    private val requests = mutableListOf<String>()

    private suspend fun managerGranted(vararg extraScopes: String): ConfiguredRedditPostManager {
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
                    "access-current",
                    "refresh-1",
                    "bearer",
                    (RedditOAuth.coreReadScopes + extraScopes).joinToString(" "),
                    Long.MAX_VALUE,
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
                callFactory = recording(),
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        return ConfiguredRedditPostManager(
            accountCoordinator = coordinator,
            callFactory = recording(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
        )
    }

    private fun recording() = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request().url.encodedPath
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private val target = PostManageTarget(accountId, "t3_abc", "me", "kotlin")

    private fun change(operation: PostManageOperation) = when (operation) {
        PostManageOperation.SET_FLAIR -> PostManageMutationRequest(target, operation, flairTemplateId = "tpl-1", flairText = "Help")
        else -> PostManageMutationRequest(target, operation, enabled = true)
    }

    private fun assertAsksToAuthorize(result: AuthenticatedPostManageMutationResult) {
        result as AuthenticatedPostManageMutationResult.Failed
        assertEquals(AuthenticatedPostManageFailureKind.AUTHORIZATION_REQUIRED, result.kind)
    }

    @Test
    fun aFlairOnlyGrantChangesFlairButNotNsfwSpoilerOrReplyNotifications() = runBlocking {
        val manager = managerGranted("flair")

        assertAsksToAuthorize(manager.mutate(change(PostManageOperation.SET_NSFW)))
        assertAsksToAuthorize(manager.mutate(change(PostManageOperation.SET_SPOILER)))
        assertAsksToAuthorize(manager.mutate(change(PostManageOperation.SET_SEND_REPLIES)))
        assertEquals(emptyList<String>(), requests)

        manager.mutate(change(PostManageOperation.SET_FLAIR))
        assertTrue(requests.toString(), requests.any { it.endsWith("/selectflair") })
    }

    @Test
    fun modpostsAllowsNsfwAndSpoilerAndEditAllowsReplyNotifications() = runBlocking {
        val manager = managerGranted("modposts", "edit")

        manager.mutate(change(PostManageOperation.SET_NSFW))
        manager.mutate(change(PostManageOperation.SET_SPOILER))
        manager.mutate(change(PostManageOperation.SET_SEND_REPLIES))

        assertTrue(requests.toString(), requests.any { it.endsWith("/marknsfw") })
        assertTrue(requests.toString(), requests.any { it.endsWith("/spoiler") })
        assertTrue(requests.toString(), requests.any { it.endsWith("/sendreplies") })
        assertAsksToAuthorize(manager.mutate(change(PostManageOperation.SET_FLAIR)))
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
