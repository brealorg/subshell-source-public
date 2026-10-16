package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostEditFailureKind
import app.subshell.prototype.data.account.PostEditPhase
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditor
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * H2: after an unknown edit outcome, Check again only reads (verify), and a
 * confirmed edit completes like a normal save.
 */
class H2PostEditCoordinatorTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val request = PostEditRequest(
        accountStableId = account.stableId,
        postFullname = "t3_abc",
        postAuthor = "me",
        text = "new text",
    )

    private class ScriptedEditor(
        private val edits: List<AuthenticatedPostEditResult>,
        private val verifies: List<AuthenticatedPostEditResult>,
    ) : AuthenticatedPostEditor {
        var editCalls = 0
        var verifyCalls = 0
        override suspend fun edit(request: AuthenticatedPostEditRequest) = edits[editCalls++]
        override suspend fun verify(request: AuthenticatedPostEditRequest) = verifies[verifyCalls++]
    }

    private val unknown = AuthenticatedPostEditResult.Failed(AuthenticatedPostEditFailureKind.OUTCOME_UNKNOWN)
    private val edited = AuthenticatedPostEditResult.Edited(
        accountStableId = account.stableId,
        postFullname = "t3_abc",
        text = "new text",
        metadata = NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null),
    )

    private fun coordinator(editor: ScriptedEditor) = PostEditCoordinator(
        capability = null,
        editor = editor,
        accountDirectory = object : AccountSessionDirectory {
            override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
            override fun selectAccount(stableId: String) = snapshot()
        },
        clientMode = null,
    )

    @Test
    fun checkAgainReadsAndCompletesTheEditWithoutSendingIt() = runBlocking {
        val editor = ScriptedEditor(edits = listOf(unknown), verifies = listOf(edited))
        val coordinator = coordinator(editor)

        coordinator.edit(request)
        assertEquals(PostEditFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)

        coordinator.checkAgain()

        assertEquals(PostEditPhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals(1, editor.editCalls)
        assertEquals(1, editor.verifyCalls)
    }

    @Test
    fun checkAgainThatStillCannotTellStaysUnknown() = runBlocking {
        val editor = ScriptedEditor(edits = listOf(unknown), verifies = listOf(unknown))
        val coordinator = coordinator(editor)

        coordinator.edit(request)
        coordinator.checkAgain()

        assertEquals(PostEditFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)
        assertEquals(1, editor.editCalls)
    }

    @Test
    fun checkAgainDoesNothingAfterAnOrdinaryFailure() = runBlocking {
        val editor = ScriptedEditor(
            edits = listOf(AuthenticatedPostEditResult.Failed(AuthenticatedPostEditFailureKind.HTTP)),
            verifies = emptyList(),
        )
        val coordinator = coordinator(editor)

        coordinator.edit(request)
        coordinator.checkAgain()

        assertEquals(PostEditFailureKind.HTTP, coordinator.uiState.value.failureKind)
        assertEquals(0, editor.verifyCalls)
    }
}
