package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (homeaction01): a save goes from the ViewModel to the save writer
 * once and the save state reports the outcome.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3PostSaveViewModelTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:saver",
        username = "saver",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun aSaveReachesTheWriterOnceAndItsOutcomeIsReported() {
        val sent = mutableListOf<PostSaveRequest>()
        val viewModel = AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                accountSessionDirectory = object : AccountSessionDirectory {
                    override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
                    override fun selectAccount(stableId: String) = snapshot()
                },
                postSaveWriter = AuthenticatedPostSaveWriter { request ->
                    sent += request
                    AuthenticatedPostSaveResult.Updated(request)
                },
            ),
        )
        val request = PostSaveRequest(accountStableId = account.stableId, postFullname = "t3_abc123", saved = true)

        viewModel.savePost(request)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(request), sent)
        assertEquals(PostSavePhase.SUCCEEDED, viewModel.postSaveUiState.value.phase)
    }
}
