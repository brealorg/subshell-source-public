package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Behaviour test for the user-feed dispatch that check-profile01a2-contract.sh
 * counts as AuthConnectViewModel text: refresh and append of a user's
 * submitted posts both reach the feed updater with the user target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelUserFeedTest {
    @Test
    fun refreshAndAppendDispatchTheUserTargetToTheFeedUpdater() {
        val calls = mutableListOf<Triple<String, RepositoryLoadOperation, AuthenticatedRedditFeedTarget>>()
        val updater = AuthenticatedRedditFeedUpdater { account, operation, target ->
            calls += Triple(account, operation, target)
            AuthenticatedRedditFeedUpdateResult.Failed(
                kind = AuthenticatedRedditFeedUpdateFailureKind.TRANSPORT,
                retryable = true,
            )
        }
        val viewModel = AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(redditFeedUpdater = updater),
        )

        viewModel.reading().refreshRedditUserSubmitted("reddit:test", "alice")
        idle()
        viewModel.reading().appendRedditUserSubmitted("reddit:test", "alice")
        idle()

        assertEquals(
            listOf(
                Triple("reddit:test", RepositoryLoadOperation.REFRESH, AuthenticatedRedditFeedTarget.user("alice")),
                Triple("reddit:test", RepositoryLoadOperation.APPEND, AuthenticatedRedditFeedTarget.user("alice")),
            ),
            calls,
        )
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
