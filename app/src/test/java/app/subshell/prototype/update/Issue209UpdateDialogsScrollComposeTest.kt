package app.subshell.prototype.update

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE209_UPDATE_DIALOGS_SCROLL_V1 (#209): with long highlights in a short
 * window, the end of the update and What's new dialogs can be scrolled to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h400dp")
class Issue209UpdateDialogsScrollComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val highlights = (1..6).map { index ->
        "Highlight $index: " + "a longer sentence about what changed in this release ".repeat(3).trim()
    }

    @Test
    fun theUpdateDialogScrollsToTheApkHash() {
        val release = UpdateManifestRelease(
            schemaVersion = 1,
            channel = "beta",
            versionCode = 300,
            versionName = "0.80.0-beta.1",
            publishedAt = "2026-10-08",
            releaseUrl = "https://example.com/release",
            apkSha256 = "a".repeat(64),
            headline = "A release with a lot to say",
            highlights = highlights,
        )
        composeRule.setContent {
            SubshellTheme { UpdateAvailableDialog(release, onDismiss = {}, onViewRelease = {}) }
        }

        composeRule.onNodeWithText(updateApkHashLine(release)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun whatsNewScrollsToItsFifthHighlight() {
        val release = UpdateChangelogRelease(
            versionCode = 300,
            versionName = "0.80.0-beta.1",
            date = "2026-10-08",
            headline = "A release with a lot to say",
            highlights = highlights,
        )
        composeRule.setContent {
            SubshellTheme { WhatsNewDialog(release, onDismiss = {}, onViewChangelog = {}) }
        }

        composeRule.onNodeWithText("Highlight 5", substring = true).performScrollTo().assertIsDisplayed()
    }
}
