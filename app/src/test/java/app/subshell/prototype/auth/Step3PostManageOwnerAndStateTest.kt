package app.subshell.prototype.auth

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostManageFailureKind
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageLoadResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManageMutationResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManager
import app.subshell.prototype.ui.FeedSort
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.mainPostFeedQueryKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (write02f): only the author may manage a post — anyone else is
 * refused before Reddit is asked — and a confirmed change shows on that post
 * in every loaded feed while the other posts stay as they were.
 */
class Step3PostManageOwnerAndStateTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private class CountingManager : AuthenticatedPostManager {
        var calls = 0
        override suspend fun load(target: PostManageTarget): AuthenticatedPostManageLoadResult {
            calls += 1
            error("not reached in this test")
        }
        override suspend fun mutate(request: PostManageMutationRequest): AuthenticatedPostManageMutationResult {
            calls += 1
            return AuthenticatedPostManageMutationResult.Updated
        }
    }

    private fun coordinator(manager: AuthenticatedPostManager) = PostManageCoordinator(
        manager = manager,
        accountDirectory = object : AccountSessionDirectory {
            override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
            override fun selectAccount(stableId: String) = snapshot()
        },
        editCapability = null,
        editClientMode = null,
        flairCapability = null,
        flairClientMode = null,
        moderationCapability = null,
        moderationClientMode = null,
    )

    private fun nsfwOn(author: String) = PostManageMutationRequest(
        target = PostManageTarget(account.stableId, "t3_abc", author, "kotlin"),
        operation = PostManageOperation.SET_NSFW,
        enabled = true,
    )

    @Test
    fun someoneElsesPostIsRefusedBeforeRedditIsAsked() = runBlocking {
        val manager = CountingManager()
        val coordinator = coordinator(manager)

        coordinator.mutate(nsfwOn(author = "someone_else"))
        assertEquals(PostManageFailureKind.NOT_OWNER, coordinator.uiState.value.failureKind)
        coordinator.load(PostManageTarget(account.stableId, "t3_abc", "someone_else", "kotlin"))
        assertEquals(PostManageFailureKind.NOT_OWNER, coordinator.uiState.value.failureKind)
        assertEquals(0, manager.calls)

        coordinator.mutate(nsfwOn(author = "me"))
        assertNotEquals(PostManageFailureKind.NOT_OWNER, coordinator.uiState.value.failureKind)
    }

    @Test
    fun aConfirmedChangeShowsOnThatPostInEveryLoadedFeed() {
        val accounts = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts.first().stableId,
        )
        val best = mainPostFeedQueryKey(state.activeAccountSession, state.feedSource, FeedSort.BEST)
        val new = mainPostFeedQueryKey(state.activeAccountSession, state.feedSource, FeedSort.NEW)
        state.ensurePostPaging(best)
        state.ensurePostPaging(new)
        val target = SampleData.posts.first()
        val other = SampleData.posts[1]

        state.applyManagedPostMetadata(postId = target.id, flairText = " Solved ", nsfw = true, spoiler = true)

        listOf(best, new).forEach { key ->
            val items = state.postPagingSnapshot(key).items
            val changed = items.single { it.id == target.id }
            assertEquals("Solved", changed.flair)
            assertTrue(changed.nsfw)
            assertTrue(changed.spoiler)
            val untouched = items.single { it.id == other.id }
            assertEquals(other.flair, untouched.flair)
            assertEquals(other.nsfw, untouched.nsfw)
            assertEquals(other.spoiler, untouched.spoiler)
        }
    }
}
