package app.subshell.prototype.data

import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.IdentityMetadataDataSource
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityMetadataRepositoryPolicyTest {
    @Test
    fun successfulRemoteMetadataIsWrittenAndReadBackBeforeExposure() = runBlocking {
        val cache = RecordingIdentityMetadataCache()
        val repository = RoomBackedIdentityMetadataRepository(
            dataSource = RecordingIdentityMetadataDataSource(),
            cache = cache,
        )
        val fallbackUser = UserFixture(
            username = "packetmoss",
            description = "fallback",
            karma = "—",
        )
        val fallbackCommunity = Community(
            id = "homelab",
            name = "homelab",
            description = "fallback",
            members = "—",
            accent = 0xFF6750A4L,
        )

        assertNull(repository.cachedUserIdentity("packetmoss"))
        assertNull(repository.cachedCommunityIdentity("homelab"))

        val user = repository.refreshUserIdentity("packetmoss", fallbackUser)
        val community = repository.refreshCommunityIdentity("homelab", fallbackCommunity)

        assertEquals(
            "https://i.redd.it/packetmoss.png",
            (user as RepositoryResult.Content<UserFixture>).value.avatarImageUri,
        )
        assertEquals(
            "https://styles.redditmedia.com/homelab-banner.png",
            (community as RepositoryResult.Content<Community>).value.bannerImageUri,
        )
        assertEquals(2, cache.writeCount)
        assertEquals(4, cache.readCount)
    }

    @Test
    fun successfulBatchMetadataIsWrittenAndReadBackBeforeExposure() = runBlocking {
        val cache = RecordingIdentityMetadataCache()
        val repository = RoomBackedIdentityMetadataRepository(
            dataSource = RecordingIdentityMetadataDataSource(),
            cache = cache,
        )
        val fallbacks = listOf("subshell", "Volvo").map { name ->
            Community(
                id = name.lowercase(),
                name = name,
                description = "fallback",
                members = "—",
                accent = 0xFF6750A4L,
            )
        }

        val result = repository.refreshCommunityIdentities(fallbacks)

        val communities = (result as RepositoryResult.Content<List<Community>>).value
        assertEquals(2, communities.size)
        assertEquals(
            "https://styles.redditmedia.com/subshell-icon.png",
            communities.first().iconImageUri,
        )
        assertEquals(2, cache.writeCount)
        assertEquals(2, cache.readCount)
    }
}

class Issue22AuthorAvatarRepositoryPolicyTest {
    @Test
    fun authorAvatarsAreKeyedBackByUsernameAndLeaveUnansweredAccountsOut() = runBlocking {
        val cache = RecordingIdentityMetadataCache()
        val repository = RoomBackedIdentityMetadataRepository(
            dataSource = RecordingIdentityMetadataDataSource(),
            cache = cache,
        )

        val result = repository.refreshAuthorAvatars(
            linkedMapOf("Alice" to "T2_A1", "bob" to "t2_b2"),
        )

        assertEquals(
            RepositoryResult.Content(
                linkedMapOf<String, String?>(
                    "Alice" to "https://i.redd.it/t2_a1.png",
                    "bob" to "https://i.redd.it/t2_b2.png",
                ),
            ),
            result,
        )
        // Avatars are not identity cache writes.
        assertEquals(0, cache.writeCount)
        assertEquals(
            RepositoryResult.Empty("No author avatars requested"),
            repository.refreshAuthorAvatars(emptyMap()),
        )
    }
}

private class RecordingIdentityMetadataCache : IdentityMetadataCacheRepository {
    private val users = linkedMapOf<String, UserFixture>()
    private val communities = linkedMapOf<String, Community>()
    var writeCount = 0
    var readCount = 0

    override fun cachedCommunityIdentity(name: String): Community? {
        readCount += 1
        return communities[name.lowercase()]
    }

    override fun cacheCommunityIdentity(community: Community) {
        writeCount += 1
        communities[community.name.lowercase()] = community
    }

    override fun cachedUserIdentity(username: String): UserFixture? {
        readCount += 1
        return users[username.lowercase()]
    }

    override fun cacheUserIdentity(user: UserFixture) {
        writeCount += 1
        users[user.username.lowercase()] = user
    }
}

private class RecordingIdentityMetadataDataSource : IdentityMetadataDataSource {
    override suspend fun user(
        username: String,
        fallback: UserFixture,
    ): NetworkResult<UserFixture> = success(
        fallback.copy(avatarImageUri = "https://i.redd.it/$username.png"),
    )

    override suspend fun community(
        communityName: String,
        fallback: Community,
    ): NetworkResult<Community> = success(
        fallback.copy(
            bannerImageUri = "https://styles.redditmedia.com/$communityName-banner.png",
        ),
    )

    override suspend fun communities(
        fallbacks: List<Community>,
    ): NetworkResult<List<Community>> = success(
        fallbacks.map { fallback ->
            fallback.copy(
                iconImageUri = "https://styles.redditmedia.com/${fallback.name}-icon.png",
            )
        },
    )

    override suspend fun authorAvatars(
        fullnames: Collection<String>,
    ): NetworkResult<Map<String, String?>> = success(
        fullnames.associate { fullname ->
            fullname.lowercase() to "https://i.redd.it/${fullname.lowercase()}.png"
        },
    )

    private fun <T> success(value: T): NetworkResult<T> = NetworkResult.Success(
        value = value,
        metadata = NetworkResponseMetadata(
            statusCode = 200,
            receivedAtEpochMs = 0L,
            rateLimit = NetworkRateLimit(
                remaining = null,
                used = null,
                resetAfterMs = null,
            ),
            retryAfterMs = null,
        ),
    )
}
