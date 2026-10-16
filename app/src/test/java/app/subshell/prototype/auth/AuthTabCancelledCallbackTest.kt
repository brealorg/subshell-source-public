package app.subshell.prototype.auth

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.browser.auth.AuthTabIntent
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.ConnectAccountPhase
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

/**
 * AUTHCALLBACK06: closing the auth tab without finishing authorization
 * reaches whoever is waiting for the callback as the cancelled token. An
 * owned capability authorization cancels its pending request instead of
 * staying AWAITING_CALLBACK; an owner-less one (login) goes to the connect
 * flow, which cancels too. A result with a callback URI is processed as
 * before; any other result without one dispatches nothing.
 * AUTHCALLBACK09: with nothing on record the cancel reaches the connect flow
 * only while a login is in progress, and the inbox treats it as a cancel,
 * not a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthTabCancelledCallbackTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val connectFlow = RecordingConnectFlow()
    private val inbox = RecordingInboxCapability()

    @Test
    fun aClosedAuthTabDeliversTheCancelledToken() {
        assertEquals(
            listOf(AUTH_CALLBACK_CANCELLED_TOKEN),
            delivered(AuthTabIntent.RESULT_CANCELED, resultUri = null),
        )
    }

    @Test
    fun aCallbackUriIsProcessedWhateverTheResultCode() {
        val callback = Uri.parse("https://subshell.example/callback?state=s&code=c")
        val processed = mutableListOf<Uri>()
        val tokens = mutableListOf<String>()
        dispatchBlocking(
            resultCode = AuthTabIntent.RESULT_OK,
            resultUri = callback,
            processCallback = { uri -> processed += uri; "accepted" },
            deliver = { tokens += it },
        )
        assertEquals(listOf(callback), processed)
        assertEquals(listOf("accepted"), tokens)
    }

    @Test
    fun aTabThatEndsWithoutACallbackForAnotherReasonDeliversARejection() {
        // AUTHCALLBACK11: never leave the owner waiting.
        assertEquals(
            listOf("rejected:AUTH_TAB_VERIFICATION_FAILED"),
            delivered(AuthTabIntent.RESULT_VERIFICATION_FAILED, resultUri = null),
        )
        assertEquals(
            listOf("rejected:AUTH_TAB_VERIFICATION_TIMED_OUT"),
            delivered(AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT, resultUri = null),
        )
        assertEquals(
            listOf("rejected:AUTH_TAB_NO_CALLBACK"),
            delivered(AuthTabIntent.RESULT_UNKNOWN_CODE, resultUri = null),
        )
    }

    @Test
    fun aRejectedTabEndsAnOwnedAuthorizationAsAFailure() {
        store.clearAll()
        store.savePendingAuthorization(pending(ownerKey = "inbox_read"))
        inbox.pending = true
        val viewModel = viewModel()

        dispatchBlocking(
            resultCode = AuthTabIntent.RESULT_VERIFICATION_FAILED,
            resultUri = null,
            processCallback = { error("no callback URI") },
            deliver = viewModel::handleCallbackResultToken,
        )
        idle()

        assertEquals(1, inbox.cancelled)
        assertTrue(connectFlow.handledTokens.isEmpty())
        val inboxState = viewModel.redditInboxUiState.value
        assertTrue(inboxState.authorizationRequired)
        assertTrue(inboxState.failureKind != null)
    }

    @Test
    fun withNothingOnRecordARejectedTabLeavesAnIdleConnectFlowAlone() {
        store.clearAll()
        connectFlow.state.value = ConnectAccountUiState(
            phase = ConnectAccountPhase.CONNECTED,
            providerLabel = "Reddit",
            username = "someone",
        )
        val viewModel = viewModel()

        viewModel.handleCallbackResultToken("rejected:AUTH_TAB_VERIFICATION_TIMED_OUT")
        idle()

        assertTrue(connectFlow.handledTokens.isEmpty())
    }

    @Test
    fun theCancelledTokenCancelsAnOwnedCapabilityAuthorization() {
        store.clearAll()
        store.savePendingAuthorization(pending(ownerKey = "inbox_read"))
        inbox.pending = true
        val viewModel = viewModel()

        dispatchBlocking(
            resultCode = AuthTabIntent.RESULT_CANCELED,
            resultUri = null,
            processCallback = { error("no callback URI") },
            deliver = viewModel::handleCallbackResultToken,
        )
        idle()

        assertEquals(1, inbox.cancelled)
        assertTrue(connectFlow.handledTokens.isEmpty())
        val inboxState = viewModel.redditInboxUiState.value
        assertTrue(inboxState.authorizationRequired)
        assertNull(inboxState.failureKind)
    }

    @Test
    fun withNothingOnRecordTheCancelLeavesAnIdleConnectFlowAlone() {
        store.clearAll()
        connectFlow.state.value = ConnectAccountUiState(
            phase = ConnectAccountPhase.CONNECTED,
            providerLabel = "Reddit",
            username = "someone",
        )
        val viewModel = viewModel()

        viewModel.handleCallbackResultToken(AUTH_CALLBACK_CANCELLED_TOKEN)
        idle()

        assertTrue(connectFlow.handledTokens.isEmpty())
    }

    @Test
    fun withNothingOnRecordTheCancelStillReachesALoginInProgress() {
        store.clearAll()
        connectFlow.state.value = ConnectAccountUiState(
            phase = ConnectAccountPhase.AWAITING_CALLBACK,
            providerLabel = "Reddit",
        )
        val viewModel = viewModel()

        viewModel.handleCallbackResultToken(AUTH_CALLBACK_CANCELLED_TOKEN)
        idle()

        assertEquals(listOf(AUTH_CALLBACK_CANCELLED_TOKEN), connectFlow.handledTokens)
    }

    @Test
    fun theCancelledTokenReachesTheConnectFlowForAnOwnerlessLogin() {
        store.clearAll()
        store.savePendingAuthorization(pending(ownerKey = null))
        val viewModel = viewModel()

        dispatchBlocking(
            resultCode = AuthTabIntent.RESULT_CANCELED,
            resultUri = null,
            processCallback = { error("no callback URI") },
            deliver = viewModel::handleCallbackResultToken,
        )
        idle()

        assertEquals(listOf(AUTH_CALLBACK_CANCELLED_TOKEN), connectFlow.handledTokens)
        assertEquals(0, inbox.cancelled)
    }

    private fun delivered(resultCode: Int, resultUri: Uri?): List<String> {
        val tokens = mutableListOf<String>()
        dispatchBlocking(
            resultCode = resultCode,
            resultUri = resultUri,
            processCallback = { error("no callback URI") },
            deliver = { tokens += it },
        )
        return tokens
    }

    private fun viewModel(): AuthConnectViewModel = AuthConnectViewModel(
        application,
        unavailableAuthApplicationRuntime().copy(
            connectFlow = connectFlow,
            authorizationOwnerResolver = AuthCapabilityAuthorizationOwnerResolver(store),
            inboxReadCapability = inbox,
        ),
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun pending(ownerKey: String?) = PendingOAuthAuthorization(
        provider = OAuthProvider.REDDIT,
        state = "state",
        redirectUri = "subshell://callback",
        createdAtEpochMs = 1L,
        authorizationOwnerKey = ownerKey,
        authorizationContextKey = null,
    )

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private class RecordingConnectFlow : AuthConnectFlow {
        val handledTokens = mutableListOf<String>()
        val state = MutableStateFlow(ConnectAccountUiState.unavailable("Reddit"))
        override val uiState: StateFlow<ConnectAccountUiState> = state
        override suspend fun beginAuthorization(): AuthBrowserLaunchResult = error("not used")
        override suspend fun beginReauthentication(accountStableId: String): AuthBrowserLaunchResult = error("not used")
        override suspend fun browserLaunchFailed() = Unit
        override suspend fun cancel() = Unit
        override suspend fun reset() = Unit
        override fun hasStoredCallback(): Boolean = false
        override suspend fun handleCallbackResultToken(resultToken: String) { handledTokens += resultToken }
        override suspend fun resumeStoredCallbackIfPresent() = Unit
    }

    private class RecordingInboxCapability : AuthInboxReadCapability {
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

    // LOW4: dispatchAuthTabResult suspends (the callback is processed off Main).
    private fun dispatchBlocking(
        resultCode: Int,
        resultUri: Uri?,
        processCallback: suspend (Uri) -> String,
        deliver: (String) -> Unit,
    ) = kotlinx.coroutines.runBlocking {
        dispatchAuthTabResult(resultCode, resultUri, processCallback, deliver)
    }
}
