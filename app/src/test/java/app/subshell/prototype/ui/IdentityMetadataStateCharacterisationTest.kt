package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the identity-metadata cache inside SubshellState as it
 * behaves today, written before the cache moves to IdentityMetadataState.
 * These tests describe current behaviour; they are not all requirements.
 */
class IdentityMetadataStateCharacterisationTest {
    @Test
    fun communityLookupPrefersOverrideThenCacheThenRepository() = runBlocking {
        // Repository tier: a local (unauthenticated) session resolves fixtures.
        val local = SubshellState(repository = FakeSubshellRepository())
        val fixture = local.communityFor("subshell")
        assertEquals("subshell", fixture?.name)
        assertSame(fixture, local.communityFor("SUBSHELL"))
        assertNull(local.communityFor("no-such-community-anywhere"))

        // Cache tier: an authenticated session synthesises a placeholder once.
        val repository = GatedIdentityMetadataRepository()
        val state = authenticatedState(repository)
        val placeholder = state.communityFor("homelab")
        assertEquals("live:homelab", placeholder?.id)
        assertSame(placeholder, state.communityFor("homelab"))
        assertEquals(0, repository.cachedCommunityReads)
        assertEquals(0, repository.communityRequests)

        // Override tier: a refreshed identity wins over the cached placeholder.
        repository.open()
        state.ensureCommunityIdentity("homelab")
        val refreshed = state.communityFor("homelab")
        assertEquals("https://styles.redditmedia.com/homelab-banner.png", refreshed?.bannerImageUri)
        assertEquals(refreshed, state.communityIdentityFor("homelab"))
    }

    @Test
    fun userLookupPrefersOverrideThenCacheThenRepository() = runBlocking {
        val local = SubshellState(repository = FakeSubshellRepository())
        assertEquals("packetmoss", local.userFor("packetmoss").username)
        assertEquals("Public profile preview.", local.userFor("nobody-here").description)
        assertSame(local.userFor("nobody-here"), local.userFor("NOBODY-HERE"))

        val repository = GatedIdentityMetadataRepository()
        val state = authenticatedState(repository)
        val placeholder = state.userFor("packetmoss")
        assertEquals("Public profile preview.", placeholder.description)
        assertSame(placeholder, state.userFor("packetmoss"))
        assertEquals(0, repository.cachedUserReads)

        repository.open()
        state.ensureUserIdentity("packetmoss")
        assertEquals("https://i.redd.it/packetmoss.png", state.userFor("packetmoss").avatarImageUri)
    }

    @Test
    fun ensureUserIdentityDoesNotStartASecondLoadWhileOneIsInFlight() = runBlocking {
        val repository = GatedIdentityMetadataRepository()
        val state = authenticatedState(repository)

        val first = launch { state.ensureUserIdentity("packetmoss") }
        repository.userRequestStarted.await()
        state.ensureUserIdentity("packetmoss")
        assertEquals(1, repository.userRequests)

        repository.open()
        first.join()
        state.ensureUserIdentity("packetmoss")
        assertEquals(1, repository.userRequests)
    }

    @Test
    fun ensureCommunityIdentitiesDoesNotStartASecondBatchForNamesAlreadyLoading() = runBlocking {
        val repository = GatedIdentityMetadataRepository()
        val state = authenticatedState(repository)

        val first = launch { state.ensureCommunityIdentities(listOf("homelab", "android")) }
        repository.communityBatchStarted.await()
        state.ensureCommunityIdentities(listOf("homelab", "android"))
        state.ensureCommunityIdentity("homelab")
        assertEquals(1, repository.communityBatchRequests)
        assertEquals(0, repository.communityRequests)

        repository.open()
        first.join()
        state.ensureCommunityIdentities(listOf("android"))
        assertEquals(1, repository.communityBatchRequests)
        assertEquals(
            "https://styles.redditmedia.com/homelab-icon.png",
            state.communityIdentityFor("homelab").iconImageUri,
        )
    }

    @Test
    fun notFoundMarksProfileUnavailableKeepsPlaceholderAndRetriesNextTime() = runBlocking {
        val repository = GatedIdentityMetadataRepository(userStatus = 404)
        repository.open()
        val state = authenticatedState(repository)

        state.ensureUserIdentity("ghost-user")
        assertTrue(state.publicUserProfileUnavailable("ghost-user"))
        assertTrue(state.publicUserProfileUnavailable("u/Ghost-User"))
        assertFalse(state.publicUserProfileUnavailable("someone-else"))
        assertEquals("Public profile preview.", state.userFor("ghost-user").description)
        assertNull(state.userFor("ghost-user").avatarImageUri)

        // Current behaviour: a 404 is not remembered as loaded, so the next
        // ensure asks again; a later success clears the unavailable mark.
        state.ensureUserIdentity("ghost-user")
        assertEquals(2, repository.userRequests)
        repository.userStatus = null
        state.ensureUserIdentity("ghost-user")
        assertEquals(3, repository.userRequests)
        assertFalse(state.publicUserProfileUnavailable("ghost-user"))
        assertEquals("https://i.redd.it/ghost-user.png", state.userFor("ghost-user").avatarImageUri)
    }

    @Test
    fun otherErrorsDoNotMarkProfileUnavailable() = runBlocking {
        val repository = GatedIdentityMetadataRepository(userStatus = 500)
        repository.open()
        val state = authenticatedState(repository)

        state.ensureUserIdentity("flaky-user")
        assertFalse(state.publicUserProfileUnavailable("flaky-user"))
        assertNull(state.userFor("flaky-user").avatarImageUri)
        state.ensureUserIdentity("flaky-user")
        assertEquals(2, repository.userRequests)
    }

    @Test
    fun ensureIsANoOpForLocalSessionsMissingRepositoryAndInvalidNames() = runBlocking {
        val repository = GatedIdentityMetadataRepository()
        repository.open()

        SubshellState(repository = FakeSubshellRepository(), identityMetadataRepository = repository)
            .ensureUserIdentity("packetmoss")
        assertEquals(0, repository.userRequests)

        val noRepository = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = null,
            initialAccountSessions = listOf(authenticatedAccount("reddit:a", "alpha")),
            initialActiveAccountId = "reddit:a",
        )
        noRepository.ensureUserIdentity("packetmoss")
        noRepository.ensureCommunityIdentity("homelab")

        val state = authenticatedState(repository)
        state.ensureUserIdentity("   ")
        state.ensureUserIdentity("not a valid name!")
        state.ensureCommunityIdentity("")
        assertEquals(0, repository.userRequests)
        assertEquals(0, repository.communityRequests)
    }

    @Test
    fun profileActivityEvidenceIsRecordedPerNormalizedUsername() {
        val state = SubshellState(repository = FakeSubshellRepository())

        assertFalse(state.hasPublicProfileActivityEvidence("packetmoss", ProfileActivityEvidence.POST))
        state.openUser("Packetmoss", activityEvidence = ProfileActivityEvidence.POST)
        assertTrue(state.hasPublicProfileActivityEvidence("packetmoss", ProfileActivityEvidence.POST))
        assertTrue(state.hasPublicProfileActivityEvidence("u/PACKETMOSS", ProfileActivityEvidence.POST))
        assertFalse(state.hasPublicProfileActivityEvidence("packetmoss", ProfileActivityEvidence.COMMENT))

        state.openUser("packetmoss", activityEvidence = ProfileActivityEvidence.COMMENT)
        assertTrue(state.hasPublicProfileActivityEvidence("packetmoss", ProfileActivityEvidence.COMMENT))
        assertTrue(state.hasPublicProfileActivityEvidence("packetmoss", ProfileActivityEvidence.POST))

        state.openUser("packetmoss")
        assertTrue(state.hasPublicProfileActivityEvidence("packetmoss", ProfileActivityEvidence.POST))
        assertFalse(state.hasPublicProfileActivityEvidence("not a valid name!", ProfileActivityEvidence.POST))
        assertFalse(state.hasPublicProfileActivityEvidence("", ProfileActivityEvidence.POST))
    }

    /**
     * Documents current behaviour, not a requirement: identity overrides,
     * caches and the unavailable-profile mark are not cleared when the
     * active account changes.
     */
    @Test
    fun currentBehaviourIdentityCachesSurviveAccountSwitchNotARequirement() = runBlocking {
        val repository = GatedIdentityMetadataRepository()
        repository.open()
        val alpha = authenticatedAccount("reddit:alpha", "alpha")
        val beta = authenticatedAccount("reddit:beta", "beta")
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = repository,
            initialAccountSessions = listOf(alpha, beta),
            initialActiveAccountId = alpha.stableId,
        )

        state.ensureUserIdentity("packetmoss")
        state.ensureCommunityIdentity("homelab")
        repository.userStatus = 404
        state.ensureUserIdentity("ghost-user")
        val cachedPlaceholder = state.userFor("someone-uncached")

        state.switchAccount(beta.stableId)
        assertEquals("beta", state.activeAccountUsername)

        assertEquals("https://i.redd.it/packetmoss.png", state.userFor("packetmoss").avatarImageUri)
        assertEquals(
            "https://styles.redditmedia.com/homelab-banner.png",
            state.communityIdentityFor("homelab").bannerImageUri,
        )
        assertTrue(state.publicUserProfileUnavailable("ghost-user"))
        assertSame(cachedPlaceholder, state.userFor("someone-uncached"))
        state.ensureUserIdentity("packetmoss")
        assertEquals(2, repository.userRequests)
    }

    private fun authenticatedState(repository: IdentityMetadataRepository): SubshellState {
        val account = authenticatedAccount("reddit:test", "connected-user")
        return SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
    }

    private fun authenticatedAccount(stableId: String, username: String) = AccountSessionSummary(
        stableId = stableId,
        username = username,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
}

/**
 * Identity repository whose refreshes block on a gate until [open] is called,
 * so a test can observe an in-flight load. [userStatus] turns user refreshes
 * into errors with that HTTP status.
 */
private class GatedIdentityMetadataRepository(
    var userStatus: Int? = null,
) : IdentityMetadataRepository {
    var userRequests = 0
    var communityRequests = 0
    var communityBatchRequests = 0
    var cachedCommunityReads = 0
    var cachedUserReads = 0
    val userRequestStarted = CompletableDeferred<Unit>()
    val communityBatchStarted = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>()

    fun open() {
        gate.complete(Unit)
    }

    override fun cachedCommunityIdentity(name: String): Community? {
        cachedCommunityReads += 1
        return null
    }

    override suspend fun refreshCommunityIdentity(
        name: String,
        fallback: Community,
    ): RepositoryResult<Community> {
        communityRequests += 1
        gate.await()
        return RepositoryResult.Content(
            fallback.copy(bannerImageUri = "https://styles.redditmedia.com/$name-banner.png"),
        )
    }

    override suspend fun refreshCommunityIdentities(
        fallbacks: List<Community>,
    ): RepositoryResult<List<Community>> {
        communityBatchRequests += 1
        communityBatchStarted.complete(Unit)
        gate.await()
        return RepositoryResult.Content(
            fallbacks.map { fallback ->
                fallback.copy(iconImageUri = "https://styles.redditmedia.com/${fallback.name}-icon.png")
            },
        )
    }

    override fun cachedUserIdentity(username: String): UserFixture? {
        cachedUserReads += 1
        return null
    }

    override suspend fun refreshUserIdentity(
        username: String,
        fallback: UserFixture,
    ): RepositoryResult<UserFixture> {
        userRequests += 1
        userRequestStarted.complete(Unit)
        gate.await()
        val status = userStatus
        if (status != null) {
            return RepositoryResult.Error(
                message = "HTTP $status",
                failureDetails = FailureDetails(httpStatusCode = status),
            )
        }
        return RepositoryResult.Content(fallback.copy(avatarImageUri = "https://i.redd.it/$username.png"))
    }
}
