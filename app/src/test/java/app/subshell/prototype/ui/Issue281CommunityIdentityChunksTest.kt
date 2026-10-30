package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.state.COMMUNITY_IDENTITY_CHUNK
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ISSUE281_EVERY_VISIBLE_COMMUNITY_GETS_ITS_IDENTITY_V1 (#281, found on the
 * Pixel 2026-10-30): community icons are read from each about page, a few per
 * call; Home asks for every community it shows, and the ones after the first
 * few kept their letter (r/norge, third on Home after a cold start).
 */
class Issue281CommunityIdentityChunksTest {
    /** Answers like the about-based source: at most [perCall] per call. */
    private class AboutPages(private val perCall: Int) : IdentityMetadataRepository {
        val asked = mutableListOf<List<String>>()

        override fun cachedCommunityIdentity(name: String): Community? = null

        override suspend fun refreshCommunityIdentity(name: String, fallback: Community) =
            RepositoryResult.Content(fallback.withIcon())

        override suspend fun refreshCommunityIdentities(fallbacks: List<Community>): RepositoryResult<List<Community>> {
            asked += fallbacks.map(Community::name)
            return RepositoryResult.Content(fallbacks.take(perCall).map { it.withIcon() })
        }

        override fun cachedUserIdentity(username: String): UserFixture? = null

        override suspend fun refreshUserIdentity(username: String, fallback: UserFixture) =
            RepositoryResult.Content(fallback)

        private fun Community.withIcon() = copy(iconImageUri = "https://styles.redditmedia.com/$name.png")
    }

    @Test
    fun everyVisibleCommunityIsAskedForAndGetsItsIcon() = runBlocking {
        val repository = AboutPages(perCall = COMMUNITY_IDENTITY_CHUNK)
        val account = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val names = (1..20).map { index -> "community$index" }

        state.ensureCommunityIdentities(names)

        assertEquals(listOf(8, 8, 4), repository.asked.map(List<String>::size))
        names.forEach { name ->
            assertEquals(name, "https://styles.redditmedia.com/$name.png", state.communityIdentityFor(name).iconImageUri)
        }
        // Loaded once: showing them again asks nothing.
        state.ensureCommunityIdentities(names)
        assertEquals(3, repository.asked.size)
    }
}
