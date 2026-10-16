package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostEditPhase
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditor
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** POSTEDIT01: a successful edit carries the text Reddit was sent. */
class PostEdit01SavedTextTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun aSucceededEditCarriesTheSentText() = runBlocking {
        val editor = object : AuthenticatedPostEditor {
            override suspend fun edit(request: AuthenticatedPostEditRequest) =
                AuthenticatedPostEditResult.Edited(
                    accountStableId = request.accountStableId,
                    postFullname = request.postFullname,
                    text = request.text,
                    metadata = NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null),
                )
            override suspend fun verify(request: AuthenticatedPostEditRequest) = error("not used")
        }
        val coordinator = PostEditCoordinator(
            capability = null,
            editor = editor,
            accountDirectory = object : AccountSessionDirectory {
                override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
                override fun selectAccount(stableId: String) = snapshot()
            },
            clientMode = null,
        )

        coordinator.edit(
            PostEditRequest(
                accountStableId = account.stableId,
                postFullname = "t3_abc",
                postAuthor = "me",
                text = "sent text",
            ),
        )

        assertEquals(PostEditPhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals("sent text", coordinator.uiState.value.savedText)
    }
}
