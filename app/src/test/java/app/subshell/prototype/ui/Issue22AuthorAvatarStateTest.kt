package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.state.MAX_AUTHOR_AVATARS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Issue22AuthorAvatarStateTest {
    @Test
    fun avatarsResolveInOneBatchAndKnownAuthorsAreNotRequestedAgain() = runBlocking {
        val repository = AvatarRepository()
        val state = authenticatedState(repository)

        assertNull(state.avatarUriForAuthor("alice"))
        state.ensureAuthorAvatars(mapOf("alice" to "t2_a", "bob" to "t2_b", "noavatar" to "t2_n", "unknown" to "t2_u"))

        assertEquals(listOf(setOf("alice", "bob", "noavatar", "unknown")), repository.batches)
        assertEquals("https://i.redd.it/t2_a.png", state.avatarUriForAuthor("alice"))
        assertEquals("https://i.redd.it/t2_b.png", state.avatarUriForAuthor("Bob"))
        assertNull("known account without avatar", state.avatarUriForAuthor("noavatar"))
        assertNull("account Reddit did not answer for", state.avatarUriForAuthor("unknown"))

        // A second thread with an overlap requests only the new author and the
        // unanswered one; resolved authors (with or without avatar) are skipped.
        state.ensureAuthorAvatars(mapOf("alice" to "t2_a", "noavatar" to "t2_n", "unknown" to "t2_u", "carol" to "t2_c"))
        assertEquals(setOf("unknown", "carol"), repository.batches[1])
    }

    @Test
    fun overlappingRequestsDoNotRepeatInFlightAuthorsAndFailuresLeaveThemRetryable() = runBlocking {
        val repository = AvatarRepository(gated = true)
        val state = authenticatedState(repository)

        val first = launch { state.ensureAuthorAvatars(mapOf("alice" to "t2_a")) }
        repository.started.await()
        // Second request while the first is in flight: only the new author.
        val second = launch { state.ensureAuthorAvatars(mapOf("alice" to "t2_a", "bob" to "t2_b")) }
        repository.secondStarted.await()
        assertEquals(listOf(setOf("alice"), setOf("bob")), repository.batches)
        repository.open()
        first.join()
        second.join()
        assertEquals("https://i.redd.it/t2_a.png", state.avatarUriForAuthor("alice"))
        assertEquals("https://i.redd.it/t2_b.png", state.avatarUriForAuthor("bob"))

        val failing = AvatarRepository(fail = true)
        val failingState = authenticatedState(failing)
        failingState.ensureAuthorAvatars(mapOf("dave" to "t2_d"))
        assertNull(failingState.avatarUriForAuthor("dave"))
        failingState.ensureAuthorAvatars(mapOf("dave" to "t2_d"))
        assertEquals(2, failing.batches.size)
    }

    @Test
    fun cacheIsBoundedAndLocalAccountsNeverRequest() = runBlocking {
        val repository = AvatarRepository()
        val state = authenticatedState(repository)
        val many = (1..(MAX_AUTHOR_AVATARS + 10)).associate { index -> "user$index" to "t2_u$index" }
        state.ensureAuthorAvatars(many)
        assertNull("oldest entries are evicted", state.avatarUriForAuthor("user1"))
        assertEquals("https://i.redd.it/t2_u${MAX_AUTHOR_AVATARS + 10}.png", state.avatarUriForAuthor("user${MAX_AUTHOR_AVATARS + 10}"))

        val local = SubshellState(repository = FakeSubshellRepository(), identityMetadataRepository = repository)
        val before = repository.batches.size
        local.ensureAuthorAvatars(mapOf("alice" to "t2_a"))
        assertEquals(before, repository.batches.size)
        assertNull(local.avatarUriForAuthor("alice"))
    }

    private fun authenticatedState(repository: IdentityMetadataRepository): SubshellState {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        return SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
    }
}

private class AvatarRepository(
    private val gated: Boolean = false,
    private val fail: Boolean = false,
) : IdentityMetadataRepository {
    val batches = mutableListOf<Set<String>>()
    val started = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>()

    fun open() {
        gate.complete(Unit)
    }

    override suspend fun refreshAuthorAvatars(
        fullnamesByUsername: Map<String, String>,
    ): RepositoryResult<Map<String, String?>> {
        batches += fullnamesByUsername.keys
        if (gated) {
            if (!started.complete(Unit)) secondStarted.complete(Unit)
            gate.await()
        }
        if (fail) return RepositoryResult.Error("HTTP 500")
        return RepositoryResult.Content(
            fullnamesByUsername
                .filterKeys { username -> username != "unknown" }
                .mapValues { (username, fullname) ->
                    if (username == "noavatar") null else "https://i.redd.it/$fullname.png"
                },
        )
    }

    override fun cachedCommunityIdentity(name: String): Community? = null
    override suspend fun refreshCommunityIdentity(name: String, fallback: Community): RepositoryResult<Community> =
        RepositoryResult.Content(fallback)
    override fun cachedUserIdentity(username: String): UserFixture? = null
    override suspend fun refreshUserIdentity(username: String, fallback: UserFixture): RepositoryResult<UserFixture> =
        RepositoryResult.Content(fallback)
}
