package io.github.brealorg.subshell.data

import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import io.github.brealorg.subshell.data.error.failureDetailsOrNull
import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.SubshellNetworkTransport
import io.github.brealorg.subshell.data.network.reddit.IdentityMetadataDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditIdentityNetworkDataSource
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.model.UserFixture
import kotlin.coroutines.cancellation.CancellationException

internal class RoomBackedIdentityMetadataRepository(
    private val dataSource: IdentityMetadataDataSource,
    cache: IdentityMetadataCacheRepository,
) : IdentityMetadataRepository {
    private val cache: IdentityMetadataCacheRepository = StorageSafeIdentityMetadataCache(cache)

    override fun cachedCommunityIdentity(name: String): Community? =
        cache.cachedCommunityIdentity(name)

    override suspend fun refreshCommunityIdentity(
        name: String,
        fallback: Community,
    ): RepositoryResult<Community> = when (val result = dataSource.community(name, fallback)) {
        is NetworkResult.Success -> {
            cache.cacheCommunityIdentity(result.value)
            cache.cachedCommunityIdentity(name)
                ?.let { cached -> RepositoryResult.Content(cached) }
                ?: RepositoryResult.Error<Community>(
                    message = "Community identity cache write failed",
                    failureDetails = FailureDetails(localKind = LocalFailureKind.STORAGE),
                )
        }
        is NetworkResult.HttpFailure -> RepositoryResult.Error<Community>(
            message = "Community identity request failed",
            cached = cache.cachedCommunityIdentity(name),
            failureDetails = result.failureDetailsOrNull(),
        )
        is NetworkResult.TransportFailure -> RepositoryResult.Offline<Community>(
            cached = cache.cachedCommunityIdentity(name),
            message = "Community identity is unavailable offline",
            failureDetails = result.failureDetailsOrNull(),
        )
        is NetworkResult.ProtocolFailure -> RepositoryResult.Error<Community>(
            message = "Community identity response was invalid",
            cached = cache.cachedCommunityIdentity(name),
            failureDetails = result.failureDetailsOrNull(),
        )
    }

    override suspend fun refreshCommunityIdentities(
        fallbacks: List<Community>,
    ): RepositoryResult<List<Community>> {
        if (fallbacks.isEmpty()) return RepositoryResult.Empty("No community identities requested")
        val cachedFallbacks = {
            fallbacks.mapNotNull { fallback -> cache.cachedCommunityIdentity(fallback.name) }
        }
        return when (val result = dataSource.communities(fallbacks)) {
            is NetworkResult.Success -> {
                result.value.forEach(cache::cacheCommunityIdentity)
                val cached = result.value.mapNotNull { community ->
                    cache.cachedCommunityIdentity(community.name)
                }
                if (cached.size == result.value.size) {
                    RepositoryResult.Content(cached)
                } else {
                    RepositoryResult.Error(
                        message = "Community identity cache write failed",
                        cached = cached,
                        failureDetails = FailureDetails(localKind = LocalFailureKind.STORAGE),
                    )
                }
            }
            is NetworkResult.HttpFailure -> RepositoryResult.Error(
                message = "Community identity batch request failed",
                cached = cachedFallbacks(),
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> RepositoryResult.Offline(
                cached = cachedFallbacks(),
                message = "Community identities are unavailable offline",
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure -> RepositoryResult.Error(
                message = "Community identity batch response was invalid",
                cached = cachedFallbacks(),
                failureDetails = result.failureDetailsOrNull(),
            )
        }
    }

    override fun cachedUserIdentity(username: String): UserFixture? =
        cache.cachedUserIdentity(username)

    override suspend fun refreshAuthorAvatars(
        fullnamesByUsername: Map<String, String>,
    ): RepositoryResult<Map<String, String?>> {
        if (fullnamesByUsername.isEmpty()) {
            return RepositoryResult.Empty("No author avatars requested")
        }
        return when (val result = dataSource.authorAvatars(fullnamesByUsername.values)) {
            is NetworkResult.Success -> {
                val byUsername = linkedMapOf<String, String?>()
                for ((username, fullname) in fullnamesByUsername) {
                    val key = fullname.trim().lowercase()
                    if (key in result.value) byUsername[username] = result.value[key]
                }
                RepositoryResult.Content(byUsername)
            }
            is NetworkResult.HttpFailure -> RepositoryResult.Error(
                message = "Author avatar request failed",
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.TransportFailure -> RepositoryResult.Offline(
                message = "Author avatars are unavailable offline",
                failureDetails = result.failureDetailsOrNull(),
            )
            is NetworkResult.ProtocolFailure -> RepositoryResult.Error(
                message = "Author avatar response was invalid",
                failureDetails = result.failureDetailsOrNull(),
            )
        }
    }

    override suspend fun refreshUserIdentity(
        username: String,
        fallback: UserFixture,
    ): RepositoryResult<UserFixture> = when (val result = dataSource.user(username, fallback)) {
        is NetworkResult.Success -> {
            cache.cacheUserIdentity(result.value)
            cache.cachedUserIdentity(username)
                ?.let { cached -> RepositoryResult.Content(cached) }
                ?: RepositoryResult.Error<UserFixture>(
                    message = "User identity cache write failed",
                    failureDetails = FailureDetails(localKind = LocalFailureKind.STORAGE),
                )
        }
        is NetworkResult.HttpFailure -> RepositoryResult.Error<UserFixture>(
            message = "User identity request failed",
            cached = cache.cachedUserIdentity(username),
            failureDetails = result.failureDetailsOrNull(),
        )
        is NetworkResult.TransportFailure -> RepositoryResult.Offline<UserFixture>(
            cached = cache.cachedUserIdentity(username),
            message = "User identity is unavailable offline",
            failureDetails = result.failureDetailsOrNull(),
        )
        is NetworkResult.ProtocolFailure -> RepositoryResult.Error<UserFixture>(
            message = "User identity response was invalid",
            cached = cache.cachedUserIdentity(username),
            failureDetails = result.failureDetailsOrNull(),
        )
    }
}

internal fun createRedditIdentityMetadataRepository(
    repository: SubshellRepository,
    accountSessionDirectory: AccountSessionDirectory?,
    userAgent: String? = null,
): IdentityMetadataRepository? {
    val cache = repository as? IdentityMetadataCacheRepository ?: return null
    val activeTokenProvider = accountSessionDirectory as? NetworkBearerTokenProvider ?: return null
    // MED2_IDENTITY_401_NEVER_ENDS_A_SESSION_V1 (OCB-471): the coordinator's
    // onUnauthorized re-reads the active account's token, so a 401 for a
    // request sent with account A's token (A switched away from meanwhile)
    // deleted account B's session. Avatar/about lookups are cosmetic reads:
    // they use the active token but a 401 here ends no session.
    val tokenProvider = identityLookupTokenProvider(activeTokenProvider)
    return RoomBackedIdentityMetadataRepository(
        dataSource = RedditIdentityNetworkDataSource(
            httpClient = SubshellHttpClient(
                callFactory = SubshellNetworkTransport.sharedCallFactory,
                endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                json = SubshellNetworkJson.providerJson,
                userAgent = userAgent ?: RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
                bearerTokenProvider = tokenProvider,
            ),
        ),
        cache = cache,
    )
}

// MED1_IDENTITY_CACHE_FAULTS_ARE_STORAGE_FAILURES_V1 (OCB-461): a Room fault
// (disk full, an unreadable row) escaped the identity refresh and crashed the
// LaunchedEffect that called it. A failed read is "not cached"; a failed write
// is noticed by the read that follows it (the STORAGE failure above).
private class StorageSafeIdentityMetadataCache(
    private val delegate: IdentityMetadataCacheRepository,
) : IdentityMetadataCacheRepository {
    override fun cachedCommunityIdentity(name: String): Community? =
        orNull { delegate.cachedCommunityIdentity(name) }

    override fun cacheCommunityIdentity(community: Community) {
        orNull { delegate.cacheCommunityIdentity(community) }
    }

    override fun cachedUserIdentity(username: String): UserFixture? =
        orNull { delegate.cachedUserIdentity(username) }

    override fun cacheUserIdentity(user: UserFixture) {
        orNull { delegate.cacheUserIdentity(user) }
    }

    private inline fun <T> orNull(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

/** The active account's token for identity lookups; a 401 there ends no session (OCB-471). */
internal fun identityLookupTokenProvider(active: NetworkBearerTokenProvider): NetworkBearerTokenProvider =
    NetworkBearerTokenProvider { active.bearerToken() }
