package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.NsfwPreferencesResult
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.auth.authenticatedAccountId
import io.github.brealorg.subshell.data.network.reddit.NsfwPreferencesNetwork
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/**
 * STRICTMODE03_NSFW_PREFERENCE_READS_OFF_THE_MAIN_THREAD_V1: the Pixel 6 log
 * showed StrictMode disk reads on the main thread from the NSFW account
 * preference notice: the session lookup decrypts the stored sign-in. The
 * service now looks the session up on its IO dispatcher whoever calls it.
 */
class StrictMode03NsfwPreferenceReadsOffMainTest {
    private val config = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "test-client", redirectUri = "test://callback",
        scopes = setOf("identity"), permanentAccess = true,
    )
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, config.clientMode, "me")
    private val stored = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = config.clientMode, clientIdFingerprint = config.clientIdFingerprint,
    )

    @Test
    fun theSessionIsNeverLookedUpOnTheCallersThread() = runBlocking {
        val main = Executors.newSingleThreadExecutor { task -> Thread(task, "fake-main") }
        val lookups = mutableListOf<String>()
        val service = ConfiguredRedditCommunityAccessService(
            configuration = config,
            activeAccountId = { account },
            ensureSession = { _, _ ->
                lookups += Thread.currentThread().name
                AuthSessionResult.Authenticated(stored)
            },
            storedSessionGrants = { _, _ -> lookups += Thread.currentThread().name; true },
            networkForSession = {
                object : NsfwPreferencesNetwork {
                    override suspend fun read(): NsfwPreferencesResult = NsfwPreferencesResult.Failed
                }
            },
        )
        try {
            withTimeout(5_000) {
                withContext(main.asCoroutineDispatcher()) { service.read(account) { true } }
            }
        } finally {
            main.shutdown()
        }

        assertEquals(2, lookups.size)
        assertTrue("$lookups", lookups.none { it.startsWith("fake-main") })
    }
}
