package io.github.brealorg.subshell.data.auth.reddit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.UserFlairFailureKind
import io.github.brealorg.subshell.data.account.UserFlairMutationRequest
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapabilityResult
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditUserFlairMutationResult
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import io.github.brealorg.subshell.data.auth.OAuthAccountIdentityClient
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.OAuthTokenEndpointClient
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import java.util.concurrent.atomic.AtomicInteger
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
 * DATALAYER12: an account switch while the flair write is being authorized
 * (a token refresh suspends) stops the write before anything is sent, as the
 * moderator writers do after authorization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataLayer12FlairOwnerRecheckTest {
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
    private val accountA = "reddit:native_production:a"

    private val requests = AtomicInteger(0)
    private val countingCallFactory = Call.Factory { _: Request ->
        requests.incrementAndGet()
        error("no request may be sent after an account switch")
    }

    @Test
    fun aSwitchDuringAuthorizationStopsTheFlairWriteBeforeDispatch() = runBlocking {
        var active: String? = accountA
        val manager = RedditUserFlairManager(
            capability = SwitchingCapability(onAuthorize = { active = "reddit:native_production:b" }),
            readCallFactory = countingCallFactory,
            writeCallFactory = countingCallFactory,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            accountCoordinator = coordinator(),
            activeAccountStableId = { active },
        )

        val result = manager.mutate(UserFlairMutationRequest(accountA, "subshell", "template-1"))

        assertEquals(
            AuthenticatedRedditUserFlairMutationResult.Failed(UserFlairFailureKind.ACCOUNT_CHANGED),
            result,
        )
        assertEquals(0, requests.get())
    }

    private fun coordinator(): AuthAccountSessionCoordinator {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        return AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = countingCallFactory,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
    }

    private class SwitchingCapability(private val onAuthorize: () -> Unit) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            onAuthorize()
            return AuthSessionResult.Authenticated(
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("token-a", null, "bearer", "identity flair", Long.MAX_VALUE),
                    identity = AuthAccountIdentity("a", "user-a"),
                ),
            )
        }
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
