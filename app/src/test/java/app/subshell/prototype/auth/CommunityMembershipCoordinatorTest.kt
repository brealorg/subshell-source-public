package app.subshell.prototype.auth

import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipRequest
import app.subshell.prototype.data.account.NOT_CHECKED_MEMBERSHIP_MESSAGE
import app.subshell.prototype.data.account.NOT_CONFIRMED_MEMBERSHIP_MESSAGE
import app.subshell.prototype.data.account.blocksActionFor
import app.subshell.prototype.data.account.failureMessageFor
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommunityMembershipResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityMembershipCoordinatorTest {
    @Test
    fun successfulMutationPublishesCanonicalTargetAndCompletion() =
        runBlocking {
            var applied: CommunityMembershipRequest? = null

            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = { request ->
                    AuthenticatedCommunityMembershipResult.Updated(request)
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
                onMembershipUpdated = { request ->
                    applied = request
                },
            )

            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = " reddit:account ",
                    communityName = "r/Norge",
                    action = CommunityMembershipAction.JOIN,
                ),
            )

            val state = coordinator.uiState.value

            assertEquals(
                CommunityMembershipPhase.SUCCEEDED,
                state.phase,
            )
            assertEquals("reddit:account", state.accountStableId)
            assertEquals("norge", state.communityName)
            assertEquals(
                CommunityMembershipAction.JOIN,
                state.action,
            )
            assertEquals(1L, state.completionRevision)
            assertEquals("norge", applied?.communityName)
        }

    @Test
    fun authorizationRequiredPreservesTransientRequest() =
        runBlocking {
            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = {
                    AuthenticatedCommunityMembershipResult.AuthorizationRequired
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
            )

            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = "reddit:account",
                    communityName = "android",
                    action = CommunityMembershipAction.LEAVE,
                ),
            )

            assertEquals(
                CommunityMembershipPhase.AUTHORIZATION_REQUIRED,
                coordinator.uiState.value.phase,
            )

            coordinator.beginAuthorization()

            assertEquals(
                CommunityMembershipPhase.FAILED,
                coordinator.uiState.value.phase,
            )
            assertEquals(
                CommunityMembershipFailureKind.CONFIGURATION,
                coordinator.uiState.value.failureKind,
            )
        }

    @Test
    fun accountMismatchFailsBeforeWriter() =
        runBlocking {
            var writerCalled = false

            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = { request ->
                    writerCalled = true
                    AuthenticatedCommunityMembershipResult.Updated(request)
                },
                clientMode = null,
                activeAccountStableId = { "reddit:other" },
            )

            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = "reddit:account",
                    communityName = "norge",
                    action = CommunityMembershipAction.JOIN,
                ),
            )

            assertFalse(writerCalled)
            assertEquals(
                CommunityMembershipPhase.FAILED,
                coordinator.uiState.value.phase,
            )
            assertEquals(
                CommunityMembershipFailureKind.ACCOUNT_CHANGED,
                coordinator.uiState.value.failureKind,
            )
            assertEquals(
                0L,
                coordinator.uiState.value.completionRevision,
            )
        }

    @Test
    fun typedWriterFailureIsPreserved() =
        runBlocking {
            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = {
                    AuthenticatedCommunityMembershipResult.Failed(
                        kind = CommunityMembershipFailureKind.HTTP,
                        retryable = true,
                        statusCode = 429,
                    )
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
            )

            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = "reddit:account",
                    communityName = "norge",
                    action = CommunityMembershipAction.JOIN,
                ),
            )

            val state = coordinator.uiState.value

            assertEquals(
                CommunityMembershipPhase.FAILED,
                state.phase,
            )
            assertEquals(
                CommunityMembershipFailureKind.HTTP,
                state.failureKind,
            )
            assertTrue(state.failureRetryable)
            assertEquals(429, state.failureStatusCode)
            assertNull(state.message)
        }
    @Test
    fun authoritativeRefreshClearsConfirmedSuccess() =
        runBlocking {
            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = { request ->
                    AuthenticatedCommunityMembershipResult.Updated(
                        request,
                    )
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
            )

            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = "reddit:account",
                    communityName = "norge",
                    action = CommunityMembershipAction.JOIN,
                ),
            )

            assertEquals(
                CommunityMembershipPhase.SUCCEEDED,
                coordinator.uiState.value.phase,
            )

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("android", "norge"),
            )

            assertEquals(
                CommunityMembershipPhase.IDLE,
                coordinator.uiState.value.phase,
            )
            assertEquals(
                1L,
                coordinator.uiState.value.completionRevision,
            )
            assertNull(
                coordinator.uiState.value.communityName,
            )
        }

    @Test
    fun unconfirmedFreshRefreshBecomesOutcomeUnknown() =
        runBlocking {
            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = { request ->
                    AuthenticatedCommunityMembershipResult.Updated(
                        request,
                    )
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
            )

            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = "reddit:account",
                    communityName = "norge",
                    action = CommunityMembershipAction.JOIN,
                ),
            )

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("android"),
            )

            assertEquals(
                CommunityMembershipPhase.FAILED,
                coordinator.uiState.value.phase,
            )
            assertEquals(
                CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                coordinator.uiState.value.failureKind,
            )
            assertEquals(
                1L,
                coordinator.uiState.value.completionRevision,
            )
        }

    @Test
    fun outcomeUnknownTriggersRefreshAndCanLaterBeConfirmed() =
        runBlocking {
            var refreshRequest: CommunityMembershipRequest? = null

            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = { request ->
                    AuthenticatedCommunityMembershipResult.Failed(
                        kind =
                            CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                    )
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
                onMembershipUpdated = { request ->
                    refreshRequest = request
                },
            )

            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = "reddit:account",
                    communityName = "norge",
                    action = CommunityMembershipAction.JOIN,
                ),
            )

            assertEquals(
                "norge",
                refreshRequest?.communityName,
            )
            assertEquals(
                CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                coordinator.uiState.value.failureKind,
            )

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("norge"),
            )

            assertEquals(
                CommunityMembershipPhase.IDLE,
                coordinator.uiState.value.phase,
            )
        }

    @Test
    fun aStillDisagreeingRefreshKeepsTheUnknownOutcome() =
        runBlocking {
            val coordinator = unconfirmedJoin("norge")
            val unknown = coordinator.uiState.value

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("android"),
            )

            assertEquals(unknown, coordinator.uiState.value)
        }

    @Test
    fun writerOutcomeUnknownIsNotClearedByADisagreeingList() =
        runBlocking {
            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = {
                    AuthenticatedCommunityMembershipResult.Failed(
                        kind =
                            CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                    )
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
            )
            coordinator.update(
                CommunityMembershipRequest(
                    accountStableId = "reddit:account",
                    communityName = "norge",
                    action = CommunityMembershipAction.JOIN,
                ),
            )

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("android"),
            )

            assertEquals(
                CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                coordinator.uiState.value.failureKind,
            )
        }

    @Test
    fun exhaustedReconciliationEndsInANonBlockingNotConfirmedState() =
        runBlocking {
            val coordinator = unconfirmedJoin("norge")

            coordinator.reconciliationExhausted(
                request("norge"),
            )

            val state = coordinator.uiState.value
            assertEquals(CommunityMembershipPhase.FAILED, state.phase)
            assertEquals(
                CommunityMembershipFailureKind.NOT_CONFIRMED,
                state.failureKind,
            )
            assertFalse(state.failureRetryable)
            assertEquals("norge", state.communityName)
            assertEquals(CommunityMembershipAction.JOIN, state.action)
            assertFalse(state.blocksActionFor("reddit:account", "norge"))
            assertEquals(
                NOT_CONFIRMED_MEMBERSHIP_MESSAGE,
                state.failureMessageFor("reddit:account", "norge"),
            )
        }

    @Test
    fun exhaustedReconciliationLeavesOtherStatesAlone() =
        runBlocking {
            val coordinator = unconfirmedJoin("norge")
            val unknown = coordinator.uiState.value

            coordinator.reconciliationExhausted(request("android"))
            coordinator.reconciliationExhausted(
                request("norge").copy(accountStableId = "reddit:other"),
            )
            assertEquals(unknown, coordinator.uiState.value)

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("norge"),
            )
            coordinator.reconciliationExhausted(request("norge"))
            assertEquals(
                CommunityMembershipPhase.IDLE,
                coordinator.uiState.value.phase,
            )
        }

    @Test
    fun aLaterAgreeingListClearsNotConfirmed() =
        runBlocking {
            val coordinator = unconfirmedJoin("norge")
            coordinator.reconciliationExhausted(request("norge"))
            val notConfirmed = coordinator.uiState.value

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("android"),
            )
            assertEquals(notConfirmed, coordinator.uiState.value)

            coordinator.authoritativeRefreshApplied(
                accountStableId = "reddit:account",
                joinedCommunityNames = setOf("android", "norge"),
            )
            assertEquals(
                CommunityMembershipPhase.IDLE,
                coordinator.uiState.value.phase,
            )
        }

    @Test
    fun exhaustedWithoutAnyListSaysTheChangeCouldNotBeChecked() =
        runBlocking {
            val coordinator = CommunityMembershipCoordinator(
                capability = null,
                writer = {
                    AuthenticatedCommunityMembershipResult.Failed(
                        kind =
                            CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
                    )
                },
                clientMode = null,
                activeAccountStableId = { "reddit:account" },
            )
            coordinator.update(request("norge"))

            coordinator.reconciliationExhausted(request("norge"))

            val state = coordinator.uiState.value
            assertEquals(
                CommunityMembershipFailureKind.NOT_CONFIRMED,
                state.failureKind,
            )
            assertFalse(state.blocksActionFor("reddit:account", "norge"))
            assertEquals(
                NOT_CHECKED_MEMBERSHIP_MESSAGE,
                state.failureMessageFor("reddit:account", "norge"),
            )
        }

    private fun request(community: String) =
        CommunityMembershipRequest(
            accountStableId = "reddit:account",
            communityName = community,
            action = CommunityMembershipAction.JOIN,
        )

    private suspend fun unconfirmedJoin(
        community: String,
    ): CommunityMembershipCoordinator {
        val coordinator = CommunityMembershipCoordinator(
            capability = null,
            writer = { request ->
                AuthenticatedCommunityMembershipResult.Updated(request)
            },
            clientMode = null,
            activeAccountStableId = { "reddit:account" },
        )
        coordinator.update(request(community))
        coordinator.authoritativeRefreshApplied(
            accountStableId = "reddit:account",
            joinedCommunityNames = setOf("android"),
        )
        assertEquals(
            CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
            coordinator.uiState.value.failureKind,
        )
        return coordinator
    }
}
