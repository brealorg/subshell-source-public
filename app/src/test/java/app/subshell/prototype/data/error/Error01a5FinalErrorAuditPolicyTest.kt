package app.subshell.prototype.data.error

import app.subshell.prototype.data.IdentityMetadataCacheRepository
import app.subshell.prototype.data.RepositoryLoadState
import app.subshell.prototype.data.RepositoryPager
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.RoomBackedIdentityMetadataRepository
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.reddit.IdentityMetadataDataSource
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Error01a5FinalErrorAuditPolicyTest {
    @Test
    fun repositoryPagerPreservesStructuredFailureCause() {
        val details = FailureDetails(
            retryable = true,
            transportKind = NetworkTransportFailureKind.TIMEOUT,
        )
        val pager = RepositoryPager<String>(
            queryKey = RepositoryQueryKey("error01a5|pager"),
            pageSize = 1,
            stableId = { value -> value },
            loadPage = {
                RepositoryResult.Error(
                    message = "Could not load repository page",
                    failureDetails = details,
                )
            },
        )

        pager.loadInitial()

        val state = pager.snapshot.initialLoadState as RepositoryLoadState.Error
        assertEquals(details, state.failureDetails)
        assertEquals("Network: Timeout", state.failureDetails?.toSafeTechnicalDetail())
    }

    @Test
    fun identityMetadataPreservesTransportCauseAcrossRepositoryBoundary() = runBlocking {
        val repository = RoomBackedIdentityMetadataRepository(
            dataSource = FailingIdentityDataSource,
            cache = EmptyIdentityCache,
        )
        val fallback = Community(
            id = "subshell",
            name = "subshell",
            description = "fallback",
            members = "—",
            accent = 0xFF6750A4L,
        )

        val result = repository.refreshCommunityIdentity("subshell", fallback)
        val offline = result as RepositoryResult.Offline<Community>

        assertEquals(NetworkTransportFailureKind.DNS, offline.failureDetails?.transportKind)
        assertEquals(true, offline.failureDetails?.retryable)
    }

    @Test
    fun safeFailureTextRedactsUrlAndHeaderCredentials() {
        val raw = "https://example.invalid/callback?code=secret-code&state=secret-state " +
            "Authorization: Bearer secret-bearer Cookie: secret-cookie access_token=secret-token"
        val safe = SafeFailureText.fromUntrusted(raw)?.value.orEmpty()

        listOf(
            "secret-code",
            "secret-state",
            "secret-bearer",
            "secret-cookie",
            "secret-token",
        ).forEach { secret -> assertFalse(safe.contains(secret)) }
        assertTrue(safe.contains("[redacted]"))
    }
}

private object FailingIdentityDataSource : IdentityMetadataDataSource {
    override suspend fun user(
        username: String,
        fallback: UserFixture,
    ): NetworkResult<UserFixture> = NetworkResult.TransportFailure(
        kind = NetworkTransportFailureKind.DNS,
        retryable = true,
    )

    override suspend fun community(
        communityName: String,
        fallback: Community,
    ): NetworkResult<Community> = NetworkResult.TransportFailure(
        kind = NetworkTransportFailureKind.DNS,
        retryable = true,
    )

    override suspend fun communities(
        fallbacks: List<Community>,
    ): NetworkResult<List<Community>> = NetworkResult.TransportFailure(
        kind = NetworkTransportFailureKind.DNS,
        retryable = true,
    )

    override suspend fun authorAvatars(
        fullnames: Collection<String>,
    ): NetworkResult<Map<String, String?>> = NetworkResult.TransportFailure(
        kind = NetworkTransportFailureKind.DNS,
        retryable = true,
    )
}

private object EmptyIdentityCache : IdentityMetadataCacheRepository {
    override fun cachedCommunityIdentity(name: String): Community? = null

    override fun cacheCommunityIdentity(community: Community) = Unit

    override fun cachedUserIdentity(username: String): UserFixture? = null

    override fun cacheUserIdentity(user: UserFixture) = Unit
}
