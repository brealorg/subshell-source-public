package app.subshell.prototype.update

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1 (owner 2026-10-16): a release can tell
 * readers on a version with a known problem, and the dialog comes back once a
 * day until they update. Older clients keep reading the manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Update09KnownBadVersionAdvisoryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val advisory = UpdateAdvisory(affectedVersionCodes = listOf(245), message = "0.81 can lose a draft.")
    private val release = UpdateManifestRelease(
        schemaVersion = 1,
        channel = "beta",
        versionCode = 246,
        versionName = "0.82-beta",
        publishedAt = "2026-10-20T00:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.82-beta",
        apkSha256 = "a".repeat(64),
        headline = "Subshell 0.82",
        advisory = advisory,
    )

    @After
    fun clearStore() {
        composeRule.activity.getSharedPreferences(UpdateStateStore.PREFERENCES_NAME, 0).edit().clear().commit()
    }

    /** The manifest model of 0.77–0.81: the nine fields, no advisory. */
    @Serializable
    private data class ReleaseBeforeAdvisories(
        val schemaVersion: Int,
        val channel: String,
        val versionCode: Int,
        val versionName: String,
        val publishedAt: String,
        val releaseUrl: String,
        val apkSha256: String,
        val headline: String,
        val highlights: List<String> = emptyList(),
    )

    @Test
    fun anOlderClientStillReadsAManifestWithAnAdvisory() {
        val json = SubshellNetworkJson.providerJson.encodeToString(UpdateManifestRelease.serializer(), release)
        assertTrue(json.contains("\"advisory\""))
        val older = SubshellNetworkJson.providerJson.decodeFromString(ReleaseBeforeAdvisories.serializer(), json)
        assertEquals(246, older.versionCode)
        assertEquals(1, older.schemaVersion)
        val withoutAdvisory = SubshellNetworkJson.providerJson.decodeFromString(
            UpdateManifestRelease.serializer(),
            SubshellNetworkJson.providerJson.encodeToString(ReleaseBeforeAdvisories.serializer(), older),
        )
        assertNull(withoutAdvisory.advisory)
    }

    @Test
    fun aMalformedAdvisoryIsDroppedButTheReleaseStays() {
        assertEquals(advisory, normalizeUpdateManifest(release)?.advisory)
        listOf(
            advisory.copy(message = " "),
            advisory.copy(message = "M".repeat(UPDATE09_MAX_ADVISORY_CHARS + 1)),
            advisory.copy(affectedVersionCodes = listOf(246, 300, 0)),
            advisory.copy(affectedVersionCodes = (1..33).toList()),
        ).forEach { bad ->
            val normalized = normalizeUpdateManifest(release.copy(advisory = bad))
            assertNotNull(normalized)
            assertNull(bad.toString(), normalized?.advisory)
        }
        assertEquals(
            listOf(244, 245),
            normalizeUpdateManifest(release.copy(advisory = advisory.copy(affectedVersionCodes = listOf(244, 245, 245, 400))))
                ?.advisory?.affectedVersionCodes,
        )
    }

    @Test
    fun theAdvisoryConcernsOnlyTheVersionsItNames() {
        assertEquals(advisory, release.advisoryFor(245))
        assertNull(release.advisoryFor(244))
        assertNull("the fixed version itself", release.advisoryFor(246))
    }

    @Test
    fun anAdvisedUpdateComesBackOnceADayAPlainOneOnlyOnce() {
        val day = UPDATE09_ADVISORY_REPEAT_INTERVAL_MILLIS
        val shownAt = 1_000_000L
        fun due(release: UpdateManifestRelease, current: Int, now: Long) = shouldAutoPresentUpdate(
            release = release,
            currentVersionCode = current,
            lastPresentedVersionCode = 246,
            lastPresentedAtEpochMillis = shownAt,
            nowEpochMillis = now,
        )

        assertFalse(due(release, current = 245, now = shownAt + day - 1))
        assertTrue(due(release, current = 245, now = shownAt + day))
        assertTrue("a clock turned back", due(release, current = 245, now = shownAt - 1))
        assertFalse("not affected", due(release, current = 244, now = shownAt + day))
        assertFalse("plain release", due(release.copy(advisory = null), current = 245, now = shownAt + 10 * day))
    }

    @Test
    fun theStoreRemembersWhenTheDialogWasShownAndKeepsTheAdvisory() {
        val store = UpdateStateStore(composeRule.activity)
        store.markUpdatePresented(246, nowEpochMillis = 42L)
        assertEquals(42L, store.lastUpdatePresentedAtEpochMillis())
        store.saveAvailableRelease(release)
        assertEquals(advisory, store.availableRelease(245)?.advisory)
    }

    @Test
    fun theNotificationNamesTheProblem() {
        assertEquals("Known problem in your subshell version", updateNotificationTitle(release, 245))
        assertEquals("0.82-beta fixes it", updateNotificationText(release, 245))
        assertEquals("subshell update available", updateNotificationTitle(release, 244))
        assertEquals("0.82-beta is ready", updateNotificationText(release, 244))
    }

    @Test
    fun theDialogPutsTheProblemFirst() {
        composeRule.setContent {
            SubshellTheme {
                UpdateAvailableDialog(release, onDismiss = {}, onViewRelease = {}, advisory = release.advisoryFor(245))
            }
        }
        composeRule.onNodeWithText("Known problem in your version").assertIsDisplayed()
        composeRule.onNodeWithText("0.81 can lose a draft.").assertIsDisplayed()
        composeRule.onNodeWithText("0.82-beta fixes it").assertIsDisplayed()
    }

    @Test
    fun theHostBringsAnAdvisedUpdateBackAfterADayButNotSooner() {
        val installed = BuildConfig.VERSION_CODE
        val advised = release.copy(
            versionCode = installed + 1,
            advisory = advisory.copy(affectedVersionCodes = listOf(installed)),
        )
        val store = UpdateStateStore(composeRule.activity)
        store.saveAvailableRelease(advised)
        val now = System.currentTimeMillis()
        store.markUpdatePresented(installed + 1, nowEpochMillis = now - UPDATE09_ADVISORY_REPEAT_INTERVAL_MILLIS - 60_000L)

        composeRule.setContent { SubshellTheme { UpdatePresentationHost(automaticPresentationEnabled = true) } }
        composeRule.onNodeWithText("Known problem in your version").assertIsDisplayed()
        composeRule.onNodeWithText("0.81 can lose a draft.").assertIsDisplayed()
    }

    @Test
    fun theHostWaitsADayBeforeShowingItAgain() {
        val installed = BuildConfig.VERSION_CODE
        val store = UpdateStateStore(composeRule.activity)
        store.saveAvailableRelease(
            release.copy(versionCode = installed + 1, advisory = advisory.copy(affectedVersionCodes = listOf(installed))),
        )
        store.markUpdatePresented(installed + 1, nowEpochMillis = System.currentTimeMillis() - 60_000L)

        composeRule.setContent { SubshellTheme { UpdatePresentationHost(automaticPresentationEnabled = true) } }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Known problem in your version"))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun withoutAnAdvisoryTheDialogIsUnchanged() {
        composeRule.setContent {
            SubshellTheme { UpdateAvailableDialog(release, onDismiss = {}, onViewRelease = {}) }
        }
        composeRule.onNodeWithText("subshell update available").assertIsDisplayed()
        composeRule.onNodeWithText("0.82-beta").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(androidx.compose.ui.test.hasText("0.81 can lose a draft.")).fetchSemanticsNodes().isEmpty())
    }
}
