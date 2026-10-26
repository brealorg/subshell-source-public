package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.account.RedditInboxUiFailureKind
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwnerResolver
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthConnectFlow
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.PendingOAuthAuthorization
import app.subshell.prototype.data.auth.StoredOAuthCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Behaviour tests for the OAuth callback routing that
 * the retired check-reddit01c4-contract.sh counted as AuthConnectViewModel text: with no
 * authorization on record the callback falls through to the connect
 * flow; an owner-less authorization (login, reauthentication) goes to the
 * connect flow even when the inbox capability reports a pending one
 * (AUTHCALLBACK02 retired the predecessor chain, AUTHCALLBACK03 the
 * predecessor capabilities); an invalid routing
 * (mixed owner-less and owned records) blocks
 * every fallback; and the stored-callback resume takes the same route.
 * The real encrypted store runs under Robolectric with an identity cipher,
 * so the routing kinds come from the real resolver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelCallbackRoutingTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val connectFlow = RecordingConnectFlow()
    private val inbox = FakeInboxCapability()

    @Test
    fun withNothingOnRecordTheCallbackFallsThroughToTheConnectFlow() {
        store.clearAll()
        val viewModel = viewModel()

        viewModel.handleCallbackResultToken("token-1")
        idle()
        assertEquals(listOf("token-1"), connectFlow.handledTokens)
        assertEquals(0, inbox.cancelled)

        viewModel.resumeStoredCallbackIfPresent()
        idle()
        assertEquals(1, connectFlow.resumes)
    }

    @Test
    fun anOwnerlessAuthorizationGoesToTheConnectFlowNotToAPredecessorCapability() {
        store.clearAll()
        store.savePendingAuthorization(pending(ownerKey = null, contextKey = null))
        inbox.pending = true
        val viewModel = viewModel()

        viewModel.handleCallbackResultToken("token-2")
        idle()
        assertEquals(listOf("token-2"), connectFlow.handledTokens)
        assertEquals(0, inbox.cancelled)
        assertFalse(viewModel.inbox().redditInboxUiState.value.authorizationRequired)

        viewModel.resumeStoredCallbackIfPresent()
        idle()
        assertEquals(1, connectFlow.resumes)
        assertEquals(0, inbox.cancelled)
    }

    @Test
    fun anInvalidRoutingBlocksEveryFallbackOnBothEntryPoints() {
        store.clearAll()
        store.savePendingAuthorization(pending(ownerKey = null, contextKey = null))
        store.saveCallback(callback(ownerKey = "inbox_read", contextKey = null))
        inbox.pending = true
        val viewModel = viewModel()

        viewModel.handleCallbackResultToken("token-4")
        idle()
        viewModel.resumeStoredCallbackIfPresent()
        idle()
        assertTrue(connectFlow.handledTokens.isEmpty())
        assertEquals(0, connectFlow.resumes)
        assertEquals(0, inbox.cancelled)
    }

    private fun viewModel(): AuthConnectViewModel = AuthConnectViewModel(
        application,
        unavailableAuthApplicationRuntime().copy(
            connectFlow = connectFlow,
            authorizationOwnerResolver = AuthCapabilityAuthorizationOwnerResolver(store),
            inboxReadCapability = inbox,
        ),
    ).withAreaViewModels() // AUTHSPLIT02/03: made with it, as MainActivity does

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun pending(ownerKey: String?, contextKey: String?) = PendingOAuthAuthorization(
        provider = OAuthProvider.REDDIT,
        state = "state",
        redirectUri = "subshell://callback",
        createdAtEpochMs = 1L,
        authorizationOwnerKey = ownerKey,
        authorizationContextKey = contextKey,
    )

    private fun callback(ownerKey: String?, contextKey: String?) = StoredOAuthCallback(
        provider = OAuthProvider.REDDIT,
        state = "state",
        code = "code",
        error = null,
        receivedAtEpochMs = 2L,
        authorizationOwnerKey = ownerKey,
        authorizationContextKey = contextKey,
    )

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private class RecordingConnectFlow : AuthConnectFlow {
        val handledTokens = mutableListOf<String>()
        var resumes = 0
        override val uiState: StateFlow<ConnectAccountUiState> =
            MutableStateFlow(ConnectAccountUiState.unavailable("Reddit"))
        override suspend fun beginAuthorization(): AuthBrowserLaunchResult = error("not used")
        override suspend fun beginReauthentication(accountStableId: String): AuthBrowserLaunchResult = error("not used")
        override suspend fun browserLaunchFailed() = Unit
        override suspend fun cancel() = Unit
        override suspend fun reset() = Unit
        override fun hasStoredCallback(): Boolean = false
        override suspend fun handleCallbackResultToken(resultToken: String) { handledTokens += resultToken }
        override suspend fun resumeStoredCallbackIfPresent() { resumes += 1 }
    }

    private class FakeInboxCapability : AuthInboxReadCapability {
        var pending = false
        var cancelled = 0
        override suspend fun beginAuthorization(accountStableId: String): String = error("not used")
        override suspend fun hasScopeGrant(accountStableId: String): Boolean = false
        override fun hasPendingAuthorization(): Boolean = pending
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() { cancelled += 1; pending = false }
        override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult = error("not used")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }
}
