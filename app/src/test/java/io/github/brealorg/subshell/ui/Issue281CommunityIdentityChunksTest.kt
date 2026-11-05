package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.IdentityMetadataRepository
import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.model.UserFixture
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
    /** Answers like the about-based source: every name it is given. */
    private class AboutPages : IdentityMetadataRepository {
        val asked = mutableListOf<List<String>>()

        override fun cachedCommunityIdentity(name: String): Community? = null

        override suspend fun refreshCommunityIdentity(name: String, fallback: Community) =
            RepositoryResult.Content(fallback.withIcon())

        override suspend fun refreshCommunityIdentities(fallbacks: List<Community>): RepositoryResult<List<Community>> {
            asked += fallbacks.map(Community::name)
            return RepositoryResult.Content(fallbacks.map { it.withIcon() })
        }

        override fun cachedUserIdentity(username: String): UserFixture? = null

        override suspend fun refreshUserIdentity(username: String, fallback: UserFixture) =
            RepositoryResult.Content(fallback)

        private fun Community.withIcon() = copy(iconImageUri = "https://styles.redditmedia.com/$name.png")
    }

    @Test
    fun everyVisibleCommunityIsAskedForAndGetsItsIcon() = runBlocking {
        val repository = AboutPages()
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

        // ISSUE34_EVERY_COMMUNITY_IS_ASKED_ONCE_V1: one call for all of them;
        // the source reads each (it used to read 8 per call and drop the rest).
        assertEquals(listOf(20), repository.asked.map(List<String>::size))
        names.forEach { name ->
            assertEquals(name, "https://styles.redditmedia.com/$name.png", state.communityIdentityFor(name).iconImageUri)
        }
        // Loaded once: showing them again asks nothing.
        state.ensureCommunityIdentities(names)
        assertEquals(1, repository.asked.size)
    }
}
