package app.subshell.prototype.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val DATA01_LOG_TAG = "subshell-data01"

@RunWith(AndroidJUnit4::class)
class DataRepositoryInstrumentedTest {
    @Test
    fun injectedFakeRepositoryOwnsReadsAndLocalMutations() {
        val repository = FakeSubshellRepository()
        val state = SubshellState(
            repository = repository,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        val target = state.posts.first()
        state.toggleSaved(target.id)
        state.vote(target.id, 1)
        val comment = Comment(
            id = "data01-runtime-comment",
            author = repository.localUsername,
            age = "now",
            body = "Local repository comment mutation.",
            score = 1,
            depth = 0,
            accent = 0xFFFF8A50,
        )
        assertTrue(repository.addComment(target.id, comment) is RepositoryResult.Content<*>)

        val poll = state.posts.first { post -> post.kind == PostKind.POLL }
        val optionId = requireNotNull(poll.poll).options.first().id
        state.votePoll(poll.id, optionId)

        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(
                community = state.communities.first().name,
                title = "DATA-01 runtime post",
                body = "Local repository mutation.",
            ),
        )
        val created = state.publishPostComposerDraft()
        val snapshot = repository.mutationSnapshot()

        assertNotNull(created)
        assertTrue(created in state.posts)
        assertEquals(true, snapshot.savedByPost[target.id])
        assertEquals(1, snapshot.votesByPost[target.id])
        assertEquals(optionId, snapshot.pollVotesByPost[poll.id])
        assertEquals(created?.id, snapshot.createdPostIds.single())
        assertEquals(listOf(comment.id), snapshot.commentIdsByPost[target.id])
        assertTrue(repository.commentsForPost(target.id).itemsOrEmpty().any { it.id == comment.id })

        Log.i(
            DATA01_LOG_TAG,
            "DATA01_REPOSITORY_BOUNDARY=true " +
                "POST_COUNT=${state.posts.size} " +
                "COMMUNITY_COUNT=${state.communities.size} " +
                "MUTATION_COUNT=${snapshot.savedByPost.size + snapshot.votesByPost.size + snapshot.pollVotesByPost.size + snapshot.createdPostIds.size + snapshot.commentIdsByPost.values.sumOf(List<String>::size)}",
        )
        Log.i(DATA01_LOG_TAG, "RESULT=SUBSHELL_DATA01_REPOSITORY_RUNTIME_PASS")
    }
}
