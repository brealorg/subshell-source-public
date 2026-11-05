package io.github.brealorg.subshell.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.PostVoteFailureKind
import io.github.brealorg.subshell.data.account.PostVotePhase
import io.github.brealorg.subshell.data.account.PostVoteRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostVoteResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostVoteWriter
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
