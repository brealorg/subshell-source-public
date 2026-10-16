package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the transient user-flair runtime bridge inside
 * SubshellState as it behaves today, written before it moves to
 * UserFlairState. Current behaviour, not a specification.
 */
class UserFlairStateCharacterisationTest {
    private val loads = mutableListOf<Triple<String, String, Boolean>>()
    private val mutations = mutableListOf<UserFlairMutationRequest>()
    private val authorizations = mutableListOf<String>()

    @Test
    fun authenticatedSessionForwardsLoadMutateAndAuthorizeWithTheActiveAccount() {
        val state = authenticatedState()

        state.loadUserFlair("homelab")
        state.loadUserFlair(" r/homelab ", force = true)
        assertEquals(
            listOf(Triple("reddit:test", "homelab", false), Triple("reddit:test", " r/homelab ", true)),
            loads,
        )

        state.setUserFlair("homelab", "tmpl-1", "Builder")
        state.setUserFlair("homelab", "tmpl-2", null)
        assertEquals(
            listOf(
                UserFlairMutationRequest("reddit:test", "homelab", "tmpl-1", "Builder"),
                UserFlairMutationRequest("reddit:test", "homelab", "tmpl-2", null),
            ),
            mutations,
        )

        state.authorizeUserFlair()
        assertEquals(listOf("reddit:test"), authorizations)
    }

    @Test
    fun uiStateIsWhateverWasLastSynchronised() {
        val state = authenticatedState()
        assertEquals(UserFlairUiState(), state.userFlairUiState)

        val loading = UserFlairUiState(accountStableId = "reddit:test", communityName = "homelab", loading = true)
        state.synchronizeUserFlairUiState(loading)
        assertEquals(loading, state.userFlairUiState)

        val loaded = loading.copy(loading = false, loaded = true, contentRevision = 3L)
        state.synchronizeUserFlairUiState(loaded)
        assertEquals(loaded, state.userFlairUiState)
    }

    @Test
    fun localSessionsNeverForwardAndReplacingTheRequestersTakesEffectImmediately() {
        val local = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
        )
        local.setAuthenticatedUserFlairRequesters(
            loader = { account, community, force -> loads += Triple(account, community, force) },
            mutator = { request -> mutations += request },
            authorizer = { account -> authorizations += account },
        )
        local.loadUserFlair("homelab")
        local.setUserFlair("homelab", "tmpl-1", "x")
        local.authorizeUserFlair()
        assertTrue(loads.isEmpty() && mutations.isEmpty() && authorizations.isEmpty())

        val state = authenticatedState()
        val second = mutableListOf<String>()
        state.setAuthenticatedUserFlairRequesters(
            loader = { _, community, _ -> second += "load:$community" },
            mutator = { request -> second += "set:${request.templateId}" },
            authorizer = { second += "authorize" },
        )
        state.loadUserFlair("homelab")
        state.setUserFlair("homelab", "tmpl-9", null)
        state.authorizeUserFlair()
        assertTrue(loads.isEmpty() && mutations.isEmpty() && authorizations.isEmpty())
        assertEquals(listOf("load:homelab", "set:tmpl-9", "authorize"), second)
    }

    private fun authenticatedState(): SubshellState {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        return SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        ).also { state ->
            state.setAuthenticatedUserFlairRequesters(
                loader = { accountId, community, force -> loads += Triple(accountId, community, force) },
                mutator = { request -> mutations += request },
                authorizer = { accountId -> authorizations += accountId },
            )
        }
    }
}
