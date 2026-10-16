package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a6, savedcomments01a2): the ViewModel hands a profile's
 * post history to the runtime's account-history updater and its Saved list
 * to the runtime's saved-history loader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelAccountHistoryTest {
    @Test
    fun postHistoryAndSavedReachTheRuntimesReaders() {
        val history = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val updater = AuthenticatedRedditAccountHistoryUpdater { account, username, kind, operation ->
            history += "$account:$username:$kind:$operation"
            AuthenticatedRedditAccountHistoryUpdateResult.AuthorizationRequired
        }
        val loader = object : AuthenticatedRedditSavedHistoryLoader {
            override suspend fun cached(accountStableId: String, username: String) =
                AuthenticatedRedditSavedHistoryResult.NotCached

            override suspend fun synchronize(accountStableId: String, username: String): AuthenticatedRedditSavedHistoryResult {
                saved += "$accountStableId:$username"
                return AuthenticatedRedditSavedHistoryResult.AuthorizationRequired
            }

            override suspend fun append(accountStableId: String, username: String, after: String) =
                AuthenticatedRedditSavedHistoryResult.AuthorizationRequired
        }
        val viewModel = AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                redditAccountHistoryUpdater = updater,
                redditSavedHistoryLoader = loader,
            ),
        )

        viewModel.refreshRedditAccountHistory("reddit:test", "alice", RedditAccountHistoryKind.UPVOTED)
        idle()
        viewModel.refreshRedditSavedHistory("reddit:test", "alice")
        idle()

        assertEquals(listOf("reddit:test:alice:UPVOTED:${RepositoryLoadOperation.REFRESH}"), history)
        assertEquals(listOf("reddit:test:alice"), saved)
        assertEquals(RedditAccountHistoryKind.UPVOTED, viewModel.redditAccountHistoryUiState.value.kind)
        assertEquals("alice", viewModel.redditSavedHistoryUiState.value.username)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
