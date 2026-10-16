package app.subshell.prototype.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.createFixtureFreeLocalSubshellRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE70_POST_LOOKUP_STAYS_IN_ITS_ACCOUNT_V1: a post restored or opened from
 * a link was read with the account-less lookup, which may return another
 * account's row (its vote and saved state). SubshellState asks for the
 * active account's copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue70SelectedPostAccountTest {
    private fun account(id: String) = AccountSessionSummary(
        stableId = "reddit:$id",
        username = id,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun post(vote: Int) = Post(
        id = "t3_x70",
        community = "test",
        author = "someone",
        age = "1h",
        title = "A post",
        score = 10 + vote,
        comments = 0,
        kind = PostKind.TEXT,
        vote = vote,
    )

    // The account-less lookup returns A's upvoted row; B's own copy has no vote.
    private val repository: SubshellRepository =
        object : SubshellRepository by createFixtureFreeLocalSubshellRepository() {
            override fun post(postId: String): RepositoryResult<Post> = RepositoryResult.Content(post(vote = 1))

            override fun postForAccount(postId: String, accountStableId: String?): RepositoryResult<Post> =
                RepositoryResult.Content(post(vote = if (accountStableId == "reddit:alice") 1 else 0))
        }

    private fun state(active: String) = SubshellState(
        repository = repository,
        initialAccountSessions = listOf(account("alice"), account("bob")),
        initialActiveAccountId = active,
        initialInteractionSideConfigured = true,
        initialSelectedPostId = "t3_x70",
    )

    @Test
    fun aRestoredPostIsTheActiveAccountsCopy() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())

        assertEquals(0, state("reddit:bob").selectedPost?.vote)
        assertEquals(1, state("reddit:alice").selectedPost?.vote)
    }

    @Test
    fun aPostOpenedFromALinkIsTheActiveAccountsCopy() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val bob = state("reddit:bob")

        assertTrue(bob.openRedditTarget(RedditTarget.Post("x70")))

        assertEquals(0, bob.selectedPost?.vote)
    }
}
