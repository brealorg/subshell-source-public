package app.subshell.prototype.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE270 (owner 2026-10-27): the Backup & restore screen says what a backup
 * holds, shows the privacy-sensitive switches without another tap, and lists
 * the three kinds of backup under tabs, each with its own switch and limit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue270BackupScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val tray = ViewportErrorHostState()

    private fun showScreen(): SettingsBackupRepository {
        ensureWorkManagerInitialized(application)
        application.getSharedPreferences("subshell_backup_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val repository = SettingsBackupRepository(application, appVersion = "test")
        repository.saveCurrent(name = "Trip setup", settings = SubshellSettings())
        repository.setAutomaticHistoryEnabled(true)
        repository.recordSettingsChange(settings = SubshellSettings(showNsfwMedia = true))
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalViewportErrorHostState provides tray) {
                    BackupRestoreScreen(state = SubshellState(), repository = repository, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        // The list sits below the contents; wait until the backups are read.
        composeRule.waitUntil(10_000) {
            runCatching { scrollTo("Trip setup") }.isSuccess && has("Trip setup")
        }
        return repository
    }

    private fun has(text: String, substring: Boolean = false) =
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText(text, substring = true))
    }

    private fun canScrollTo(text: String) = runCatching { scrollTo(text) }.isSuccess &&
        has(text, substring = true)

    private fun openTab(label: String) {
        scrollTo(label)
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theScreenSaysWhatIsBackedUpAndShowsThePrivacySwitches() {
        showScreen()
        scrollTo("Back up now")
        assertTrue(has("Back up now"))
        assertTrue(has("Restore from file"))
        scrollTo("What's backed up")
        assertTrue(has("Everything you choose in Settings"))
        scrollTo("Privacy-sensitive backup data")
        listOf(
            "Hidden communities",
            "Muted communities",
            "Post drafts",
            "Saved moderator responses",
            "Private notes & tags",
        ).forEach { title ->
            scrollTo(title)
            val switches = composeRule.onAllNodes(
                hasText(title) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
            ).fetchSemanticsNodes()
            assertEquals(title, 1, switches.size)
        }
        // ISSUE270_BACKUP_NOTICES_USE_THE_TRAY_V1: the answer shows in the app's tray.
        scrollTo("Muted communities")
        composeRule.onNode(
            hasText("Muted communities") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        ).performClick()
        composeRule.waitUntil(10_000) { tray.currentRequest != null }
        assertEquals("Muted communities will be backed up", tray.currentRequest?.presentation?.title)
        scrollTo("Never backed up")
        assertTrue(has("sign-ins and your Reddit Client ID", substring = true))
    }

    @Test
    fun eachTabListsItsOwnKindWithItsOwnChoices() {
        showScreen()
        scrollTo("Trip setup")
        assertTrue(has("Trip setup"))
        assertTrue(!has("Back up automatically"))
        assertTrue("an automatic backup is not under Yours", !canScrollTo("Automatic backup"))

        openTab("Automatic")
        scrollTo("Back up automatically")
        assertTrue(has("Keep the newest"))
        assertTrue(has("Automatic backup"))
        assertTrue("old automatic name is not shown", !has("Automatic settings history"))
        assertTrue("your backup is not under Automatic", !canScrollTo("Trip setup"))

        openTab("Undo")
        assertTrue(has("Before every restore, subshell backs up your current setup here", substring = true))
        assertTrue(has("Nothing here yet"))
        assertTrue(!has("Back up automatically"))
    }
}
