package app.subshell.prototype.data

import app.subshell.prototype.data.cache.RestoredLocalMutation
import app.subshell.prototype.data.cache.SubshellCacheCodec
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post

// SUBSHELL_DATA03_DEPENDENCY_AWARE_MUTATION_REPLAY_V1
internal enum class LocalMutationKind(
    val storageValue: String,
    val dependencyPriority: Int,
) {
    CREATED_POST("CREATED_POST", 0),
    COMMENT("COMMENT", 1),
    SAVED("SAVED", 2),
    VOTE("VOTE", 2),
    POLL("POLL", 2),
    ;

    companion object {
        fun fromStorageValue(value: String): LocalMutationKind? =
            entries.firstOrNull { kind -> kind.storageValue == value }
    }
}

internal enum class LocalMutationReplayStatus {
    APPLIED,
    ALREADY_APPLIED,
    DEFERRED,
    FAILED,
}

internal data class LocalMutationReplayOutcome(
    val mutationKey: String,
    val kind: String,
    val subjectId: String,
    val status: LocalMutationReplayStatus,
    val message: String? = null,
)

internal data class LocalMutationReplaySummary(
    val outcomes: List<LocalMutationReplayOutcome>,
) {
    val appliedCount: Int
        get() = count(LocalMutationReplayStatus.APPLIED)

    val alreadyAppliedCount: Int
        get() = count(LocalMutationReplayStatus.ALREADY_APPLIED)

    val deferredCount: Int
        get() = count(LocalMutationReplayStatus.DEFERRED)

    val failedCount: Int
        get() = count(LocalMutationReplayStatus.FAILED)

    val successfulCount: Int
        get() = appliedCount + alreadyAppliedCount

    private fun count(status: LocalMutationReplayStatus): Int =
        outcomes.count { outcome -> outcome.status == status }

    companion object {
        val Empty = LocalMutationReplaySummary(emptyList())
    }
}

internal object LocalMutationReplayPolicy {
    fun ordered(
        mutations: List<RestoredLocalMutation>,
    ): List<RestoredLocalMutation> =
        mutations.sortedWith(
            compareBy<RestoredLocalMutation>(
                { mutation ->
                    LocalMutationKind.fromStorageValue(mutation.kind)
                        ?.dependencyPriority
                        ?: Int.MAX_VALUE
                },
                RestoredLocalMutation::updatedAtEpochMs,
                RestoredLocalMutation::mutationKey,
            ),
        )
}

internal class LocalMutationReplayer(
    private val delegate: SubshellRepository,
) {
    fun replay(
        mutations: List<RestoredLocalMutation>,
    ): LocalMutationReplaySummary =
        LocalMutationReplaySummary(
            outcomes = LocalMutationReplayPolicy.ordered(mutations).map(::replayOne),
        )

    private fun replayOne(
        mutation: RestoredLocalMutation,
    ): LocalMutationReplayOutcome = try {
        when (LocalMutationKind.fromStorageValue(mutation.kind)) {
            LocalMutationKind.CREATED_POST -> replayCreatedPost(mutation)
            LocalMutationKind.COMMENT -> replayComment(mutation)
            LocalMutationKind.SAVED -> replaySaved(mutation)
            LocalMutationKind.VOTE -> replayVote(mutation)
            LocalMutationKind.POLL -> replayPollVote(mutation)
            null -> mutation.outcome(
                status = LocalMutationReplayStatus.FAILED,
                message = "Unknown local mutation kind: ${mutation.kind}",
            )
        }
    } catch (_: IllegalArgumentException) {
        mutation.outcome(
            status = LocalMutationReplayStatus.FAILED,
            message = "Invalid local mutation payload",
        )
    } catch (_: IllegalStateException) {
        mutation.outcome(
            status = LocalMutationReplayStatus.FAILED,
            message = "Invalid local mutation state",
        )
    }

    private fun replayCreatedPost(
        mutation: RestoredLocalMutation,
    ): LocalMutationReplayOutcome {
        val post = SubshellCacheCodec.decodePost(mutation.payload)
        if (delegate.post(post.id).valueOrNull() != null) {
            return mutation.outcome(LocalMutationReplayStatus.ALREADY_APPLIED)
        }
        return mutation.classify(delegate.createPost(post))
    }

    private fun replayComment(
        mutation: RestoredLocalMutation,
    ): LocalMutationReplayOutcome {
        val fields = SubshellCacheCodec.decodeMutationFields(mutation.payload, 2)
        val postId = requireNotNull(fields[0]) { "Comment mutation is missing its parent post ID" }
        val commentPayload = requireNotNull(fields[1]) { "Comment mutation is missing its payload" }
        val comment = SubshellCacheCodec.decodeComment(commentPayload)

        if (delegate.post(postId).valueOrNull() == null) {
            return mutation.outcome(
                status = LocalMutationReplayStatus.DEFERRED,
                message = "Parent post is unavailable: $postId",
            )
        }

        val existing = delegate.commentsForPost(postId).itemsOrEmpty()
        if (existing.any { candidate -> candidate.id == comment.id }) {
            return mutation.outcome(LocalMutationReplayStatus.ALREADY_APPLIED)
        }
        return mutation.classify(delegate.addComment(postId, comment))
    }

    private fun replaySaved(
        mutation: RestoredLocalMutation,
    ): LocalMutationReplayOutcome {
        if (delegate.post(mutation.subjectId).valueOrNull() == null) {
            return mutation.missingParent()
        }
        val saved = mutation.payload.toBooleanStrictOrNull()
            ?: return mutation.outcome(
                status = LocalMutationReplayStatus.FAILED,
                message = "Saved mutation payload must be true or false",
            )
        return mutation.classify(delegate.recordSaved(mutation.subjectId, saved))
    }

    private fun replayVote(
        mutation: RestoredLocalMutation,
    ): LocalMutationReplayOutcome {
        if (delegate.post(mutation.subjectId).valueOrNull() == null) {
            return mutation.missingParent()
        }
        val direction = mutation.payload.toIntOrNull()
            ?.takeIf { value -> value in -1..1 }
            ?: return mutation.outcome(
                status = LocalMutationReplayStatus.FAILED,
                message = "Vote mutation payload must be -1, 0 or 1",
            )
        return mutation.classify(delegate.recordVote(mutation.subjectId, direction))
    }

    private fun replayPollVote(
        mutation: RestoredLocalMutation,
    ): LocalMutationReplayOutcome {
        if (delegate.post(mutation.subjectId).valueOrNull() == null) {
            return mutation.missingParent()
        }
        return mutation.classify(
            delegate.recordPollVote(
                mutation.subjectId,
                mutation.payload.takeIf(String::isNotEmpty),
            ),
        )
    }

    private fun RestoredLocalMutation.missingParent(): LocalMutationReplayOutcome =
        outcome(
            status = LocalMutationReplayStatus.DEFERRED,
            message = "Parent post is unavailable: $subjectId",
        )

    private fun <T> RestoredLocalMutation.classify(
        result: RepositoryResult<T>,
    ): LocalMutationReplayOutcome = when (result) {
        is RepositoryResult.Content ->
            outcome(LocalMutationReplayStatus.APPLIED)
        RepositoryResult.Loading ->
            outcome(
                status = LocalMutationReplayStatus.DEFERRED,
                message = "Delegate is still loading",
            )
        is RepositoryResult.Offline ->
            outcome(
                status = LocalMutationReplayStatus.DEFERRED,
                message = result.message,
            )
        is RepositoryResult.Empty ->
            outcome(
                status = LocalMutationReplayStatus.FAILED,
                message = result.reason ?: "Delegate returned empty",
            )
        is RepositoryResult.Error ->
            outcome(
                status = LocalMutationReplayStatus.FAILED,
                message = result.message,
            )
    }

    private fun RestoredLocalMutation.outcome(
        status: LocalMutationReplayStatus,
        message: String? = null,
    ): LocalMutationReplayOutcome =
        LocalMutationReplayOutcome(
            mutationKey = mutationKey,
            kind = kind,
            subjectId = subjectId,
            status = status,
            message = message,
        )
}
