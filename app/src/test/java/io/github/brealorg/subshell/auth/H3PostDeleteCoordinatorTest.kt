package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.PostDeleteFailureKind
import io.github.brealorg.subshell.data.account.PostDeletePhase
import io.github.brealorg.subshell.data.account.PostDeleteRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleteFailureKind
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleteRequest
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleteResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleter
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * H3: after an unknown delete outcome, Check again only reads (verify), and
 * a confirmed delete completes like a normal delete.
 */
class H3PostDeleteCoordinatorTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val request = PostDeleteRequest(
        accountStableId = account.stableId,
        postFullname = "t3_abc",
        postAuthor = "me",
    )

    private class ScriptedDeleter(
        private val deletes: List<AuthenticatedPostDeleteResult>,
        private val verifies: List<AuthenticatedPostDeleteResult>,
    ) : AuthenticatedPostDeleter {
        var deleteCalls = 0
        var verifyCalls = 0
        override suspend fun delete(request: AuthenticatedPostDeleteRequest) = deletes[deleteCalls++]
        override suspend fun verify(request: AuthenticatedPostDeleteRequest) = verifies[verifyCalls++]
    }

    private val unknown = AuthenticatedPostDeleteResult.Failed(AuthenticatedPostDeleteFailureKind.OUTCOME_UNKNOWN)
    private val deleted = AuthenticatedPostDeleteResult.Deleted(
        account.stableId,
        "t3_abc",
        NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null),
    )

    private fun coordinator(
        deleter: ScriptedDeleter,
        remover: io.github.brealorg.subshell.data.cache.DeletedPostCacheRemover? = null,
    ) = PostDeleteCoordinator(
        capability = null,
        deleter = deleter,
        accountDirectory = object : AccountSessionDirectory {
            override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
            override fun selectAccount(stableId: String) = snapshot()
        },
        clientMode = null,
        deletedPostCacheRemover = remover,
        cacheDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    // OCB102_DELETED_POST_LEAVES_THE_CACHE_V1
    @Test
    fun aConfirmedDeleteRemovesThePostFromTheCache() = runBlocking {
        val removed = mutableListOf<String>()
        val coordinator = coordinator(
            ScriptedDeleter(deletes = listOf(deleted), verifies = emptyList()),
            remover = { postId -> removed += postId },
        )

        coordinator.delete(request)

        assertEquals(listOf("t3_abc"), removed)
    }

    @Test
    fun checkAgainReadsAndCompletesTheDeleteWithoutSendingIt() = runBlocking {
        val deleter = ScriptedDeleter(deletes = listOf(unknown), verifies = listOf(deleted))
        val coordinator = coordinator(deleter)

        coordinator.delete(request)
        assertEquals(PostDeleteFailureKind.OUTCOME_UNKNOWN, coordinator.uiState.value.failureKind)

        coordinator.checkAgain()

        assertEquals(PostDeletePhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals(1, deleter.deleteCalls)
        assertEquals(1, deleter.verifyCalls)
    }

    @Test
    fun checkAgainDoesNothingAfterAnOrdinaryFailure() = runBlocking {
        val deleter = ScriptedDeleter(
            deletes = listOf(AuthenticatedPostDeleteResult.Failed(AuthenticatedPostDeleteFailureKind.HTTP)),
            verifies = emptyList(),
        )
        val coordinator = coordinator(deleter)

        coordinator.delete(request)
        coordinator.checkAgain()

        assertEquals(PostDeleteFailureKind.HTTP, coordinator.uiState.value.failureKind)
        assertEquals(0, deleter.verifyCalls)
    }
}
