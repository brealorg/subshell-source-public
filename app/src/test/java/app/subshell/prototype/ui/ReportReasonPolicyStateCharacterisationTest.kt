package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditReportTargetKind
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the transient report-reason policy bridge inside
 * SubshellState as it behaves today, written before it moves to
 * ReportReasonPolicyState. Current behaviour, not a specification.
 */
class ReportReasonPolicyStateCharacterisationTest {
    private val requests = mutableListOf<Triple<String, String, Boolean>>()

    @Test
    fun ensureRequestsOncePerCommunityNormalisesTheNameAndSkipsLoadingOrLoadedState() {
        val state = authenticatedState()

        state.ensureReportReasonPolicy(" r/HomeLab ")
        assertEquals(listOf(Triple("reddit:test", "HomeLab", false)), requests)

        // Nothing synchronised yet: a second ensure asks again.
        state.ensureReportReasonPolicy("homelab")
        assertEquals(2, requests.size)

        state.synchronizeReportReasonCommunityInfo(
            RedditCommunityInfoUiState(accountStableId = "reddit:test", communityName = "homelab", loading = true),
        )
        state.ensureReportReasonPolicy("homelab")
        assertEquals(2, requests.size)
        assertTrue(state.reportReasonPolicyLoading("HOMELAB"))
        assertFalse(state.reportReasonPolicyFailed("homelab"))
        assertNull(state.reportReasonPolicy("homelab", RedditReportTargetKind.POST))

        state.synchronizeReportReasonCommunityInfo(loadedState("homelab"))
        state.ensureReportReasonPolicy("homelab")
        assertEquals(2, requests.size)
        state.ensureReportReasonPolicy("homelab", force = true)
        assertEquals(Triple("reddit:test", "homelab", true), requests.last())

        // Another community is not covered by the current state.
        state.ensureReportReasonPolicy("android")
        assertEquals(Triple("reddit:test", "android", false), requests.last())
        assertNull(state.reportReasonPolicy("android", RedditReportTargetKind.POST))
        assertFalse(state.reportReasonPolicyLoading("android"))

        state.ensureReportReasonPolicy("   ")
        state.ensureReportReasonPolicy("r/")
        assertEquals(4, requests.size)
    }

    @Test
    fun policyIsDerivedFromTheLoadedSnapshotForTheMatchingAccountAndCommunity() {
        val state = authenticatedState()
        state.synchronizeReportReasonCommunityInfo(loadedState("homelab"))

        val postPolicy = state.reportReasonPolicy("r/Homelab", RedditReportTargetKind.POST)
        assertEquals("homelab", postPolicy?.communityName)
        assertEquals(listOf("no-spam", "be-kind"), postPolicy?.options?.map { it.wireReason })
        assertEquals(listOf("No spam", "Be kind"), postPolicy?.options?.map { it.label })
        assertTrue(postPolicy?.freeFormReportsAllowed == true)
        assertTrue(postPolicy?.freeFormReportsKnown == true)

        val commentPolicy = state.reportReasonPolicy("homelab", RedditReportTargetKind.COMMENT)
        assertEquals(listOf("be-kind"), commentPolicy?.options?.map { it.wireReason })

        assertEquals(state.reportReasonCommunityInfoState, loadedState("homelab"))
        assertFalse(state.reportReasonPolicyLoading("homelab"))
        assertFalse(state.reportReasonPolicyFailed("homelab"))

        // A state for another account does not match this session.
        state.synchronizeReportReasonCommunityInfo(
            loadedState("homelab").copy(accountStableId = "reddit:other"),
        )
        assertNull(state.reportReasonPolicy("homelab", RedditReportTargetKind.POST))
    }

    @Test
    fun failedStateReportsFailedOnlyWhenNotLoadingAndAFailureKindIsSet() {
        val state = authenticatedState()
        state.synchronizeReportReasonCommunityInfo(
            RedditCommunityInfoUiState(
                accountStableId = "reddit:test",
                communityName = "homelab",
                failureKind = RedditCommunityInfoUiFailureKind.HTTP,
                failureHttpStatusCode = 503,
            ),
        )
        assertTrue(state.reportReasonPolicyFailed("homelab"))
        assertFalse(state.reportReasonPolicyLoading("homelab"))
        assertNull(state.reportReasonPolicy("homelab", RedditReportTargetKind.POST))

        // Failed state is not "loaded", so ensure asks again.
        state.ensureReportReasonPolicy("homelab")
        assertEquals(1, requests.size)

        state.synchronizeReportReasonCommunityInfo(
            RedditCommunityInfoUiState(
                accountStableId = "reddit:test",
                communityName = "homelab",
                loading = true,
                failureKind = RedditCommunityInfoUiFailureKind.HTTP,
            ),
        )
        assertFalse(state.reportReasonPolicyFailed("homelab"))
        assertTrue(state.reportReasonPolicyLoading("homelab"))
    }

    @Test
    fun localSessionsNeverRequestAndReplacingTheRequesterTakesEffectImmediately() {
        val local = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
        )
        local.setAuthenticatedReportReasonInfoRequester { account, community, force ->
            requests += Triple(account, community, force)
        }
        local.ensureReportReasonPolicy("homelab")
        assertTrue(requests.isEmpty())
        assertEquals(RedditCommunityInfoUiState(), local.reportReasonCommunityInfoState)

        val state = authenticatedState()
        val second = mutableListOf<String>()
        state.setAuthenticatedReportReasonInfoRequester { _, community, _ -> second += community }
        state.ensureReportReasonPolicy("homelab")
        assertTrue(requests.isEmpty())
        assertEquals(listOf("homelab"), second)
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
            state.setAuthenticatedReportReasonInfoRequester { accountId, community, force ->
                requests += Triple(accountId, community, force)
            }
        }
    }

    private fun loadedState(community: String) = RedditCommunityInfoUiState(
        accountStableId = "reddit:test",
        communityName = community,
        loaded = true,
        snapshot = CommunityInfoSnapshot(
            communityName = community,
            freeFormReports = true,
            rules = listOf(
                CommunityRule(shortName = "No spam", kind = "link", violationReason = "no-spam"),
                CommunityRule(shortName = "Be kind", kind = "all", violationReason = "be-kind"),
            ),
        ),
    )
}
