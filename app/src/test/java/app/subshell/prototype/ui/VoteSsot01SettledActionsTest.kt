package app.subshell.prototype.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryLoadOperation
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
import app.subshell.prototype.data.account.PostVoteSettlement
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VOTESSOT01_SETTLED_ACTIONS_REACH_THE_STORE_V1 (#244): a post vote or save
 * Reddit confirmed is written to the stored post (it lived only in the
 * override map); a comment vote and a vote still in flight are not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoteSsot01SettledActionsTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private class RecordingRepository(
        private val fake: SubshellRepository = FakeSubshellRepository(),
    ) : SubshellRepository by fake, SettledPostActions {
        val stored = mutableListOf<String>()

        override fun storeSettledVote(accountStableId: String, postFullname: String, direction: Int) {
            stored += "vote $accountStableId $postFullname $direction"
        }

        override fun storeSettledSave(accountStableId: String, postFullname: String, saved: Boolean) {
            stored += "save $accountStableId $postFullname $saved"
        }

        override fun storeEditedPost(
            accountStableId: String,
            postFullname: String,
            transform: (app.subshell.prototype.model.Post) -> app.subshell.prototype.model.Post,
        ) {
            stored += "edit $accountStableId $postFullname"
        }
    }

    private fun signedIn(repository: SubshellRepository) = SubshellState(
        repository = repository,
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
    )

    @Test
    fun aConfirmedPostVoteAndSaveAreStoredAndCommentAndInFlightVotesAreNot() {
        val repository = RecordingRepository()
        val state = signedIn(repository)

        state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SENDING, account.stableId, "t3_sending", 1))
        state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SUCCEEDED, account.stableId, "t3_post", 1))
        state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SUCCEEDED, account.stableId, "t1_comment", -1))
        state.synchronizePostSaveState(PostSaveUiState(PostSavePhase.SUCCEEDED, account.stableId, "t3_post", saved = true))

        assertEquals(
            listOf("vote reddit:me t3_post 1", "save reddit:me t3_post true"),
            repository.stored,
        )
    }

    @Test
    fun aVoteSettledWhileAnotherStateWasShownIsStoredToo() {
        val repository = RecordingRepository()
        val state = signedIn(repository)
        state.synchronizePostVoteState(PostVoteUiState(PostVotePhase.SENDING, account.stableId, "t3_early", 1))

        // A later vote's state carries the earlier vote's outcome (VOTE02).
        state.synchronizePostVoteState(
            PostVoteUiState(
                phase = PostVotePhase.SENDING,
                accountStableId = account.stableId,
                postFullname = "t3_later",
                direction = -1,
                settlements = listOf(PostVoteSettlement(1L, account.stableId, "t3_early", 1, succeeded = true)),
            ),
        )

        assertEquals(listOf("vote reddit:me t3_early 1"), repository.stored)
    }

    @Test
    fun theRepositoryMovesTheStoredScoreWithTheVoteForEitherIdSpelling() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "votessot01-repository.db"
        val repository = app.subshell.prototype.data.createPersistentSubshellRepositoryForTest(context, databaseName = name)
        val writer = RoomSubshellCacheStore(SubshellCacheDatabase.open(context, name))
        // Stored under the bare id; Reddit names it as a fullname.
        writer.writePostResult(
            RepositoryPageRequest(cursor = null, limit = 10, queryKey = RepositoryQueryKey("feed:any"), operation = RepositoryLoadOperation.REFRESH),
            RepositoryResult.Content(listOf(SampleData.posts.first().copy(id = "abc", score = 10, vote = -1))),
        )

        repository.storeSettledVote("reddit:me", "t3_abc", 1)
        // The repository's store thread runs in order: this read waits for the write.
        repository.cacheQueryMetadata(RepositoryQueryKey("feed:any"))

        val stored = requireNotNull(writer.post("abc"))
        assertEquals(1, stored.vote)
        assertEquals(12, stored.score)
        writer.close()
        repository.close()
    }

    @Test
    fun theStoredPostTakesTheVoteScoreAndSave() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cache = RoomSubshellCacheStore(database)
        val post = SampleData.posts.first().copy(id = "t3_abc", score = 10, vote = 0, saved = false)
        cache.writePostResult(
            RepositoryPageRequest(cursor = null, limit = 10, queryKey = RepositoryQueryKey("feed:any"), operation = RepositoryLoadOperation.REFRESH),
            RepositoryResult.Content(listOf(post)),
        )

        cache.updateStoredPostLater("reddit:me", "t3_abc") { stored -> stored.copy(score = stored.score + (1 - stored.vote), vote = 1) }
        cache.updateStoredPostLater("reddit:me", "abc") { stored -> stored.copy(saved = true) }
        cache.updateStoredPostLater("reddit:me", "t3_missing") { stored -> stored.copy(vote = 1) }

        // The store's own thread runs in order: this read comes after both writes.
        val stored = requireNotNull(cache.post("t3_abc"))
        assertEquals(1, stored.vote)
        assertEquals(11, stored.score)
        assertTrue(stored.saved)
        cache.close()
    }
}
