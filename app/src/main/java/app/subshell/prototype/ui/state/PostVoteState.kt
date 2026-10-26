package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateMapOf
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteSettlement
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.normalizeCommentVoteFullname
import app.subshell.prototype.data.account.normalizePostVoteFullname
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.nextPostVoteDirection
import app.subshell.prototype.ui.postScoreForEffectiveVote
import app.subshell.prototype.ui.voteTargetControlEnabled

/**
 * Post and comment votes: the local vote maps of an unauthenticated
 * account, and for an authenticated one the optimistic override, rollback
 * and in-flight marker per target plus the requester MainActivity sets.
 * [synchronizePostVoteState] reconciles the vote flow's phases (success
 * keeps, failure rolls back, another account clears).
 * Extracted verbatim from SubshellState, which holds one instance and
 * forwards its public API to it. The account post actions (capture,
 * replace on account switch, persistence) stay in SubshellState and read
 * [votes] through its forwarder; account switch and session sync call
 * [clearAuthenticatedPostVoteState]. Behaviour is unchanged.
 */
internal class PostVoteState(
    private val repository: SubshellRepository,
    private val persistActiveAccountPostActions: () -> Unit,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountIsAuthenticatedProvider: () -> Boolean,
    private val localMutationsAllowedProvider: () -> Boolean,
) {
    private val activeAccountId: String
        get() = activeAccountIdProvider()
    private val activeAccountIsAuthenticated: Boolean
        get() = activeAccountIsAuthenticatedProvider()
    private val localMutationsAllowed: Boolean
        get() = localMutationsAllowedProvider()
    val votes = mutableStateMapOf<String, Int>()
    private val commentVotes = mutableStateMapOf<String, Int>()
    private val authenticatedPostVoteOverrides = mutableStateMapOf<String, Int>()
    private val authenticatedPostVoteRollback = mutableMapOf<String, Int?>()
    private val authenticatedPostVotesInFlight = mutableStateMapOf<String, Unit>()
    private var lastAppliedSettlementSequence = 0L
    private var requestAuthenticatedPostVote: (PostVoteRequest) -> Unit = {}

    fun postVoteControlEnabled(post: Post): Boolean =
        localMutationsAllowed || runCatching { normalizePostVoteFullname(post.id) }
            .getOrNull()
            ?.let { target -> voteTargetControlEnabled(authenticatedPostVotesInFlight.keys, target) }
            ?: false

    fun commentVoteControlEnabled(comment: Comment): Boolean =
        localMutationsAllowed || runCatching { normalizeCommentVoteFullname(comment.id) }
            .getOrNull()
            ?.let { target -> voteTargetControlEnabled(authenticatedPostVotesInFlight.keys, target) }
            ?: false

    fun setAuthenticatedPostVoteRequester(
        requester: (PostVoteRequest) -> Unit,
    ) {
        requestAuthenticatedPostVote = requester
    }

    fun effectivePostVote(post: Post): Int =
        if (activeAccountIsAuthenticated) {
            runCatching { normalizePostVoteFullname(post.id) }
                .getOrNull()
                ?.let(authenticatedPostVoteOverrides::get)
                ?: post.vote
        } else {
            votes[post.id] ?: post.vote
        }

    fun effectivePostScore(post: Post): Int = postScoreForEffectiveVote(
        serverScore = post.score,
        serverVote = post.vote,
        effectiveVote = effectivePostVote(post),
    )

    fun effectiveCommentVote(comment: Comment): Int =
        if (activeAccountIsAuthenticated) {
            runCatching { normalizeCommentVoteFullname(comment.id) }
                .getOrNull()
                ?.let(authenticatedPostVoteOverrides::get)
                ?: comment.vote
        } else {
            commentVotes[comment.id] ?: comment.vote
        }

    fun effectiveCommentScore(comment: Comment): Int = postScoreForEffectiveVote(
        serverScore = comment.score,
        serverVote = comment.vote,
        effectiveVote = effectiveCommentVote(comment),
    )

    fun vote(postId: String, direction: Int) {
        if (!localMutationsAllowed) return
        val normalizedPostId = postId.trim()
        if (normalizedPostId.isEmpty() || (direction != -1 && direction != 1)) return
        val appliedDirection = if (votes[normalizedPostId] == direction) {
            votes.remove(normalizedPostId)
            0
        } else {
            votes[normalizedPostId] = direction
            direction
        }
        repository.recordVote(normalizedPostId, appliedDirection)
        persistActiveAccountPostActions()
    }

    fun vote(post: Post, direction: Int) {
        if (!activeAccountIsAuthenticated) {
            vote(post.id, direction)
            return
        }
        if (direction != -1 && direction != 1) return
        val postFullname = runCatching {
            normalizePostVoteFullname(post.id)
        }.getOrNull() ?: return
        if (postFullname in authenticatedPostVotesInFlight) return
        val currentVote = effectivePostVote(post)
        val targetVote = nextPostVoteDirection(currentVote, direction)
        authenticatedPostVoteRollback[postFullname] =
            authenticatedPostVoteOverrides[postFullname]
        authenticatedPostVoteOverrides[postFullname] = targetVote
        authenticatedPostVotesInFlight[postFullname] = Unit
        requestAuthenticatedPostVote(
            PostVoteRequest(
                accountStableId = activeAccountId,
                postFullname = postFullname,
                direction = targetVote,
            ),
        )
    }

    fun vote(comment: Comment, direction: Int) {
        if (direction != -1 && direction != 1) return
        if (!activeAccountIsAuthenticated) {
            val normalizedCommentId = comment.id.trim()
            if (normalizedCommentId.isEmpty()) return
            if (commentVotes[normalizedCommentId] == direction) {
                commentVotes.remove(normalizedCommentId)
            } else {
                commentVotes[normalizedCommentId] = direction
            }
            return
        }
        val commentFullname = runCatching {
            normalizeCommentVoteFullname(comment.id)
        }.getOrNull() ?: return
        if (commentFullname in authenticatedPostVotesInFlight) return
        val currentVote = effectiveCommentVote(comment)
        val targetVote = nextPostVoteDirection(currentVote, direction)
        authenticatedPostVoteRollback[commentFullname] =
            authenticatedPostVoteOverrides[commentFullname]
        authenticatedPostVoteOverrides[commentFullname] = targetVote
        authenticatedPostVotesInFlight[commentFullname] = Unit
        requestAuthenticatedPostVote(
            PostVoteRequest(
                accountStableId = activeAccountId,
                postFullname = commentFullname,
                direction = targetVote,
            ),
        )
    }

    fun synchronizePostVoteState(postVoteState: PostVoteUiState) {
        if (!activeAccountIsAuthenticated) {
            clearAuthenticatedPostVoteState()
            return
        }
        if (
            postVoteState.accountStableId != null &&
            postVoteState.accountStableId != activeAccountId
        ) {
            clearAuthenticatedPostVoteState()
            return
        }
        applySettlements(postVoteState.settlements)
        val postFullname = postVoteState.postFullname ?: return
        val direction = postVoteState.direction ?: return
        when (postVoteState.phase) {
            PostVotePhase.SENDING,
            PostVotePhase.AUTHORIZATION_REQUIRED,
            PostVotePhase.AUTHORIZING,
            PostVotePhase.AWAITING_CALLBACK,
            -> {
                if (!authenticatedPostVoteRollback.containsKey(postFullname)) {
                    authenticatedPostVoteRollback[postFullname] =
                        authenticatedPostVoteOverrides[postFullname]
                }
                authenticatedPostVoteOverrides[postFullname] = direction
                authenticatedPostVotesInFlight[postFullname] = Unit
            }

            PostVotePhase.SUCCEEDED -> {
                authenticatedPostVoteOverrides[postFullname] = direction
                authenticatedPostVoteRollback.remove(postFullname)
                authenticatedPostVotesInFlight.remove(postFullname)
                storeSettledVote(postFullname, direction)
            }

            PostVotePhase.FAILED -> {
                rollBackAuthenticatedPostVote(postFullname)
            }

            PostVotePhase.IDLE -> Unit
        }
    }

    // VOTE02_SETTLEMENTS_SURVIVE_CONFLATION_V1 (#60): the flow is read through
    // collectAsState, which keeps only the newest state, so the end of an
    // earlier vote arrives as a settlement. Applied before the current phase
    // (a retry of the same target then marks it in flight again), once each,
    // and only to a target of this account that is still in flight.
    private fun applySettlements(settlements: List<PostVoteSettlement>) {
        for (settlement in settlements) {
            if (settlement.sequence <= lastAppliedSettlementSequence) continue
            lastAppliedSettlementSequence = settlement.sequence
            val target = settlement.postFullname
            if (settlement.accountStableId != activeAccountId) continue
            if (target !in authenticatedPostVotesInFlight) continue
            if (settlement.succeeded) {
                authenticatedPostVoteOverrides[target] = settlement.direction
                authenticatedPostVoteRollback.remove(target)
                authenticatedPostVotesInFlight.remove(target)
                storeSettledVote(target, settlement.direction)
            } else {
                rollBackAuthenticatedPostVote(target)
            }
        }
    }

    // VOTESSOT01_SETTLED_ACTIONS_REACH_THE_STORE_V1 (#244): a post vote Reddit
    // confirmed goes to the stored post too (comment votes reach the stored
    // thread through the vote coordinator). The override stays until the
    // copies show the vote (releaseSettledOverrides).
    private fun storeSettledVote(target: String, direction: Int) {
        if (!target.startsWith("t3_")) return
        (repository as? app.subshell.prototype.data.SettledPostActions)
            ?.storeSettledVote(activeAccountId, target, direction)
    }

    private fun rollBackAuthenticatedPostVote(postFullname: String) {
        if (authenticatedPostVoteRollback.containsKey(postFullname)) {
            val previous = authenticatedPostVoteRollback.remove(postFullname)
            if (previous == null) {
                authenticatedPostVoteOverrides.remove(postFullname)
            } else {
                authenticatedPostVoteOverrides[postFullname] = previous
            }
        }
        authenticatedPostVotesInFlight.remove(postFullname)
    }

    // RECREATE01_SETTLED_VOTES_SURVIVE_RECREATION_V1 (#241): a settled vote whose
    // copies have not shown it yet lives only here (a post that is not stored,
    // a thread not open), so the settled ones ride in the process snapshot. A vote still in
    // flight is left out: the coordinator hands it back after the rebuild, and
    // without its rollback a later failure would keep a vote Reddit never took.
    internal fun settledVoteOverridesForSnapshot(): Map<String, Int> =
        authenticatedPostVoteOverrides.filterKeys { target -> target !in authenticatedPostVotesInFlight }

    internal fun restoreSettledVoteOverrides(overrides: Map<String, Int>) {
        if (!activeAccountIsAuthenticated) return
        overrides.forEach { (target, direction) ->
            if (direction in -1..1) authenticatedPostVoteOverrides[target] = direction
        }
    }

    /**
     * OPENPOSTSSOT01_SETTLED_OVERRIDES_GIVE_WAY_TO_THE_STORE_V1 (#244): a vote
     * Reddit took stands in for the copies only until they show it themselves
     * (the store wrote it and the feeds, the open post or the thread read it
     * again). [copiesOf] gives the vote of every copy of a target held now; a
     * settled override is dropped when there is one and all agree, so the
     * screen does not change, and the copies follow Reddit from then on. In
     * flight, or with no copy (not stored, nothing open), it stays.
     */
    fun releaseSettledOverrides(copiesOf: (target: String) -> List<Int>?) {
        if (!activeAccountIsAuthenticated) return
        authenticatedPostVoteOverrides.toMap().forEach { (target, direction) ->
            if (target in authenticatedPostVotesInFlight) return@forEach
            val copies = copiesOf(target) ?: return@forEach
            if (copies.isNotEmpty() && copies.all { vote -> vote == direction }) {
                authenticatedPostVoteOverrides.remove(target)
            }
        }
    }

    internal val settledOverrideTargets: Set<String>
        get() = authenticatedPostVoteOverrides.keys - authenticatedPostVotesInFlight.keys

    fun clearAuthenticatedPostVoteState() {
        authenticatedPostVoteOverrides.clear()
        authenticatedPostVoteRollback.clear()
        authenticatedPostVotesInFlight.clear()
    }
}
