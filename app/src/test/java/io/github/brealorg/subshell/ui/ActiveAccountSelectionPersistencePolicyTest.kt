package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveAccountSelectionPersistencePolicyTest {
    private val root = localFixtureAccountSummaries(listOf("root")).single()
    private val authenticated = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:account01",
        username = "brealorg",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val accounts = listOf(root, authenticated)

    @Test
    fun authoritativePersistedIdRepairsStaleLegacyFixtureUsername() {
        val resolved = resolveActiveAccountSelection(
            accounts = accounts,
            persistedActiveAccountId = authenticated.stableId,
            directoryActiveAccountId = authenticated.stableId,
            legacyActiveAccountUsername = "root",
        )

        assertEquals(authenticated, resolved.account)
        assertTrue(resolved.migrationRequired)
    }

    @Test
    fun invalidPersistedIdFallsBackToAuthoritativeDirectorySelection() {
        val resolved = resolveActiveAccountSelection(
            accounts = accounts,
            persistedActiveAccountId = "reddit:redreader_compatibility:missing",
            directoryActiveAccountId = authenticated.stableId,
            legacyActiveAccountUsername = "root",
        )

        assertEquals(authenticated, resolved.account)
        assertTrue(resolved.migrationRequired)
    }

    @Test
    fun legacyUsernameCanResolveAuthenticatedAccountWhenIdsAreAbsent() {
        val resolved = resolveActiveAccountSelection(
            accounts = accounts,
            persistedActiveAccountId = null,
            directoryActiveAccountId = null,
            legacyActiveAccountUsername = "BREALORG",
        )

        assertEquals(authenticated, resolved.account)
        assertTrue(resolved.migrationRequired)
    }

    @Test
    fun canonicalPairDoesNotRequestMigration() {
        val resolved = resolveActiveAccountSelection(
            accounts = accounts,
            persistedActiveAccountId = authenticated.stableId,
            directoryActiveAccountId = authenticated.stableId,
            legacyActiveAccountUsername = authenticated.username,
        )

        assertEquals(authenticated, resolved.account)
        assertFalse(resolved.migrationRequired)
    }

    @Test
    fun accountSwitchPersistsBothAuthoritativeIdAndLegacyUsername() {
        val persistedIds = mutableListOf<String>()
        val persistedUsernames = mutableListOf<String>()
        val state = SubshellState(
            initialAccountSessions = accounts,
            initialActiveAccountId = root.stableId,
            loadJoinedCommunityIds = { emptySet() },
            loadAccountPostActions = { AccountPostActions() },
            persistActiveAccountId = persistedIds::add,
            persistActiveAccountUsername = persistedUsernames::add,
        )

        state.switchAccount(authenticated.stableId)

        assertEquals(authenticated.stableId, state.activeAccountId)
        assertEquals(authenticated.username, state.activeAccountUsername)
        assertEquals(listOf(authenticated.stableId), persistedIds)
        assertEquals(listOf(authenticated.username), persistedUsernames)
    }
}
