package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMPOSER06 (ui review round 3 P8): the Drafts lists show the active
 * account's drafts; drafts saved before drafts carried an account show for
 * every account.
 */
class Composer06DraftsPerAccountTest {
    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_$name",
        username = name,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val first = account("first")
    private val second = account("second")

    private fun draft(id: String, owner: AccountSessionSummary?, updatedAt: Long) = PostComposerDraft(
        id = id,
        accountStableId = owner?.stableId,
        community = "subshell",
        title = "Draft $id",
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun eachAccountSeesItsOwnDraftsAndOwnerlessOnes() {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(first, second),
            initialActiveAccountId = first.stableId,
            initialPostComposerDrafts = listOf(
                draft("a", first, 3L),
                draft("b", second, 2L),
                draft("legacy", null, 1L),
            ),
        )

        assertEquals(listOf("a", "legacy"), state.activeAccountPostComposerDrafts.map { it.id })

        state.switchAccount(second.stableId)
        assertEquals(listOf("b", "legacy"), state.activeAccountPostComposerDrafts.map { it.id })

        // Every draft is still kept; only the lists are per account.
        assertEquals(listOf("a", "b", "legacy"), state.postComposerDrafts.map { it.id })
    }
}
