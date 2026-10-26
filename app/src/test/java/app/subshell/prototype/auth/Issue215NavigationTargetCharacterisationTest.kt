package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.RedditAccountHistoryUiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryCommentResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentTargetResult
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditProfileCommentsResult
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #215: characterises the Reddit navigation target resolvers through the
 * ViewModel before they move to a coordinator: input checks, the history
 * state guard before and after the await, each loader answer, and the
 * authorization/failure writes to the history state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue215NavigationTargetCharacterisationTest {
    private val account = "reddit:a"
    private val target = post("abc")

    // Inbox / deep-link target post.

    @Test
    fun aBlankAccountOrPostIdResolvesNothingWithoutAskingTheLoader() {
        val inbox = FakeInbox { AuthenticatedRedditInboxTargetResult.Loaded(target, fromCache = false) }
        val viewModel = viewModel(inbox = inbox)

        assertNull(resolveInbox(viewModel, "  ", "abc"))
        assertNull(resolveInbox(viewModel, account, " t3_ "))
        assertTrue(inbox.calls.isEmpty())
    }

    @Test
    fun withoutAnInboxLoaderNothingResolves() {
        assertNull(resolveInbox(viewModel(), account, "abc"))
    }

    @Test
    fun theInboxLoaderGetsTheTrimmedAccountAndBarePostId() {
        val inbox = FakeInbox { AuthenticatedRedditInboxTargetResult.Loaded(target, fromCache = true) }
        val viewModel = viewModel(inbox = inbox)

        assertEquals(target, resolveInbox(viewModel, " $account ", " t3_abc "))
        assertEquals(listOf(account to "abc"), inbox.calls)
    }

    @Test
    fun everyOtherInboxAnswerResolvesNothing() {
        listOf(
            { AuthenticatedRedditInboxTargetResult.AuthorizationRequired },
            { AuthenticatedRedditInboxTargetResult.NotFound },
            { AuthenticatedRedditInboxTargetResult.Failed(AuthenticatedRedditInboxFailureKind.HTTP, retryable = true) },
            { error("storage") },
        ).forEach { answer ->
            assertNull(resolveInbox(viewModel(inbox = FakeInbox(answer)), account, "abc"))
        }
    }

    // Profile comment target.

    @Test
    fun profileTargetNeedsAValidUsernameAndPostId() {
        val history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.Loaded(target, fromCache = false) }
        val viewModel = viewModel(history = history)
        viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
        idle()

        assertNull(resolveProfile(viewModel, account, "not a name!", "abc"))
        assertNull(resolveProfile(viewModel, account, "alice", "t3_"))
        assertNull(resolveProfile(viewModel, " ", "alice", "abc"))
        assertTrue(history.targetCalls.isEmpty())
    }

    @Test
    fun profileTargetNeedsTheOpenCommentHistoryOfThatUser() {
        val history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.Loaded(target, fromCache = false) }
        val viewModel = viewModel(history = history)

        assertNull(resolveProfile(viewModel, account, "alice", "abc"))
        viewModel.reading().refreshRedditAccountHistory(account, "bob", RedditAccountHistoryKind.COMMENTS)
        idle()
        assertNull(resolveProfile(viewModel, account, "alice", "abc"))
        assertTrue(history.targetCalls.isEmpty())
    }

    @Test
    fun withoutACommentLoaderNoProfileTargetResolves() {
        val viewModel = viewModel()
        viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
        idle()

        assertNull(resolveProfile(viewModel, account, "alice", "abc"))
    }

    @Test
    fun aLoadedProfileTargetIsReturned() {
        val history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.Loaded(target, fromCache = false) }
        val viewModel = viewModel(history = history)
        viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
        idle()

        assertEquals(target, resolveProfile(viewModel, account, "Alice", "t3_abc"))
        assertEquals(listOf(account to "abc"), history.targetCalls)
    }

    @Test
    fun aProfileTargetNeedingAuthorizationMarksTheHistory() {
        val history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.AuthorizationRequired }
        val viewModel = viewModel(history = history)
        viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
        idle()

        assertNull(resolveProfile(viewModel, account, "alice", "abc"))
        val state = viewModel.reading().redditAccountHistoryUiState.value
        assertTrue(state.authorizationRequired)
        assertFalse(state.authorizing)
        assertNull(state.failureKind)
        assertFalse(state.failureRetryable)
    }

    @Test
    fun aFailedProfileTargetMapsItsKindIntoTheHistory() {
        mapOf(
            AuthenticatedRedditProfileCommentsFailureKind.SESSION to RedditAccountHistoryUiFailureKind.SESSION,
            AuthenticatedRedditProfileCommentsFailureKind.HTTP to RedditAccountHistoryUiFailureKind.HTTP,
            AuthenticatedRedditProfileCommentsFailureKind.TRANSPORT to RedditAccountHistoryUiFailureKind.TRANSPORT,
            AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL to RedditAccountHistoryUiFailureKind.PROTOCOL,
            AuthenticatedRedditProfileCommentsFailureKind.STORAGE to RedditAccountHistoryUiFailureKind.STORAGE,
        ).forEach { (kind, uiKind) ->
            val history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.Failed(kind, retryable = true) }
            val viewModel = viewModel(history = history)
            viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
            idle()

            assertNull(resolveProfile(viewModel, account, "alice", "abc"))
            val state = viewModel.reading().redditAccountHistoryUiState.value
            assertEquals(uiKind, state.failureKind)
            assertTrue(state.failureRetryable)
            assertFalse(state.authorizationRequired)
        }
    }

    @Test
    fun aThrowingProfileTargetIsARetryableStorageFailure() {
        val viewModel = viewModel(history = FakeHistory { error("disk") })
        viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
        idle()

        assertNull(resolveProfile(viewModel, account, "alice", "abc"))
        assertEquals(RedditAccountHistoryUiFailureKind.STORAGE, viewModel.reading().redditAccountHistoryUiState.value.failureKind)
        assertTrue(viewModel.reading().redditAccountHistoryUiState.value.failureRetryable)
    }

    @Test
    fun aProfileTargetNotFoundLeavesTheHistoryAsItWas() {
        val viewModel = viewModel(history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.NotFound })
        viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
        idle()
        val before = viewModel.reading().redditAccountHistoryUiState.value

        assertNull(resolveProfile(viewModel, account, "alice", "abc"))
        assertEquals(before, viewModel.reading().redditAccountHistoryUiState.value)
    }

    @Test
    fun aProfileAnswerForAHistoryThatChangedMeanwhileIsDropped() {
        val gate = CompletableDeferred<AuthenticatedRedditProfileCommentTargetResult>()
        val viewModel = viewModel(history = FakeHistory { gate.await() })
        viewModel.reading().refreshRedditAccountHistory(account, "alice", RedditAccountHistoryKind.COMMENTS)
        idle()
        val answers = mutableListOf<Post?>()
        viewModel.reading().resolveRedditProfileCommentTarget(account, "alice", "abc") { answers += it }
        idle()

        viewModel.reading().refreshRedditAccountHistory(account, "bob", RedditAccountHistoryKind.COMMENTS)
        idle()
        gate.complete(AuthenticatedRedditProfileCommentTargetResult.Loaded(target, fromCache = false))
        idle()

        assertEquals(listOf<Post?>(null), answers)
        assertEquals("bob", viewModel.reading().redditAccountHistoryUiState.value.username)
    }

    // Saved comment target.

    @Test
    fun savedTargetNeedsTheOpenSavedHistoryOfThatUser() {
        val history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.Loaded(target, fromCache = false) }
        val viewModel = viewModel(history = history)

        assertNull(resolveSaved(viewModel, account, "alice", "abc"))
        viewModel.reading().refreshRedditSavedHistory(account, "bob")
        idle()
        assertNull(resolveSaved(viewModel, account, "alice", "abc"))
        assertNull(resolveSaved(viewModel, account, "bob", " t3_ "))
        assertTrue(history.targetCalls.isEmpty())
    }

    @Test
    fun aLoadedSavedTargetIsReturned() {
        val history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.Loaded(target, fromCache = false) }
        val viewModel = viewModel(history = history)
        viewModel.reading().refreshRedditSavedHistory(account, "alice")
        idle()

        assertEquals(target, resolveSaved(viewModel, " $account", "alice", "t3_abc"))
        assertEquals(listOf(account to "abc"), history.targetCalls)
    }

    @Test
    fun aSavedTargetNeedingAuthorizationOrFailingMarksTheSavedHistory() {
        val authorizing = viewModel(history = FakeHistory { AuthenticatedRedditProfileCommentTargetResult.AuthorizationRequired })
        authorizing.reading().refreshRedditSavedHistory(account, "alice")
        idle()
        assertNull(resolveSaved(authorizing, account, "alice", "abc"))
        authorizing.reading().redditSavedHistoryUiState.value.let { state ->
            assertTrue(state.authorizationRequired)
            assertNull(state.failureKind)
        }

        val failing = viewModel(
            history = FakeHistory {
                AuthenticatedRedditProfileCommentTargetResult.Failed(AuthenticatedRedditProfileCommentsFailureKind.PROTOCOL)
            },
        )
        failing.reading().refreshRedditSavedHistory(account, "alice")
        idle()
        assertNull(resolveSaved(failing, account, "alice", "abc"))
        failing.reading().redditSavedHistoryUiState.value.let { state ->
            assertFalse(state.authorizationRequired)
            assertEquals(RedditAccountHistoryUiFailureKind.PROTOCOL, state.failureKind)
            assertFalse(state.failureRetryable)
        }
    }

    @Test
    fun aSavedAnswerForAHistoryThatChangedMeanwhileIsDropped() {
        val gate = CompletableDeferred<AuthenticatedRedditProfileCommentTargetResult>()
        val viewModel = viewModel(history = FakeHistory { gate.await() })
        viewModel.reading().refreshRedditSavedHistory(account, "alice")
        idle()
        val answers = mutableListOf<Post?>()
        viewModel.reading().resolveRedditSavedCommentTarget(account, "alice", "abc") { answers += it }
        idle()

        viewModel.reading().refreshRedditSavedHistory(account, "bob")
        idle()
        gate.complete(AuthenticatedRedditProfileCommentTargetResult.Loaded(target, fromCache = false))
        idle()

        assertEquals(listOf<Post?>(null), answers)
    }

    private fun resolveInbox(viewModel: AuthConnectViewModel, account: String, postId: String): Post? =
        collect { viewModel.reading().resolveRedditNavigationTargetPost(account, postId, it) }

    private fun resolveProfile(viewModel: AuthConnectViewModel, account: String, user: String, postId: String): Post? =
        collect { viewModel.reading().resolveRedditProfileCommentTarget(account, user, postId, it) }

    private fun resolveSaved(viewModel: AuthConnectViewModel, account: String, user: String, postId: String): Post? =
        collect { viewModel.reading().resolveRedditSavedCommentTarget(account, user, postId, it) }

    private fun collect(start: ((Post?) -> Unit) -> Unit): Post? {
        val answers = mutableListOf<Post?>()
        start { answers += it }
        idle()
        assertEquals("exactly one answer", 1, answers.size)
        return answers.single()
    }

    private fun viewModel(
        inbox: AuthenticatedRedditInboxLoader? = null,
        history: AuthenticatedRedditAccountHistoryCommentLoader? = null,
    ) = AuthConnectViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        unavailableAuthApplicationRuntime().copy(
            redditInboxLoader = inbox,
            redditAccountHistoryCommentLoader = history,
        ),
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun post(id: String) = Post(
        id = id,
        community = "r/test",
        author = "me",
        age = "1h",
        title = id,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private class FakeInbox(
        private val answer: suspend () -> AuthenticatedRedditInboxTargetResult,
    ) : AuthenticatedRedditInboxLoader {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun cached(accountStableId: String): AuthenticatedRedditInboxResult = error("not used")
        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditInboxResult = error("not used")
        override suspend fun append(accountStableId: String, after: String): AuthenticatedRedditInboxResult = error("not used")

        override suspend fun resolveTargetPost(accountStableId: String, postId: String): AuthenticatedRedditInboxTargetResult {
            calls += accountStableId to postId
            return answer()
        }
    }

    /** The comment history itself fails quietly; only the target answer is scripted. */
    private class FakeHistory(
        private val answer: suspend () -> AuthenticatedRedditProfileCommentTargetResult,
    ) : AuthenticatedRedditAccountHistoryCommentLoader {
        val targetCalls = mutableListOf<Pair<String, String>>()

        override suspend fun cached(accountStableId: String, username: String, kind: RedditAccountHistoryKind): AuthenticatedRedditAccountHistoryCommentResult =
            AuthenticatedRedditProfileCommentsResult.NotCached

        override suspend fun synchronize(accountStableId: String, username: String, kind: RedditAccountHistoryKind): AuthenticatedRedditAccountHistoryCommentResult =
            AuthenticatedRedditProfileCommentsResult.Failed(AuthenticatedRedditProfileCommentsFailureKind.TRANSPORT)

        override suspend fun append(accountStableId: String, username: String, kind: RedditAccountHistoryKind, after: String): AuthenticatedRedditAccountHistoryCommentResult =
            error("not used")

        override suspend fun resolveTargetPost(accountStableId: String, postId: String): AuthenticatedRedditProfileCommentTargetResult {
            targetCalls += accountStableId to postId
            return answer()
        }
    }
}
