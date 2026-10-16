package app.subshell.prototype.auth

import app.subshell.prototype.data.account.SharedPreferencesRedditFavoriteCommunityStore
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.DirectChatReaction
import app.subshell.prototype.data.account.DirectChatReactionPresentationCache
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredAuthAccountRecord
import app.subshell.prototype.data.auth.StoredAuthAccountRegistry
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthConnectFlow
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthAccountIdentityClient
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AUTH08A1_LOGOUT_RESETS_CONNECT_FLOW_V1. A completed connect leaves the
 * connect flow in CONNECTED. Logging the account out has to put that state
 * back, or the onboarding screen returns with a phase that belongs to an
 * account nobody is signed in to any more. The local-fixture account is the
 * cheap path through the coordinator: it clears the session without any
 * network call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelLogoutTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val connectFlow = RecordingConnectFlow()

    @Test
    fun aClearedLogoutResetsTheConnectFlow() {
        val viewModel = viewModel()
        idle()
        // LOW4: the flow restores its stored phase once at start.
        val resetsAtStart = connectFlow.resets
        var completed: Boolean? = null

        viewModel.logoutActiveAccount { cleared -> completed = cleared }
        idle()

        assertEquals(true, completed)
        assertEquals(resetsAtStart + 1, connectFlow.resets)
    }

    @Test
    fun withoutAnAccountSessionCoordinatorNothingIsResetAndLogoutReportsFailure() {
        val viewModel = AuthConnectViewModel(
            application,
            unavailableAuthApplicationRuntime().copy(connectFlow = connectFlow),
        )
        idle()
        val resetsAtStart = connectFlow.resets
        var completed: Boolean? = null

        viewModel.logoutActiveAccount { cleared -> completed = cleared }
        idle()

        assertEquals(false, completed)
        assertEquals(resetsAtStart, connectFlow.resets)
    }

    // ACCOUNT01_REACTION_CACHE_ACCOUNT_SCOPED_V1 --------------------------------

    private val chatPreferences = application.getSharedPreferences("account01-logout", android.content.Context.MODE_PRIVATE)
    private val chatCache = DirectChatReactionPresentationCache(
        preferences = chatPreferences,
        json = SubshellNetworkJson.providerJson,
    )

    private fun seedChatCache(accountStableId: String) {
        chatCache.save(
            accountStableId = accountStableId,
            inboxItemId = "inbox-1",
            participantUsername = "alice",
            messages = listOf(MessageBubble(id = "\$m", sender = "alice", age = "1m", body = "hello", outgoing = false, createdEpochSeconds = 100L)),
            reactions = listOf(DirectChatReaction("\$r", "\$m", "g6akcitga7081.gif", senderIsSelf = false)),
        )
    }

    private fun chatCacheOf(accountStableId: String) = chatCache.load(accountStableId, "inbox-1", "alice")

    @Test
    fun loggingOutARealAccountRemovesItsChatReactionCacheOnly() {
        chatPreferences.edit().clear().commit()
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        val account = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "me")
        store.saveSession(
            account,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("token", "refresh", "bearer", "read identity", Long.MAX_VALUE),
                identity = AuthAccountIdentity("me", "user-me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = account,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "me",
                        username = "user-me",
                        createdAtEpochMs = 1,
                        lastUsedAtEpochMs = 1,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = account,
            ),
        )
        seedChatCache(account)
        seedChatCache("reddit:someone-else")
        // REDDITFAV03_LOGOUT_FORGETS_REDDIT_FAVORITES_V1
        val favorites = SharedPreferencesRedditFavoriteCommunityStore(application)
        favorites.replace(account, setOf("norge"))
        favorites.replace("reddit:someone-else", setOf("subshell"))
        val viewModel = viewModel(store, chatCache, offline = OfflineIoCallFactory)
        var completed: Boolean? = null

        viewModel.logoutActiveAccount { cleared -> completed = cleared }
        // The revocation runs off the main looper; wait for the callback.
        val deadline = System.currentTimeMillis() + 5_000
        while (completed == null && System.currentTimeMillis() < deadline) {
            idle()
            Thread.sleep(10)
        }

        assertEquals(true, completed)
        assertNull(chatCacheOf(account))
        assertNotNull(chatCacheOf("reddit:someone-else"))
        assertNull(favorites.read(account))
        assertEquals(setOf("subshell"), favorites.read("reddit:someone-else"))
    }

    // STEP3 (auth08a1): logging out deletes the active account's session and
    // registry entry only; another signed-in account keeps both.
    @Test
    fun loggingOutTheActiveAccountDeletesOnlyItsSession() {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        val me = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "me")
        val other = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "other")
        fun session(id: String, name: String) = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet("token-$id", "refresh-$id", "bearer", "read identity", Long.MAX_VALUE),
            identity = AuthAccountIdentity(id, name),
            clientMode = configuration.clientMode,
            clientIdFingerprint = configuration.clientIdFingerprint,
        )
        fun record(account: String, id: String, name: String) = StoredAuthAccountRecord(
            accountId = account,
            provider = OAuthProvider.REDDIT,
            providerAccountId = id,
            username = name,
            createdAtEpochMs = 1,
            lastUsedAtEpochMs = 1,
            clientMode = configuration.clientMode,
        )
        store.saveSession(me, session("me", "user-me"))
        store.saveSession(other, session("other", "user-other"))
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(me, "me", "user-me"), record(other, "other", "user-other")),
                activeAccountId = me,
            ),
        )
        val viewModel = viewModel(store, chatCache, offline = OfflineIoCallFactory)
        var completed: Boolean? = null

        viewModel.logoutActiveAccount { cleared -> completed = cleared }
        val deadline = System.currentTimeMillis() + 5_000
        while (completed == null && System.currentTimeMillis() < deadline) {
            idle()
            Thread.sleep(10)
        }

        assertEquals(true, completed)
        assertNull(store.session(me))
        assertNotNull(store.session(other))
        assertEquals(listOf(other), store.accountRegistry()?.accounts?.map { it.accountId })
    }

    @Test
    fun anAccountThatRemainsAfterLogoutKeepsItsChatReactionCache() {
        chatPreferences.edit().clear().commit()
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        val viewModel = viewModel(store, chatCache, offline = OfflineCallFactory)
        val local = "local:subshell"
        seedChatCache(local)
        var completed: Boolean? = null

        viewModel.logoutActiveAccount { cleared -> completed = cleared }
        idle()

        assertEquals(true, completed)
        assertNotNull(chatCacheOf(local))
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

    private fun viewModel(
        store: EncryptedAuthSessionStore,
        cache: DirectChatReactionPresentationCache,
        offline: Call.Factory,
    ): AuthConnectViewModel {
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = offline,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        return AuthConnectViewModel(
            application,
            unavailableAuthApplicationRuntime().copy(
                accountSessionDirectory = coordinator,
                connectFlow = connectFlow,
                directChatReactionPresentationCache = cache,
            ),
        )
    }

    /** Revocation fails like it does offline; the local logout still completes. */
    private val OfflineIoCallFactory: Call.Factory = okhttp3.OkHttpClient.Builder()
        .addInterceptor { throw java.io.IOException("offline") }
        .build()

    private fun viewModel(): AuthConnectViewModel {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OfflineCallFactory,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            // The logout path hops to the coordinator's storage dispatcher; keep it
            // on the caller so the Robolectric looper alone drives the test.
            storageDispatcher = Dispatchers.Unconfined,
        )
        assertTrue(coordinator.snapshot().accounts.isNotEmpty())
        return AuthConnectViewModel(
            application,
            unavailableAuthApplicationRuntime().copy(
                accountSessionDirectory = coordinator,
                connectFlow = connectFlow,
            ),
        )
    }

    private fun idle() {
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
    }

    private object OfflineCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = error("no network in this test")
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private class RecordingConnectFlow : AuthConnectFlow {
        var resets = 0
        override val uiState: StateFlow<ConnectAccountUiState> =
            MutableStateFlow(
                ConnectAccountUiState(
                    phase = ConnectAccountPhase.CONNECTED,
                    providerLabel = "Reddit",
                    username = "someone",
                ),
            )
        override suspend fun beginAuthorization(): AuthBrowserLaunchResult = error("not used")
        override suspend fun beginReauthentication(accountStableId: String): AuthBrowserLaunchResult = error("not used")
        override suspend fun browserLaunchFailed() = Unit
        override suspend fun cancel() = Unit
        override suspend fun reset() { resets += 1 }
        override fun hasStoredCallback(): Boolean = false
        override suspend fun handleCallbackResultToken(resultToken: String) = Unit
        override suspend fun resumeStoredCallbackIfPresent() = Unit
    }
}
