package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwnerResolver
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.PendingOAuthAuthorization
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedContentSafetyWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (safety01a2, safety01a32): the ViewModel hands Hide and Report to the
 * runtime's content-safety writer, and a report that needed authorization
 * resumes after the callback even when the same Reddit account came back
 * under a new stable id: the ViewModel tells the coordinator the active
 * account's name, which is what lets it recognise the account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelContentSafetyTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val directory = SwitchableDirectory()
    private val writer = RecordingWriter()

    private fun viewModel(capability: AuthCommentWriteCapability = StoredCallbackCapability("reddit:new", "TESTER")) =
        AuthConnectViewModel(
            application,
            unavailableAuthApplicationRuntime().copy(
                accountSessionDirectory = directory,
                authorizationOwnerResolver = AuthCapabilityAuthorizationOwnerResolver(store),
                contentSafetyCapability = capability,
                contentSafetyClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                contentSafetyWriter = writer,
            ),
        ).withAreaViewModels() // AUTHSPLIT05: made with it, as MainActivity does

    @Test
    fun hideAndReportReachTheRuntimesWriterOnce() {
        directory.activate("reddit:old", "tester")
        val viewModel = viewModel()
        idle()

        val hide = RedditPostVisibilityRequest("reddit:old", "t3_post1", hidden = true)
        viewModel.reading().setPostHidden(hide)
        idle()
        assertEquals(listOf(hide), writer.hides)
        assertEquals(ContentSafetyPhase.SUCCEEDED, viewModel.reading().contentSafetyUiState.value.phase)

        val report = RedditContentReportRequest("reddit:old", "t3_post2", "Spam", communityName = "homelab")
        viewModel.reading().reportContent(report)
        idle()
        assertEquals(listOf(report), writer.reports)
        assertEquals(ContentSafetyPhase.SUCCEEDED, viewModel.reading().contentSafetyUiState.value.phase)
    }

    @Test
    fun aReportResumesForTheSameRedditAccountUnderItsNewStableId() {
        store.clearAll()
        directory.activate("reddit:old", "tester")
        writer.requireAuthorizationFirst = true
        val viewModel = viewModel()
        idle()

        viewModel.reading().reportContent(RedditContentReportRequest("reddit:old", "t3_post1", "Spam"))
        idle()
        assertEquals(ContentSafetyPhase.AUTHORIZATION_REQUIRED, viewModel.reading().contentSafetyUiState.value.phase)

        // The authorization comes back for the same Reddit account, which the
        // directory now lists under a new stable id.
        directory.activate("reddit:new", "Tester")
        store.savePendingAuthorization(pending(AuthCapabilityAuthorizationOwner.CONTENT_SAFETY.wireKey))
        viewModel.handleCallbackResultToken("token")
        idle()

        assertEquals(listOf("reddit:old", "reddit:new"), writer.reports.map { it.accountStableId })
        assertEquals(ContentSafetyPhase.SUCCEEDED, viewModel.reading().contentSafetyUiState.value.phase)
        assertEquals("reddit:new", viewModel.reading().contentSafetyUiState.value.accountStableId)
    }

    private fun idle() = repeat(3) { shadowOf(Looper.getMainLooper()).idle() }

    private fun pending(ownerKey: String) = PendingOAuthAuthorization(
        provider = OAuthProvider.REDDIT,
        state = "state",
        redirectUri = "subshell://callback",
        createdAtEpochMs = 1L,
        authorizationOwnerKey = ownerKey,
    )

    private class SwitchableDirectory : AccountSessionDirectory {
        private var current = snapshotOf("reddit:old", "tester")

        fun activate(stableId: String, username: String) {
            current = snapshotOf(stableId, username)
        }

        private fun snapshotOf(stableId: String, username: String) =
            AccountSessionDirectorySnapshot(
                accounts = listOf(
                    AccountSessionSummary(
                        stableId = stableId,
                        username = username,
                        source = AccountSessionSource.AUTHENTICATED,
                        state = AccountSessionState.CONNECTED,
                        providerLabel = "Reddit",
                    ),
                ),
                activeAccountId = stableId,
            )

        override fun snapshot() = current
        override fun selectAccount(stableId: String) = current
    }

    private class RecordingWriter : AuthenticatedContentSafetyWriter {
        val hides = mutableListOf<RedditPostVisibilityRequest>()
        val reports = mutableListOf<RedditContentReportRequest>()
        var requireAuthorizationFirst = false

        override suspend fun setPostHidden(request: RedditPostVisibilityRequest): AuthenticatedContentSafetyResult {
            hides += request
            return AuthenticatedContentSafetyResult.Updated(request.accountStableId, request.postFullname, hidden = request.hidden)
        }

        override suspend fun report(request: RedditContentReportRequest): AuthenticatedContentSafetyResult {
            reports += request
            if (requireAuthorizationFirst && reports.size == 1) {
                return AuthenticatedContentSafetyResult.AuthorizationRequired
            }
            return AuthenticatedContentSafetyResult.Updated(request.accountStableId, request.thingFullname, hidden = true)
        }
    }

    /** Holds a stored callback that upgrades [accountStableId] as [username]. */
    private class StoredCallbackCapability(
        private val accountStableId: String,
        private val username: String,
    ) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = "https://auth.invalid/contentSafety"
        override fun hasPendingAuthorization() = true
        override fun hasStoredCallback() = true
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            AuthCommentWriteCapabilityResult.Upgraded(accountStableId, username)
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
