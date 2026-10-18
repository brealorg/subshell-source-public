package app.subshell.prototype.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthBug01OnboardingPolicyTest {
    @Test
    fun clientIdConfigurationReloadStartsFreshRootTaskWithoutFinishRace() {
        val activity = File(
            "src/main/java/app/subshell/prototype/MainActivity.kt",
        ).readText()

        val reload = activity
            .substringAfter("private fun reloadRedditConfigurationHost() {")
            .substringBefore("private fun launchReconnectAccount")

        assertTrue(
            reload.contains(
                "Intent.makeRestartActivityTask(componentName)",
            ),
        )
        assertTrue(reload.contains("startActivity("))
        assertFalse(reload.contains("finish()"))
        assertFalse(reload.contains("recreate()"))
    }

    @Test
    fun firstRunSetupIsANeutralStepAndExampleIsClearlyStatic() {
        val screen = File(
            "src/main/java/app/subshell/prototype/ui/screens/RedditOnboardingScreen.kt",
        ).readText()

        assertTrue(screen.contains("Setup required before sign-in"))
        assertTrue(screen.contains("Show me how to get a Client ID"))

        val intro = screen
            .substringAfter("private fun RedditOnboardingIntro(")
            .substringBefore("private fun RedditClientIdGuide(")

        // Owner 2026-10-13 (M3225_SETUP_CARD_IS_NOT_AN_ERROR_V1): the setup card is
        // neutral; no error icon, border or colour.
        assertTrue(intro.contains("M3225_SETUP_CARD_IS_NOT_AN_ERROR_V1"))
        assertFalse(intro.contains("Symbols.Error"))
        assertFalse(intro.contains(".error"))

        assertTrue(screen.contains("EXAMPLE ONLY — NOT A REAL EMAIL"))
        assertTrue(screen.contains("What to look for in Reddit's email"))
        assertTrue(screen.contains("your Client ID appears here"))
        assertFalse(screen.contains("You've authorized a new app in your Reddit account"))
        assertFalse(screen.contains("copy this value"))
    }
}
