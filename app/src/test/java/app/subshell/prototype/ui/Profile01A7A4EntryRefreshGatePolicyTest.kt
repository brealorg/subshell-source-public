package app.subshell.prototype.ui

import app.subshell.prototype.data.RepositoryQueryKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01A7A4EntryRefreshGatePolicyTest {
    @Test
    fun sameQueryCanClaimAutomaticRefreshOnlyOnce() {
        val gate = ProfileHistoryEntryRefreshGate()
        val query = RepositoryQueryKey("profile01a7a4|account-a|posts")

        assertTrue(gate.claim(query))
        assertFalse(gate.claim(query))
    }

    @Test
    fun differentQueriesHaveIndependentClaims() {
        val gate = ProfileHistoryEntryRefreshGate()

        assertTrue(gate.claim(RepositoryQueryKey("profile01a7a4|a|posts")))
        assertTrue(gate.claim(RepositoryQueryKey("profile01a7a4|a|comments")))
        assertTrue(gate.claim(RepositoryQueryKey("profile01a7a4|b|posts")))
    }
}
