package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.PostStoreChanges
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SettledPostActions
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OPENPOSTSSOT01 (#244): the open post takes the feeds' new copy (not an older
 * one it was opened past), a settled vote or save gives way once every copy
 * shows it (so the copies follow Reddit from then on), one in flight never
 * does, and a confirmed post edit is written to the store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenPostSsot01FollowsTheStoreTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val home = RepositoryQueryKey("feed:home")
    private val base = SampleData.posts.first().copy(id = "t3_abc", score = 10, vote = 0, saved = false)

    /** Stored posts a test changes, signalling each change as the store does. */
    private class StoreRepository(
        @Volatile var stored: Post,
        private val fake: SubshellRepository = FakeSubshellRepository(),
    ) : SubshellRepository by fake, PostStoreChanges, SettledPostActions {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val edits = mutableListOf<String>()

        override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> =
            RepositoryResult.Content(listOf(stored))

        override fun postStoreChanges(): Flow<Unit> = changes

        override fun storeSettledVote(accountStableId: String, postFullname: String, direction: Int) = Unit

        override fun storeSettledSave(accountStableId: String, postFullname: String, saved: Boolean) = Unit

        override fun storeEditedPost(accountStableId: String, postFullname: String, transform: (Post) -> Post) {
            edits += "$accountStableId $postFullname ${transform(stored).body}"
        }
    }

    private fun signedIn(repository: SubshellRepository) = SubshellState(
        repository = repository,
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
    )

    /** Runs [block] while the state follows the store, as RuntimeStateBridge does. */
    private fun following(repository: StoreRepository, block: suspend (SubshellState, suspend (Post) -> Unit) -> Unit) =
        runBlocking {
            val state = signedIn(repository)
            state.ensurePostPaging(home)
            val job = launch(start = CoroutineStart.UNDISPATCHED) { state.followPostStore() }
            while (repository.changes.subscriptionCount.value == 0) yield()
            val storeWrites: suspend (Post) -> Unit = { post ->
                repository.stored = post
                repository.changes.emit(Unit)
                repeat(3) { yield() }
                state.followLoadedPostCopies()
            }
            state.followLoadedPostCopies()
            block(state, storeWrites)
            job.cancel()
        }

    @Test
    fun theOpenPostTakesTheFeedsNewCopy() = following(StoreRepository(base)) { state, storeWrites ->
        state.open(state.postPagingSnapshot(home).items.single())
        state.followLoadedPostCopies()

        storeWrites(base.copy(score = 11, vote = 1))

        assertEquals(1, state.selectedPost?.vote)
        assertEquals(11, state.selectedPost?.score)
    }

    @Test
    fun aPostOpenedWithNewerDataKeepsItUntilTheFeedsChange() = following(StoreRepository(base)) { state, storeWrites ->
        // Opened from a link with a newer score than the feed's older copy.
        state.open(base.copy(score = 20))
        state.followLoadedPostCopies()

        assertEquals(20, state.selectedPost?.score)

        storeWrites(base.copy(score = 21))

        assertEquals(21, state.selectedPost?.score)
    }

    @Test
    fun aSettledVoteGivesWayOnceTheCopiesShowItAndTheCopiesFollowRedditAfter() =
        following(StoreRepository(base)) { state, storeWrites ->
            state.open(state.postPagingSnapshot(home).items.single())
            state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SUCCEEDED, account.stableId, "t3_abc", 1))
            state.followLoadedPostCopies()

            // The store has not written it yet: the override stands in.
            assertEquals(1, state.effectivePostVote(state.selectedPost!!))

            storeWrites(base.copy(score = 11, vote = 1))
            assertEquals(1, state.effectivePostVote(state.selectedPost!!))

            // Reddit later says the vote was taken back (another device).
            storeWrites(base.copy(score = 10, vote = 0))
            assertEquals(0, state.effectivePostVote(state.selectedPost!!))
            assertEquals(0, state.effectivePostVote(state.postPagingSnapshot(home).items.single()))
        }

    @Test
    fun aVoteInFlightNeverGivesWay() = following(StoreRepository(base)) { state, storeWrites ->
        state.open(state.postPagingSnapshot(home).items.single())
        state.followLoadedPostCopies()
        state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SENDING, account.stableId, "t3_abc", 1))

        // Every copy shows the vote while Reddit has not answered yet.
        storeWrites(base.copy(vote = 1))
        assertEquals(1, state.selectedPost?.vote)
        storeWrites(base.copy(vote = 0))

        assertEquals(1, state.effectivePostVote(state.selectedPost!!))
    }

    @Test
    fun aSettledSaveGivesWayOnceTheCopiesShowIt() = following(StoreRepository(base)) { state, storeWrites ->
        state.open(state.postPagingSnapshot(home).items.single())
        state.synchronizePostSaveState(PostSaveUiState(PostSavePhase.SUCCEEDED, account.stableId, "t3_abc", saved = true))
        state.followLoadedPostCopies()
        assertTrue(state.effectivePostSaved(state.selectedPost!!))

        storeWrites(base.copy(saved = true))
        storeWrites(base.copy(saved = false))

        assertFalse(state.effectivePostSaved(state.selectedPost!!))
    }

    @Test
    fun aSettledCommentVoteGivesWayOnceTheThreadShowsIt() {
        val state = signedIn(StoreRepository(base))
        val reply = comment("t1_reply", vote = 0)
        val thread = listOf(comment("t1_top", vote = 0).copy(children = listOf(reply)))
        state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SUCCEEDED, account.stableId, "t1_reply", 1))

        state.releaseSettledCommentOverrides(thread)
        assertEquals(1, state.effectiveCommentVote(reply))

        state.releaseSettledCommentOverrides(listOf(thread.single().copy(children = listOf(reply.copy(vote = 1)))))
        assertEquals(0, state.effectiveCommentVote(reply))
    }

    @Test
    fun aSettledCommentVoteOutsideTheThreadStays() {
        val state = signedIn(StoreRepository(base))
        state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SUCCEEDED, account.stableId, "t1_elsewhere", 1))

        state.releaseSettledCommentOverrides(listOf(comment("t1_top", vote = 1)))

        assertEquals(1, state.effectiveCommentVote(comment("t1_elsewhere", vote = 0)))
    }

    @Test
    fun aConfirmedPostEditIsWrittenToTheStore() {
        val repository = StoreRepository(base.copy(body = "old"))
        val state = signedIn(repository)

        state.applyEditedPostBody("t3_abc", "new body")

        assertEquals(listOf("reddit:me t3_abc new body"), repository.edits)
    }

    @Test
    fun aConfirmedFlairNsfwOrSpoilerChangeIsWrittenToTheStore() {
        val repository = StoreRepository(base.copy(body = "body"))
        val state = signedIn(repository)

        state.applyManagedPostMetadata("t3_abc", flairText = "News", nsfw = true, spoiler = false)

        assertEquals(listOf("reddit:me t3_abc body"), repository.edits)
    }

    private fun comment(id: String, vote: Int) =
        Comment(id = id, author = "a", age = "1h", body = "b", score = 1, depth = 0, accent = 0L, vote = vote)
}
