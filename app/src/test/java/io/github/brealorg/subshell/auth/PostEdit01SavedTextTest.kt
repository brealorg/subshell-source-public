package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.PostEditPhase
import io.github.brealorg.subshell.data.account.PostEditRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostEditRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostEditResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostEditor
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
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
