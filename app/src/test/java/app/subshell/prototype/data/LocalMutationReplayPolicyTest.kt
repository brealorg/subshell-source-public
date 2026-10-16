package app.subshell.prototype.data

import app.subshell.prototype.data.cache.RestoredLocalMutation
import app.subshell.prototype.data.cache.SubshellCacheCodec
import app.subshell.prototype.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMutationReplayPolicyTest {
    @Test
    fun dependencyPriorityWinsOverTimestampAndShuffledStorageOrder() {
        val backend = FakeSubshellRepository(initialMode = FakeRepositoryMode.OFFLINE)
        val createdPost = SampleData.posts.first().copy(id = CREATED_POST_ID)
        val comment = localComment()
        val mutations = listOf(
            commentMutation(
                postId = createdPost.id,
                comment = comment,
                updatedAtEpochMs = 100L,
            ),
            savedMutation(
                postId = createdPost.id,
                updatedAtEpochMs = 50L,
            ),
            createdPostMutation(
                post = createdPost,
                updatedAtEpochMs = 500L,
            ),
        )

        val ordered = LocalMutationReplayPolicy.ordered(mutations)
        assertEquals(
            listOf(
                "created-post:$CREATED_POST_ID",
                "comment:$CREATED_POST_ID:$CREATED_COMMENT_ID",
                "saved:$CREATED_POST_ID",
            ),
            ordered.map(RestoredLocalMutation::mutationKey),
        )

        val summary = LocalMutationReplayer(backend).replay(mutations)
        assertEquals(3, summary.appliedCount)
        assertEquals(0, summary.deferredCount)
        assertEquals(0, summary.failedCount)
        assertEquals(
            listOf(
                LocalMutationReplayStatus.APPLIED,
                LocalMutationReplayStatus.APPLIED,
                LocalMutationReplayStatus.APPLIED,
            ),
            summary.outcomes.map(LocalMutationReplayOutcome::status),
        )
        assertTrue(CREATED_POST_ID in backend.mutationSnapshot().createdPostIds)
        assertTrue(
            CREATED_COMMENT_ID in
                backend.mutationSnapshot().commentIdsByPost[CREATED_POST_ID].orEmpty(),
        )
    }

    @Test
    fun repeatedReplayIsIdempotentAndReportsAlreadyAppliedObjects() {
        val backend = FakeSubshellRepository(initialMode = FakeRepositoryMode.OFFLINE)
        val createdPost = SampleData.posts.first().copy(id = CREATED_POST_ID)
        val mutations = listOf(
            commentMutation(createdPost.id, localComment(), updatedAtEpochMs = 10L),
            createdPostMutation(createdPost, updatedAtEpochMs = 10L),
            savedMutation(createdPost.id, updatedAtEpochMs = 10L),
        )
        val replayer = LocalMutationReplayer(backend)

        val first = replayer.replay(mutations)
        val second = replayer.replay(mutations)

        assertEquals(3, first.appliedCount)
        assertEquals(1, second.appliedCount)
        assertEquals(2, second.alreadyAppliedCount)
        assertEquals(0, second.deferredCount)
        assertEquals(0, second.failedCount)
        assertEquals(
            1,
            backend.mutationSnapshot()
                .commentIdsByPost[CREATED_POST_ID]
                .orEmpty()
                .count { id -> id == CREATED_COMMENT_ID },
        )
        assertEquals(
            1,
            backend.mutationSnapshot()
                .createdPostIds
                .count { id -> id == CREATED_POST_ID },
        )
    }

    @Test
    fun deferredAndFailedMutationsAreReportedWithoutSilentDrops() {
        val backend = FakeSubshellRepository(initialMode = FakeRepositoryMode.OFFLINE)
        val orphanComment = commentMutation(
            postId = "missing-parent",
            comment = localComment(),
            updatedAtEpochMs = 1L,
        )
        val malformedVote = RestoredLocalMutation(
            mutationKey = "vote:${SampleData.posts.first().id}",
            kind = LocalMutationKind.VOTE.storageValue,
            subjectId = SampleData.posts.first().id,
            payload = "not-a-vote",
            updatedAtEpochMs = 2L,
        )
        val unknown = RestoredLocalMutation(
            mutationKey = "future:${SampleData.posts.first().id}",
            kind = "FUTURE_KIND",
            subjectId = SampleData.posts.first().id,
            payload = "",
            updatedAtEpochMs = 3L,
        )

        val summary = LocalMutationReplayer(backend).replay(
            listOf(unknown, malformedVote, orphanComment),
        )

        assertEquals(3, summary.outcomes.size)
        assertEquals(0, summary.appliedCount)
        assertEquals(0, summary.alreadyAppliedCount)
        assertEquals(1, summary.deferredCount)
        assertEquals(2, summary.failedCount)
        assertEquals(
            setOf(
                LocalMutationReplayStatus.DEFERRED,
                LocalMutationReplayStatus.FAILED,
            ),
            summary.outcomes.map(LocalMutationReplayOutcome::status).toSet(),
        )
        assertTrue(summary.outcomes.all { outcome -> !outcome.message.isNullOrBlank() })
    }

    private fun createdPostMutation(
        post: app.subshell.prototype.model.Post,
        updatedAtEpochMs: Long,
    ): RestoredLocalMutation =
        RestoredLocalMutation(
            mutationKey = "created-post:${post.id}",
            kind = LocalMutationKind.CREATED_POST.storageValue,
            subjectId = post.id,
            payload = SubshellCacheCodec.encodePost(post),
            updatedAtEpochMs = updatedAtEpochMs,
        )

    private fun commentMutation(
        postId: String,
        comment: Comment,
        updatedAtEpochMs: Long,
    ): RestoredLocalMutation =
        RestoredLocalMutation(
            mutationKey = "comment:$postId:${comment.id}",
            kind = LocalMutationKind.COMMENT.storageValue,
            subjectId = comment.id,
            payload = SubshellCacheCodec.encodeMutationFields(
                postId,
                SubshellCacheCodec.encodeComment(comment),
            ),
            updatedAtEpochMs = updatedAtEpochMs,
        )

    private fun savedMutation(
        postId: String,
        updatedAtEpochMs: Long,
    ): RestoredLocalMutation =
        RestoredLocalMutation(
            mutationKey = "saved:$postId",
            kind = LocalMutationKind.SAVED.storageValue,
            subjectId = postId,
            payload = true.toString(),
            updatedAtEpochMs = updatedAtEpochMs,
        )

    private fun localComment(): Comment =
        Comment(
            id = CREATED_COMMENT_ID,
            author = SampleData.LOCAL_USERNAME,
            age = "now",
            body = "Dependency-aware replay",
            score = 1,
            depth = 0,
            accent = 0xFFFF8A50,
            isOp = true,
        )

    companion object {
        private const val CREATED_POST_ID = "data03-replay-created-post"
        private const val CREATED_COMMENT_ID = "data03-replay-created-comment"
    }
}
