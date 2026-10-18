package app.subshell.prototype.ui

import android.app.Application
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.backup.SettingsSnapshot
import app.subshell.prototype.backup.SettingsSnapshotCodec
import app.subshell.prototype.backup.SnapshotKind
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.SystemMotionEffect
import app.subshell.prototype.ui.app.systemRemovesAnimations
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.screens.settingsSearchResult
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SETTINGSREVIEW01 (owner 2026-10-17, `reports/2026-10-17-settings-review.md`):
 * the onboarding side moves the menu, empty preferences load exactly the
 * defaults (dynamic colour on), the muted-community view travels in backups,
 * reduced motion follows Android's "Remove animations", and the Backup
 * screen's choices are searchable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettingsReview01Test {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun launchFreshInstall(): Pair<SubshellState, android.content.SharedPreferences> {
        ensureWorkManagerInitialized(context)
        val preferences = context.getSharedPreferences("subshell_preferences", 0)
        assertFalse(preferences.contains(SubshellSettings.INTERACTION_SIDE))
        var state: SubshellState? = null
        composeRule.setContent {
            state = rememberSubshellState().also { current ->
                SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = current)
            }
        }
        composeRule.waitForIdle()
        return requireNotNull(state) to preferences
    }

    @Test
    fun choosingLeftInTheOnboardingMovesTheMenuToTheLeft() {
        val (state, preferences) = launchFreshInstall()
        assertTrue(state.commentActionOnboardingVisible)

        composeRule.runOnIdle {
            state.previewInteractionSide(InteractionSide.LEFT)
            state.confirmInteractionSide()
        }
        composeRule.waitForIdle()

        assertEquals(InteractionSide.LEFT, state.resolvedDrawerSide)
        assertEquals(DrawerSidePreference.LEFT, state.drawerSidePreference)
        assertEquals("LEFT", preferences.getString(SubshellSettings.DRAWER_SIDE_PREFERENCE, null))
        assertEquals(CommentActionPlacement.LEFT, state.commentActionPlacement)
    }

    @Test
    fun emptyPreferencesLoadExactlyTheDefaultsWithDynamicColourOn() {
        val (state, _) = launchFreshInstall()

        assertTrue(DefaultSubshellSettings.dynamicColor)
        // The first composition makes the drawer side explicit (FOLLOW -> RIGHT).
        assertEquals(
            DefaultSubshellSettings.copy(
                drawerSidePreference = DrawerSidePreference.RIGHT,
                inboxNotificationsEnabled = INBOX_NOTIFICATIONS_ON_BY_DEFAULT,
            ),
            state.captureSettings(),
        )
    }

    @Test
    fun theMutedCommunityViewTravelsInABackupAndAnOlderBackupKeepsTheCurrentOne() {
        val backedUp = SubshellSettings(mutedCommunityDirectViewMode = MutedCommunityDirectViewMode.ABOUT_ONLY)
        val current = SubshellSettings(mutedCommunityDirectViewMode = MutedCommunityDirectViewMode.ABOUT_ONLY)

        assertTrue(SubshellSettings.MUTED_COMMUNITY_DIRECT_VIEW_MODE in SubshellSettings.storageKeys)
        val newer = SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(snapshot(backedUp)))
        assertEquals(
            MutedCommunityDirectViewMode.ABOUT_ONLY,
            newer.settingsRestoredOver(SubshellSettings()).mutedCommunityDirectViewMode,
        )

        val older = SettingsSnapshotCodec.decode(withoutMutedCommunityView(SettingsSnapshotCodec.encode(snapshot(SubshellSettings()))))
        assertEquals(
            MutedCommunityDirectViewMode.ABOUT_ONLY,
            older.settingsRestoredOver(current).mutedCommunityDirectViewMode,
        )
    }

    @Test
    fun aRestoreAppliesTheMutedCommunityViewToTheLiveState() {
        val source = SubshellState(initialInteractionSideConfigured = true)
        source.updateMutedCommunityDirectViewMode(MutedCommunityDirectViewMode.ABOUT_ONLY)
        val captured = source.captureSettings()
        assertEquals(MutedCommunityDirectViewMode.ABOUT_ONLY, captured.mutedCommunityDirectViewMode)

        val target = SubshellState(initialInteractionSideConfigured = true)
        target.restoreSettings(captured)
        assertEquals(MutedCommunityDirectViewMode.ABOUT_ONLY, target.mutedCommunityDirectViewMode)
    }

    @Test
    fun onlyAnAnimatorScaleOfZeroRemovesAnimations() {
        assertTrue(systemRemovesAnimations(0f))
        assertFalse(systemRemovesAnimations(0.5f))
        assertFalse(systemRemovesAnimations(1f))
    }

    @Test
    fun theAppReadsAndroidsRemoveAnimations() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val (state, _) = launchFreshInstall()

        assertTrue(state.systemRemovesAnimations)
        assertTrue(state.motionReduced)
        assertFalse(state.reduceMotion)
    }

    @Test
    fun androidsRemoveAnimationsReducesMotionAndTheRowSaysWhy() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        ensureWorkManagerInitialized(context)
        val state = SubshellState(initialInteractionSideConfigured = true)
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SystemMotionEffect(state)
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = SettingsDestination.APPEARANCE.name,
                )
            }
        }
        composeRule.waitForIdle()

        assertFalse(state.reduceMotion)
        assertTrue(state.systemRemovesAnimations)
        assertTrue(state.motionReduced)
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText("On because Android's Remove animations is on"))
        val row = composeRule.onAllNodes(
            hasText("On because Android's Remove animations is on", substring = true) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled),
            useUnmergedTree = false,
        ).fetchSemanticsNodes()
        assertEquals(1, row.size)
    }

    @Test
    fun theBackupScreensChoicesLeadToTheBackupRow() {
        listOf("post drafts", "muted communities", "retention", "private notes", "moderator responses").forEach { query ->
            assertTrue(query, "backup" in settingsSearchResult(query).itemKeys)
        }
        assertTrue("reduced-motion" in settingsSearchResult("remove animations").itemKeys)
    }

    private fun snapshot(settings: SubshellSettings) = SettingsSnapshot(
        id = "backup-1",
        name = "Backup",
        createdAtEpochMillis = 1L,
        appVersion = "0.82-beta",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = settings,
    )

    private fun withoutMutedCommunityView(encoded: String): String {
        val root = Json.parseToJsonElement(encoded).jsonObject
        val settings = root.getValue("settings").jsonObject
        assertTrue(SubshellSettings.MUTED_COMMUNITY_DIRECT_VIEW_MODE in settings.keys)
        return JsonObject(
            root + ("settings" to JsonObject(settings - SubshellSettings.MUTED_COMMUNITY_DIRECT_VIEW_MODE)),
        ).toString()
    }
}
