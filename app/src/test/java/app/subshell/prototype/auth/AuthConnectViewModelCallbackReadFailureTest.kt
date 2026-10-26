package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.ConnectAccountUiState
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AUTHCALLBACK04: an owned callback never reaches the connect flow, even
 * when one read of the encrypted store fails while it is being routed.
 * The routing decision is one snapshot of the pending and stored callback
 * records: an owned snapshot goes to its owner, an unreadable or invalid
 * one reaches nobody, and only an owner-less or empty one goes to the
 * connect flow. The cipher fails on exactly the Nth decrypt, for every N
 * that the routing can reach; past the last read the callback must reach
 * its owner (the inbox capability here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelCallbackReadFailureTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aFailedStoreReadNeverRoutesAnOwnedCallbackToTheConnectFlow() {
        for (failingDecrypt in 1..MAX_DECRYPTS) {
            val outcome = route(failingDecrypt) { viewModel ->
                viewModel.handleCallbackResultToken("token-$failingDecrypt")
            }
            assertTrue(
                "decrypt #$failingDecrypt failed: handle reached the connect flow",
                outcome.connectFlowCalls == 0,
            )
        }
    }

    @Test
    fun aFailedStoreReadNeverRoutesAStoredOwnedCallbackToTheConnectFlow() {
        for (failingDecrypt in 1..MAX_DECRYPTS) {
            val outcome = route(failingDecrypt) { viewModel ->
                viewModel.resumeStoredCallbackIfPresent()
            }
            assertTrue(
                "decrypt #$failingDecrypt failed: resume reached the connect flow",
                outcome.connectFlowCalls == 0,
            )
        }
    }

    @Test
    fun withEveryReadSucceedingTheOwnedCallbackReachesItsOwner() {
        val handled = route(failingDecrypt = null) { viewModel ->
            viewModel.handleCallbackResultToken("token")
        }
        assertEquals(0, handled.connectFlowCalls)
        assertEquals(1, handled.inboxCalls)

        val resumed = route(failingDecrypt = null) { viewModel ->
            viewModel.resumeStoredCallbackIfPresent()
        }
        assertEquals(0, resumed.connectFlowCalls)
        assertEquals(1, resumed.inboxCalls)
    }

    private class Outcome(val connectFlowCalls: Int, val inboxCalls: Int)

    private fun route(
        failingDecrypt: Int?,
        entryPoint: (AuthConnectViewModel) -> Unit,
    ): Outcome {
        val cipher = FailingOnNthDecryptCipher()
        val store = EncryptedAuthSessionStore(application, cipher = cipher)
        store.clearAll()
        store.savePendingAuthorization(pending())
        store.saveCallback(callback())
        cipher.failOn = failingDecrypt
        val connectFlow = RecordingConnectFlow()
        val inbox = RecordingInboxCapability()
        val viewModel = AuthConnectViewModel(
            application,
            unavailableAuthApplicationRuntime().copy(
                connectFlow = connectFlow,
                authorizationOwnerResolver = AuthCapabilityAuthorizationOwnerResolver(store),
                inboxReadCapability = inbox,
            ),
        ).withAreaViewModels() // AUTHSPLIT02/03: made with it, as MainActivity does
        entryPoint(viewModel)
        shadowOf(Looper.getMainLooper()).idle()
        return Outcome(
            connectFlowCalls = connectFlow.handledTokens.size + connectFlow.resumes,
            inboxCalls = inbox.calls,
        )
    }

    private fun pending() = PendingOAuthAuthorization(
        provider = OAuthProvider.REDDIT,
        state = "state",
        redirectUri = "subshell://callback",
        createdAtEpochMs = 1L,
        authorizationOwnerKey = "inbox_read",
        authorizationContextKey = null,
    )

    private fun callback() = StoredOAuthCallback(
        provider = OAuthProvider.REDDIT,
        state = "state",
        code = "code",
        error = null,
        receivedAtEpochMs = 2L,
        authorizationOwnerKey = "inbox_read",
        authorizationContextKey = null,
    )

    private class FailingOnNthDecryptCipher : AuthCipher {
        var failOn: Int? = null
        private var decrypts = 0
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray {
            decrypts += 1
            if (decrypts == failOn) error("transient decrypt failure #$decrypts")
            return envelope
        }
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

    /** Counts the routed callback: both routes ask for the stored callback first. */
    private class RecordingInboxCapability : AuthInboxReadCapability {
        var calls = 0
        override suspend fun beginAuthorization(accountStableId: String): String = error("not used")
        override suspend fun hasScopeGrant(accountStableId: String): Boolean = false
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean { calls += 1; return false }
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult = error("not used")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }

    private companion object {
        // Pending + callback on record: the old routing decrypted up to ten
        // times per entry point; the snapshot decrypts twice.
        const val MAX_DECRYPTS = 12
    }
}
