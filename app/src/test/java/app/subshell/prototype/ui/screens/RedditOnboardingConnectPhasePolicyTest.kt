package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The connect panel of the Reddit onboarding screen only renders while no
 * account is on screen. A CONNECTED phase there is either the last frame of a
 * finished connect or state left behind by a logout, so it must offer the
 * connect action and never a spinner with no way forward — the mapping
 * AccountSwitcherPresentation already uses.
 */
class RedditOnboardingConnectPhasePolicyTest {
    @Test
    fun onlyTheGenuinelyRunningPhasesShowWork() {
        val working = ConnectAccountPhase.entries.filter(::redditOnboardingConnectPhaseIsWorking)
        assertEquals(
            listOf(
                ConnectAccountPhase.LAUNCHING,
                ConnectAccountPhase.EXCHANGING,
                ConnectAccountPhase.RESOLVING_IDENTITY,
                ConnectAccountPhase.SYNCING_REDDIT_FEED,
            ),
            working,
        )
    }

    @Test
    fun aConnectedPhaseWithoutAnAccountOffersConnectInsteadOfWork() {
        assertFalse(redditOnboardingConnectPhaseIsWorking(ConnectAccountPhase.CONNECTED))
        assertTrue(redditOnboardingConnectPhaseOffersConnect(ConnectAccountPhase.CONNECTED))
        assertTrue(redditOnboardingConnectPhaseOffersConnect(ConnectAccountPhase.IDLE))
    }

    @Test
    fun theWorkingGroupMatchesTheUiStatesOwnInProgressDefinition() {
        ConnectAccountPhase.entries.forEach { phase ->
            val state = ConnectAccountUiState(phase = phase, providerLabel = "Reddit")
            val expected = state.inProgress && phase != ConnectAccountPhase.AWAITING_CALLBACK
            assertEquals(phase.name, expected, redditOnboardingConnectPhaseIsWorking(phase))
        }
    }

    @Test
    fun noPhaseShowsBothWorkAndConnect() {
        ConnectAccountPhase.entries.forEach { phase ->
            assertFalse(
                phase.name,
                redditOnboardingConnectPhaseIsWorking(phase) &&
                    redditOnboardingConnectPhaseOffersConnect(phase),
            )
        }
    }
}
