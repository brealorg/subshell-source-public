package io.github.brealorg.subshell.update

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import io.github.brealorg.subshell.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE291_UPDATE_DIALOG_UPDATES_IN_THE_APP_V1 (#291): the update dialog's main
 * button is Update (it leads to Settings > Updates), not the GitHub page; View
 * release is the second choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue291UpdateDialogUpdatesInAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val release = UpdateManifestRelease(
        schemaVersion = 1,
        channel = "beta",
        versionCode = 300,
        versionName = "9.99-beta",
        publishedAt = "2026-11-13T00:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v9.99-beta",
        apkSha256 = "a".repeat(64),
        headline = "A release",
        highlights = listOf("One"),
    )

    @Test
    fun updateLeadsIntoTheAppAndViewReleaseOpensThePage() {
        var updated = 0
        var viewed = 0
        composeRule.setContent {
            SubshellTheme {
                UpdateAvailableDialog(
                    release,
                    onDismiss = {},
                    onViewRelease = { viewed++ },
                    onUpdate = { updated++ },
                )
            }
        }

        composeRule.onNodeWithText("Update").performClick()
        assertEquals(1, updated)
        assertEquals(0, viewed)
        composeRule.onNodeWithText("View release").performScrollTo().performClick()
        assertEquals(1, viewed)
        assertEquals(1, updated)
    }

    @After
    fun clearStore() {
        composeRule.activity.getSharedPreferences(UpdateStateStore.PREFERENCES_NAME, 0).edit().clear().commit()
    }

    @Test
    fun theHostOpensTheUpdatesPageAndMarksTheUpdateShown() {
        val newer = release.copy(versionCode = BuildConfig.VERSION_CODE + 1)
        val store = UpdateStateStore(composeRule.activity)
        store.saveAvailableRelease(newer)
        var opened = 0
        composeRule.setContent {
            SubshellTheme { UpdatePresentationHost(automaticPresentationEnabled = true, onOpenUpdates = { opened++ }) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Update").performClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
        assertEquals(newer.versionCode, UpdateStartRequest.take())
        composeRule.onAllNodesWithText("subshell update available").assertCountEquals(0)
        assertEquals(newer.versionCode, store.lastPresentedUpdateVersionCode())
    }

    @Test
    fun theUpdatesPageStartsTheRequestedUpdateAsItOpens() {
        val newer = release.copy(versionCode = BuildConfig.VERSION_CODE + 1)
        UpdateStateStore(composeRule.activity).saveAvailableRelease(newer)
        UpdateStartRequest.request(newer.versionCode)
        composeRule.setContent {
            SubshellTheme { UpdatesScreen(onBack = {}, runtimeEnabled = true) }
        }
        composeRule.waitForIdle()

        // Without Android's permission the page asks for it, as its own button does.
        composeRule.onNodeWithText("Allow subshell to install its update").assertExists()
        assertEquals(0, UpdateStartRequest.take())
    }

    @Test
    fun theAppRoutesThatToSettingsUpdates() {
        val sheets = java.io.File("src/main/java/io/github/brealorg/subshell/ui/app/AppSheetsHost.kt").readText()
        val call = sheets.substringAfter("UpdatePresentationHost(").substringBefore("\n            )\n")
        assertTrue(call.contains("SettingsDestination.UPDATES.name"))
        assertTrue(call.contains("state.openSecondary(SecondaryDestination.SETTINGS)"))
    }
}
