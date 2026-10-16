package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentEditFailureKind
import app.subshell.prototype.data.account.CommentEditPhase
import app.subshell.prototype.data.account.CommentEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditor
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * H1: after an unknown edit outcome, Check again only reads (verify), and a
 * confirmed edit completes like a normal save.
 */
class H1CommentEditCoordinatorTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val request = CommentEditRequest(
        accountStableId = account.stableId,
        postId = "t3_post",
        commentFullname = "t1_abc",
        commentAuthor = "me",
        text = "new text",
        sort = RedditCommentSort.BEST,
    )

    private class ScriptedEditor(
        private val edits: List<AuthenticatedCommentEditResult>,
        private val verifies: List<AuthenticatedCommentEditResult>,
    ) : AuthenticatedCommentEditor {
        var editCalls = 0
        var verifyCalls = 0
        override suspend fun edit(request: AuthenticatedCommentEditRequest) = edits[editCalls++]
        override suspend fun verify(request: AuthenticatedCommentEditRequest) = verifies[verifyCalls++]
    }

    private val unknown = AuthenticatedCommentEditResult.Failed(AuthenticatedCommentEditFailureKind.OUTCOME_UNKNOWN)
    private val edited = AuthenticatedCommentEditResult.Edited(
        accountStableId = account.stableId,
        commentFullname = "t1_abc",
        text = "new text",
        metadata = NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null),
    )

    private fun coordinator(editor: ScriptedEditor, onEdited: (CommentEditRequest) -> Unit = {}) = CommentEditCoordinator(
        capability = null,
        editor = editor,
        accountDirectory = object : AccountSessionDirectory {
            override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
            override fun selectAccount(stableId: String) = snapshot()
        },
        clientMode = null,
        onCommentEdited = onEdited,
    )

    @Test
    fun checkAgainReadsAndCompletesTheEditWithoutSendingIt() = runBlocking {
        val editor = ScriptedEditor(edits = listOf(unknown), verifies = listOf(edited))
        val applied = mutableListOf<CommentEditRequest>()
        val coordinator = coordinator(editor) { applied += it }

        coordinator.edit(request)
        assertEquals(CommentEditFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)

        coordinator.checkAgain()

        assertEquals(CommentEditPhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals(1, editor.editCalls)
        assertEquals(1, editor.verifyCalls)
        assertEquals(listOf(request), applied)
    }

    @Test
    fun checkAgainThatStillCannotTellStaysUnknown() = runBlocking {
        val editor = ScriptedEditor(edits = listOf(unknown), verifies = listOf(unknown))
        val coordinator = coordinator(editor)

        coordinator.edit(request)
        coordinator.checkAgain()

        assertEquals(CommentEditFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)
        assertEquals(1, editor.editCalls)
    }

    @Test
    fun checkAgainDoesNothingAfterAnOrdinaryFailure() = runBlocking {
        val editor = ScriptedEditor(
            edits = listOf(AuthenticatedCommentEditResult.Failed(AuthenticatedCommentEditFailureKind.HTTP)),
            verifies = emptyList(),
        )
        val coordinator = coordinator(editor)

        coordinator.edit(request)
        coordinator.checkAgain()

        assertEquals(CommentEditFailureKind.HTTP, coordinator.uiState.value.failureKind)
        assertEquals(0, editor.verifyCalls)
    }
}
