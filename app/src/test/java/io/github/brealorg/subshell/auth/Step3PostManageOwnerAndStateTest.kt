package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.PostManageFailureKind
import io.github.brealorg.subshell.data.account.PostManageMutationRequest
import io.github.brealorg.subshell.data.account.PostManageOperation
import io.github.brealorg.subshell.data.account.PostManageTarget
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostManageLoadResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostManageMutationResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostManager
import io.github.brealorg.subshell.ui.FeedSort
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.mainPostFeedQueryKey
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
