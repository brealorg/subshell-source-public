package io.github.brealorg.subshell.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.CommentDeleteRequest
import io.github.brealorg.subshell.data.account.CommentEditRequest
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditProfileCommentTargetResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditProfileCommentsResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentDeleteResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentDeleter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentEditResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentEditor
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.model.ProfileCommentFixture
import io.github.brealorg.subshell.model.ThreadPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * PROFILESSOT01_HISTORY_KEEPS_EDITS_AND_DELETES_V1 through the ViewModel: a
 * confirmed comment edit or delete reaches the account's stored comment
 * history, not only the thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileSsot01EditRoutingTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val account = "reddit:test"
    private val historyChanges = mutableListOf<Pair<String, ProfileCommentFixture?>>()
    private val metadata = NetworkResponseMetadata(
        statusCode = 200,
        receivedAtEpochMs = 0L,
        rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
        retryAfterMs = null,
    )

    private val history = object : AuthenticatedRedditAccountHistoryCommentLoader {
        override suspend fun updateStoredHistoryComment(
            commentFullname: String,
            transform: (ProfileCommentFixture) -> ProfileCommentFixture?,
        ) {
            val stored = ProfileCommentFixture(
                id = commentFullname,
                author = "tester",
                postId = "t3_post",
                age = "1h",
                body = "old",
                score = 1,
                placement = ThreadPlacement.TOP,
            )
            historyChanges += commentFullname to transform(stored)
        }
        override suspend fun cached(accountStableId: String, username: String, kind: RedditAccountHistoryKind) =
            AuthenticatedRedditProfileCommentsResult.NotCached
        override suspend fun synchronize(accountStableId: String, username: String, kind: RedditAccountHistoryKind) =
            AuthenticatedRedditProfileCommentsResult.NotCached
        override suspend fun append(accountStableId: String, username: String, kind: RedditAccountHistoryKind, after: String) =
            AuthenticatedRedditProfileCommentsResult.NotCached
        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditProfileCommentTargetResult.AuthorizationRequired
    }

    @Test
    fun aConfirmedEditAndDeleteReachTheCommentHistory() {
        ensureWorkManagerInitialized(application)
        val viewModel = AuthConnectViewModel(
            application,
            unavailableAuthApplicationRuntime().copy(
                accountSessionDirectory = FixedDirectory(account),
                commentEditClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                commentEditor = AuthenticatedCommentEditor { request ->
                    AuthenticatedCommentEditResult.Edited(account, request.commentFullname, request.text, metadata)
                },
                commentDeleter = AuthenticatedCommentDeleter { request ->
                    AuthenticatedCommentDeleteResult.Deleted(account, request.commentFullname, metadata)
                },
                redditAccountHistoryCommentLoader = history,
            ),
        )

        viewModel.reading().editComment(CommentEditRequest(account, "t3_post", "t1_mine", "tester", "edited", RedditCommentSort.BEST))
        idle()
        viewModel.reading().resetCommentEditState()
        viewModel.reading().deleteComment(CommentDeleteRequest(account, "t3_post", "t1_mine", "tester", RedditCommentSort.BEST))
        idle()

        assertEquals(listOf("t1_mine", "t1_mine"), historyChanges.map { it.first })
        assertEquals("edited", historyChanges[0].second?.body)
        assertNull(historyChanges[1].second)
    }

    private fun idle() = repeat(3) { shadowOf(Looper.getMainLooper()).idle() }

    private class FixedDirectory(private val active: String) : AccountSessionDirectory {
        private val snapshot = AccountSessionDirectorySnapshot(
            accounts = listOf(
                AccountSessionSummary(
                    stableId = active,
                    username = "tester",
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                ),
            ),
            activeAccountId = active,
        )

        override fun snapshot() = snapshot
        override fun selectAccount(stableId: String) = snapshot
    }
}
