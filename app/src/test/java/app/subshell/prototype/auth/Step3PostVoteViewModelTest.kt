package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostVoteFailureKind
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (vote01): the app's vote goes from the ViewModel to the vote writer
 * once and the vote state reports the outcome; Retry sends it again only when
 * the failure was retryable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3PostVoteViewModelTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:voter",
        username = "voter",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val request = PostVoteRequest(accountStableId = account.stableId, postFullname = "t3_abc123", direction = 1)
    private val sent = mutableListOf<PostVoteRequest>()

    private fun viewModel(answers: MutableList<AuthenticatedPostVoteResult>) = AuthConnectViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        unavailableAuthApplicationRuntime().copy(
            accountSessionDirectory = object : AccountSessionDirectory {
                override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
                override fun selectAccount(stableId: String) = snapshot()
            },
            postVoteWriter = AuthenticatedPostVoteWriter { request ->
                sent += request
                answers.removeAt(0)
            },
        ),
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun aVoteReachesTheWriterOnceAndItsOutcomeIsReported() {
        val viewModel = viewModel(mutableListOf(AuthenticatedPostVoteResult.Voted(request)))

        viewModel.reading().votePost(request)
        idle()

        assertEquals(listOf(request), sent)
        assertEquals(PostVotePhase.SUCCEEDED, viewModel.reading().postVoteUiState.value.phase)
    }

    @Test
    fun retrySendsAgainOnlyAfterARetryableFailure() {
        val viewModel = viewModel(
            mutableListOf(
                AuthenticatedPostVoteResult.Failed(PostVoteFailureKind.TRANSPORT, retryable = true),
                AuthenticatedPostVoteResult.Failed(PostVoteFailureKind.HTTP, retryable = false),
            ),
        )

        viewModel.reading().votePost(request)
        idle()
        viewModel.reading().retryPostVote()
        idle()
        assertEquals(2, sent.size)
        assertEquals(PostVotePhase.FAILED, viewModel.reading().postVoteUiState.value.phase)

        viewModel.reading().retryPostVote()
        idle()
        assertEquals(2, sent.size)
    }
}
