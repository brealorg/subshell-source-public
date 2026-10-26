package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.DirectChatStartFailureKind
import app.subshell.prototype.data.account.DirectChatStartPhase
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModQueueResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationTargetResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatStartResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatStartWriter
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
 * LOW8: work started for one account touched another after a switch: the
 * mod queue kept the old account's moderator directory (OCB-590), a resolved
 * moderation post opened for the new account (OCB-425), another account's
 * reset cleared a Direct Chat unknown outcome and that state blocked every
 * account (OCB-418), and the moderator log left its authorization pending
 * (OCB-424).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low8AccountSwitchTest {
    private val accountA = "reddit:a"
    private val accountB = "reddit:b"

    @Test
    fun aModQueueForAnotherAccountStartsWithoutTheOldDirectory() {
        val loader = ModerationLoader()
        val viewModel = viewModel(loader)
        viewModel.loadRedditModeratorCommunities(accountA)
        idle()
        loader.directoryGate.complete(AuthenticatedRedditModeratedCommunitiesResult.Loaded(setOf("alpha")))
        idle()
        assertEquals(setOf("alpha"), viewModel.redditModerationUiState.value.moderatedCommunities)

        viewModel.loadRedditModQueue(accountB, "alpha")
        idle()

        val state = viewModel.redditModerationUiState.value
        assertEquals(accountB, state.accountStableId)
        assertTrue(state.moderatedCommunities.isEmpty())
        assertFalse(state.directoryLoaded)
    }

    @Test
    fun aModerationPostResolvedAfterASwitchOpensNothing() {
        val loader = ModerationLoader()
        val viewModel = viewModel(loader)
        viewModel.loadRedditModQueue(accountA, "alpha")
        idle()
        var resolved: Post? = Post(id = "sentinel", community = "r/x", author = "x", age = "1h", title = "x", score = 0, comments = 0, kind = PostKind.TEXT)
        viewModel.resolveRedditModerationTarget(accountA, "t3_abc") { resolved = it }
        idle()
        viewModel.loadRedditModQueue(accountB, "alpha")
        idle()

        loader.targetGate.complete(
            AuthenticatedRedditModerationTargetResult.Loaded(
                Post(id = "t3_abc", community = "r/alpha", author = "x", age = "1h", title = "A's post", score = 0, comments = 0, kind = PostKind.TEXT),
            ),
        )
        idle()

        assertNull(resolved)
    }

    @Test
    fun aDirectChatUnknownOutcomeBlocksOnlyItsOwnAccountAndSurvivesAnotherAccountsReset() {
        val starts = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = DirectChatCoordinator(
            directChatStartWriter = object : AuthenticatedRedditDirectChatStartWriter {
                override suspend fun start(request: DirectChatStartRequest): AuthenticatedRedditDirectChatStartResult {
                    starts += request.accountStableId
                    return AuthenticatedRedditDirectChatStartResult.Failed(DirectChatStartFailureKind.OUTCOME_UNKNOWN)
                }
            },
            directChatLiveReader = null,
            directChatTypingWriter = null,
            directChatRedactionWriter = null,
            directChatReactionWriter = null,
            directChatReactionPresentationCache = null,
            directChatReactionRelationsProbeReader = null,
            onChatStarted = {},
            scope = scope,
        )
        coordinator.startRedditDirectChat(DirectChatStartRequest(accountA, "peer", "hello"))
        assertEquals(DirectChatStartPhase.OUTCOME_UNKNOWN, coordinator.directChatStartUiState.value.phase)

        coordinator.resetRedditDirectChatStartState(activeAccountStableId = accountB)
        assertEquals(DirectChatStartPhase.OUTCOME_UNKNOWN, coordinator.directChatStartUiState.value.phase)

        coordinator.startRedditDirectChat(DirectChatStartRequest(accountA, "peer", "hello"))
        coordinator.startRedditDirectChat(DirectChatStartRequest(accountB, "peer", "hello"))
        assertEquals(listOf(accountA, accountB), starts)
        scope.cancel()
    }

    // OCB418_UNKNOWN_START_OUTCOME_KEPT_PER_ACCOUNT_V1
    @Test
    fun anUnknownStartOutcomeSurvivesAnotherAccountsStartAndKeepsBlockingItsAccount() {
        val starts = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = DirectChatCoordinator(
            directChatStartWriter = object : AuthenticatedRedditDirectChatStartWriter {
                override suspend fun start(request: DirectChatStartRequest): AuthenticatedRedditDirectChatStartResult {
                    starts += request.accountStableId
                    return AuthenticatedRedditDirectChatStartResult.Failed(DirectChatStartFailureKind.OUTCOME_UNKNOWN)
                }
            },
            directChatLiveReader = null,
            directChatTypingWriter = null,
            directChatRedactionWriter = null,
            directChatReactionWriter = null,
            directChatReactionPresentationCache = null,
            directChatReactionRelationsProbeReader = null,
            onChatStarted = {},
            scope = scope,
        )
        coordinator.startRedditDirectChat(DirectChatStartRequest(accountA, "peer", "hello"))
        // Account B starts its own chat; A's unknown outcome is set aside, not lost.
        coordinator.startRedditDirectChat(DirectChatStartRequest(accountB, "other", "hi"))
        assertEquals(accountB, coordinator.directChatStartUiState.value.accountStableId)

        // Back on A: a resend stays blocked, and A's screen shows its unknown outcome again.
        coordinator.startRedditDirectChat(DirectChatStartRequest(accountA, "peer", "hello"))
        assertEquals(listOf(accountA, accountB), starts)
        coordinator.resetRedditDirectChatStartState(activeAccountStableId = accountA)
        val shown = coordinator.directChatStartUiState.value
        assertEquals(accountA, shown.accountStableId)
        assertEquals(DirectChatStartPhase.OUTCOME_UNKNOWN, shown.phase)
        scope.cancel()
    }

    @Test
    fun anAccountSwitchCancelsTheModeratorLogsPendingAuthorization() = runBlocking<Unit> {
        val capability = RecordingCapability()
        var active = accountA
        val coordinator = ModerationLogCoordinator(
            capability = capability,
            loader = null,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { active },
        )
        coordinator.open(accountA, "alpha")
        coordinator.beginAuthorization()
        assertTrue(coordinator.uiState.value.authorizing)

        active = accountB
        coordinator.accountChanged(accountB)

        assertEquals(1, capability.cancels)
    }

    // AUTHSPLIT01: the mod queue lives in ModerationViewModel, sharing the
    // runtime AuthConnectViewModel owns.
    private fun viewModel(loader: AuthenticatedRedditModerationLoader): ModerationViewModel =
        ModerationViewModel(
            AuthConnectViewModel(
                ApplicationProvider.getApplicationContext<Application>(),
                unavailableAuthApplicationRuntime().copy(redditModerationLoader = loader),
            ).sharedRuntime,
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private class ModerationLoader : AuthenticatedRedditModerationLoader {
        val directoryGate = CompletableDeferred<AuthenticatedRedditModeratedCommunitiesResult>()
        val targetGate = CompletableDeferred<AuthenticatedRedditModerationTargetResult>()
        override suspend fun loadModeratorCommunities(accountStableId: String) = directoryGate.await()
        override suspend fun loadModQueue(accountStableId: String, rawCommunityName: String, after: String?): AuthenticatedRedditModQueueResult =
            CompletableDeferred<AuthenticatedRedditModQueueResult>().await()
        override suspend fun resolveTargetPost(accountStableId: String, postId: String) = targetGate.await()
    }

    private class RecordingCapability : AuthCommentWriteCapability {
        var cancels = 0
        override suspend fun beginAuthorization(accountStableId: String): String = "https://example.test/authorize"
        override fun hasPendingAuthorization() = true
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() { cancels++ }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = AuthSessionResult.ReauthenticationRequired
    }
}
