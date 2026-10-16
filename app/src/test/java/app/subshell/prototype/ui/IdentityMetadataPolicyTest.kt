package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityMetadataPolicyTest {
    @Test
    fun repeatedCardIdentityReadsNeverTouchPersistentBackend() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val repository = RecordingIdentityMetadataRepository()
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )

        repeat(1_000) {
            state.communityFor("feed04-hot-path")
            state.communityIdentityFor("feed04-hot-path")
            state.userFor("feed04-user")
        }

        assertEquals(0, repository.cachedCommunityReads)
        assertEquals(0, repository.cachedUserReads)
    }

    @Test
    fun profileAndCommunityMetadataLoadOnceAndRemainAvailableInState() = runBlocking {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val repository = RecordingIdentityMetadataRepository()
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )

        state.ensureUserIdentity("packetmoss")
        state.ensureUserIdentity("packetmoss")
        state.ensureCommunityIdentity("homelab")
        state.ensureCommunityIdentity("homelab")

        assertEquals(1, repository.userRequests)
        assertEquals(1, repository.communityRequests)
        assertEquals("https://i.redd.it/packetmoss.png", state.userFor("packetmoss").avatarImageUri)
        assertEquals(
            "https://styles.redditmedia.com/homelab-banner.png",
            state.communityIdentityFor("homelab").bannerImageUri,
        )
    }

    @Test
    fun homeCommunityMetadataUsesOneBatchAndRemainsAvailableInState() = runBlocking {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val repository = RecordingIdentityMetadataRepository()
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )

        state.ensureCommunityIdentities(listOf("subshell", "Volvo", "subshell"))
        state.ensureCommunityIdentities(listOf("subshell", "Volvo"))

        assertEquals(1, repository.communityBatchRequests)
        assertEquals(
            "https://styles.redditmedia.com/subshell-icon.png",
            state.communityIdentityFor("subshell").iconImageUri,
        )
        assertEquals(
            "https://styles.redditmedia.com/Volvo-icon.png",
            state.communityIdentityFor("Volvo").iconImageUri,
        )
    }
}

private class RecordingIdentityMetadataRepository : IdentityMetadataRepository {
    var userRequests = 0
    var communityRequests = 0
    var communityBatchRequests = 0
    var cachedCommunityReads = 0
    var cachedUserReads = 0
    private val users = linkedMapOf<String, UserFixture>()
    private val communities = linkedMapOf<String, Community>()

    override fun cachedCommunityIdentity(name: String): Community? {
        cachedCommunityReads += 1
        return communities[name.lowercase()]
    }

    override suspend fun refreshCommunityIdentity(
        name: String,
        fallback: Community,
    ): RepositoryResult<Community> {
        communityRequests += 1
        val value = fallback.copy(
            bannerImageUri = "https://styles.redditmedia.com/$name-banner.png",
        )
        communities[name.lowercase()] = value
        return RepositoryResult.Content(value)
    }

    override suspend fun refreshCommunityIdentities(
        fallbacks: List<Community>,
    ): RepositoryResult<List<Community>> {
        communityBatchRequests += 1
        val values = fallbacks.map { fallback ->
            fallback.copy(
                iconImageUri = "https://styles.redditmedia.com/${fallback.name}-icon.png",
            ).also { value -> communities[value.name.lowercase()] = value }
        }
        return RepositoryResult.Content(values)
    }

    override fun cachedUserIdentity(username: String): UserFixture? {
        cachedUserReads += 1
        return users[username.lowercase()]
    }

    override suspend fun refreshUserIdentity(
        username: String,
        fallback: UserFixture,
    ): RepositoryResult<UserFixture> {
        userRequests += 1
        val value = fallback.copy(
            avatarImageUri = "https://i.redd.it/$username.png",
        )
        users[username.lowercase()] = value
        return RepositoryResult.Content(value)
    }
}
