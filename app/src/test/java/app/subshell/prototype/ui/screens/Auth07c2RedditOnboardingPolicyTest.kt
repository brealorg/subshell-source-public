package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class Auth07c2RedditOnboardingPolicyTest {
    @Test
    fun unknownConfigurationWaitsInsteadOfGuessing() {
        assertEquals(
            RedditOnboardingEntry.CHECKING,
            resolveRedditOnboardingEntry(
                configurationKnown = false,
                configured = false,
            ),
        )
    }

    @Test
    fun missingConfigurationStartsSetup() {
        assertEquals(
            RedditOnboardingEntry.SETUP,
            resolveRedditOnboardingEntry(
                configurationKnown = true,
                configured = false,
            ),
        )
    }

    @Test
    fun configuredInstallationWithoutAccountGoesStraightToConnect() {
        assertEquals(
            RedditOnboardingEntry.READY,
            resolveRedditOnboardingEntry(
                configurationKnown = true,
                configured = true,
            ),
        )
    }
}
