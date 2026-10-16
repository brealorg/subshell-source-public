package app.subshell.prototype.data

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSubshellRepositoryContractTest {
    @Test
    fun contentBoundaryExposesStableUniqueIdentities() {
        val repository = FakeSubshellRepository()
        val posts = repository.posts().itemsOrEmpty()
        val communities = repository.communities().itemsOrEmpty()
        val users = repository.users().itemsOrEmpty()
        val inbox = repository.inbox().itemsOrEmpty()
        val comments = repository.comments().itemsOrEmpty()

        assertTrue(posts.isNotEmpty())
        assertEquals(posts.size, posts.map(Post::id).distinct().size)
        assertEquals(communities.size, communities.map { it.id }.distinct().size)
        assertEquals(users.size, users.map { it.username.lowercase() }.distinct().size)
        assertEquals(inbox.size, inbox.map { it.id }.distinct().size)
        assertEquals(
            comments.flatMap { comment -> comment.flatten() }.size,
            comments.flatMap { comment -> comment.flatten() }.map(Comment::id).distinct().size,
        )
        assertTrue(repository.localUsername in repository.accounts().itemsOrEmpty())
    }

    @Test
    fun repositoryResultCoversLoadingRefreshingEmptyOfflineAndError() {
        val repository = FakeSubshellRepository()

        repository.mode = FakeRepositoryMode.LOADING
        assertTrue(repository.posts() is RepositoryResult.Loading)

        repository.mode = FakeRepositoryMode.REFRESHING
        val refreshing = repository.posts() as RepositoryResult.Content<List<Post>>
        assertTrue(refreshing.refreshing)
        assertTrue(refreshing.value.isNotEmpty())

        repository.mode = FakeRepositoryMode.EMPTY
        assertTrue(repository.posts() is RepositoryResult.Empty)

        repository.mode = FakeRepositoryMode.OFFLINE
        val offline = repository.posts() as RepositoryResult.Offline<List<Post>>
        assertTrue(offline.cached.orEmpty().isNotEmpty())

        repository.mode = FakeRepositoryMode.ERROR
        val error = repository.posts() as RepositoryResult.Error<List<Post>>
        assertEquals("Fake repository error", error.message)
        assertTrue(error.cached.orEmpty().isNotEmpty())
    }

    @Test
    fun cursorPagingIsStableAndNonOverlapping() {
        val repository = FakeSubshellRepository()
        val first = repository.posts(RepositoryPageRequest(limit = 5))
            as RepositoryResult.Content<List<Post>>
        val second = repository.posts(
            RepositoryPageRequest(cursor = first.nextCursor, limit = 5),
        ) as RepositoryResult.Content<List<Post>>

        assertEquals(5, first.value.size)
        assertEquals("5", first.nextCursor)
        assertEquals(5, second.value.size)
        assertTrue(first.value.map(Post::id).intersect(second.value.map(Post::id).toSet()).isEmpty())
    }

    @Test
    fun stateReadsAndWritesThroughInjectedFakeBackend() {
        val repository = FakeSubshellRepository()
        val state = SubshellState(
            repository = repository,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        val target = state.posts.first { post ->
            post.id !in state.savedPostIds &&
                post.id !in repository.defaultUpvotedPostIds
        }

        state.toggleSaved(target.id)
        state.vote(target.id, 1)
        val poll = state.posts.first { post -> post.kind == PostKind.POLL }
        state.votePoll(poll.id, requireNotNull(poll.poll).options.first().id)

        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(
                community = state.communities.first().name,
                title = "Repository boundary post",
                body = "Created by the local fake backend.",
            ),
        )
        val created = state.publishPostComposerDraft()

        assertNotNull(created)
        assertTrue(created in state.posts)
        assertEquals(created?.id, state.selectedPost?.id)

        val snapshot = repository.mutationSnapshot()
        assertEquals(true, snapshot.savedByPost[target.id])
        assertEquals(1, snapshot.votesByPost[target.id])
        assertEquals(created?.id, snapshot.createdPostIds.single())
        assertEquals(
            requireNotNull(poll.poll).options.first().id,
            snapshot.pollVotesByPost[poll.id],
        )
    }

    @Test
    fun mutationBoundaryRejectsUnknownPostsAndPollOptions() {
        val repository = FakeSubshellRepository()
        val poll = repository.posts().itemsOrEmpty().first { post -> post.kind == PostKind.POLL }

        assertTrue(repository.recordSaved("missing-post", true) is RepositoryResult.Error<*>)
        assertTrue(repository.recordVote("missing-post", 1) is RepositoryResult.Error<*>)
        assertTrue(repository.recordPollVote("missing-post", "missing-option") is RepositoryResult.Error<*>)
        assertTrue(repository.recordPollVote(poll.id, "missing-option") is RepositoryResult.Error<*>)
    }

    @Test
    fun localCommentMutationAppearsInPostThreadOnly() {
        val repository = FakeSubshellRepository()
        val post = repository.posts().itemsOrEmpty().first()
        val comment = Comment(
            id = "local-repository-comment",
            author = repository.localUsername,
            age = "now",
            body = "Repository-local comment.",
            score = 1,
            depth = 0,
            accent = 0xFFFF8A50,
        )

        assertTrue(repository.addComment(post.id, comment) is RepositoryResult.Content<*>)
        val thread = repository.commentsForPost(post.id).itemsOrEmpty()
        assertTrue(thread.any { candidate -> candidate.id == comment.id })
        assertFalse(
            repository.commentsForPost("missing-post").itemsOrEmpty()
                .any { candidate -> candidate.id == comment.id },
        )
    }

    private fun Comment.flatten(): List<Comment> =
        listOf(this) + children.flatMap { child -> child.flatten() }
}
