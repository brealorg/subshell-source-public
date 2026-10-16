package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.app.SETTINGS_REVIEW_NUDGE_DELAY_MS
import app.subshell.prototype.ui.app.SETTINGS_REVIEW_NUDGE_SCOPE
import app.subshell.prototype.ui.app.SettingsReviewNudge
import app.subshell.prototype.ui.app.SettingsReviewNudgeStep
import app.subshell.prototype.ui.app.SettingsReviewNudgeStore
import app.subshell.prototype.ui.app.settingsReviewNudgeStep
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings audit (owner 2026-10-13): SETTINGSAUDIT01_DEFAULTS_V1,
 * SETTINGSAUDIT01_BACKUP_CLEANUP_V1, SETTINGSAUDIT01_REVIEW_SETTINGS_NUDGE_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettingsAudit01Test {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun theNewDefaultsAreTheSameInSettingsAndInANewState() {
        val defaults = SubshellSettings()
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertTrue(defaults.showUnreadMarker)
        assertFalse(defaults.swipeActionsEnabled)
        assertEquals(PostSwipeAction.UPVOTE, defaults.postSwipeLeftAction)
        assertEquals(PostSwipeAction.DOWNVOTE, defaults.postSwipeRightAction)
        assertEquals(CommentSwipeAction.UPVOTE, defaults.commentSwipeLeftAction)
        assertEquals(CommentSwipeAction.DOWNVOTE, defaults.commentSwipeRightAction)

        val state = SubshellState()
        assertEquals(defaults.themeMode, state.themeMode)
        assertEquals(defaults.showUnreadMarker, state.showUnreadMarker)
        assertEquals(defaults.postSwipeLeftAction, state.postSwipeLeftAction)
        assertEquals(defaults.postSwipeRightAction, state.postSwipeRightAction)
        assertEquals(defaults.commentSwipeLeftAction, state.commentSwipeLeftAction)
        assertEquals(defaults.commentSwipeRightAction, state.commentSwipeRightAction)
    }

    @Test
    fun aNewBackupLeavesTheRetiredKeysOutAndAnOlderOneStillRestores() {
        val written = SubshellSettings().toStorageMap()
        SubshellSettings.RETIRED_SETTING_KEYS.forEach { key -> assertNull(written[key]) }

        // A backup from an older build: the retired keys with the values that
        // build wrote, plus a debug Feed preview and a real choice.
        val older = SubshellSettings(themeMode = ThemeMode.DARK).toStorageMap() + mapOf(
            SubshellSettings.DISCOVER_EXPANDED to "false",
            SubshellSettings.SUBS_EXPLORE_SHORTCUT_MODE to "AUTO",
            SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES to "false",
            SubshellSettings.FEED_PREVIEW_STATE to "OFFLINE",
        )
        val (readable, unread) = SubshellSettings.splitBackupSettingsValues(older)

        assertTrue(unread.isEmpty())
        SubshellSettings.RETIRED_SETTING_KEYS.forEach { key -> assertFalse(key in readable) }
        assertEquals(ThemeMode.DARK, SubshellSettings.fromStorageMap(readable).themeMode)
        assertFalse(
            SubshellSettings.includedStorageKeys(readable).any(SubshellSettings.RETIRED_SETTING_KEYS::contains),
        )
    }

    @Test
    fun theNudgeWaitsThreeDaysOnANewInstallAndNeverComesToAnExistingOne() {
        val now = 1_800_000_000_000L
        assertEquals(
            SettingsReviewNudgeStep.MARK_EXISTING_INSTALL,
            settingsReviewNudgeStep(done = false, firstRunAtEpochMillis = null, nowEpochMillis = now, newInstall = false),
        )
        assertEquals(
            SettingsReviewNudgeStep.START_CLOCK,
            settingsReviewNudgeStep(done = false, firstRunAtEpochMillis = null, nowEpochMillis = now, newInstall = true),
        )
        assertEquals(
            SettingsReviewNudgeStep.WAIT,
            settingsReviewNudgeStep(
                done = false,
                firstRunAtEpochMillis = now - SETTINGS_REVIEW_NUDGE_DELAY_MS + 1,
                nowEpochMillis = now,
                newInstall = false,
            ),
        )
        assertEquals(
            SettingsReviewNudgeStep.SHOW,
            settingsReviewNudgeStep(
                done = false,
                firstRunAtEpochMillis = now - SETTINGS_REVIEW_NUDGE_DELAY_MS,
                nowEpochMillis = now,
                newInstall = false,
            ),
        )
        assertEquals(
            SettingsReviewNudgeStep.NOTHING,
            settingsReviewNudgeStep(done = true, firstRunAtEpochMillis = 0L, nowEpochMillis = now, newInstall = true),
        )
    }

    @Test
    fun theNudgeShowsOnceInTheTrayAndOpensSettings() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val now = 1_800_000_000_000L
        SettingsReviewNudgeStore.preferences(context).edit()
            .putLong("first_run_at_epoch_ms", now - SETTINGS_REVIEW_NUDGE_DELAY_MS - 1)
            .commit()
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val host = ViewportErrorHostState()
        composeRule.setContent {
            SubshellTheme { SettingsReviewNudge(state = state, noticeHost = host, clock = { now }) }
        }
        composeRule.waitForIdle()

        val request = requireNotNull(host.currentRequest)
        assertEquals(SETTINGS_REVIEW_NUDGE_SCOPE, request.scopeKey)
        assertEquals(TrayTone.INFO, request.tone)
        assertEquals("Open Settings", request.presentation.primaryAction?.label)
        composeRule.runOnIdle { requireNotNull(request.onRetry).invoke() }
        composeRule.waitForIdle()

        val route = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(SecondaryDestination.SETTINGS, route.secondaryDestination)
        assertNull(host.currentRequest)
        assertTrue(SettingsReviewNudgeStore.preferences(context).getBoolean("done", false))
    }
}
