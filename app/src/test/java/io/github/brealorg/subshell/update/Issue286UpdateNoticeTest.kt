package io.github.brealorg.subshell.update

import android.app.Application
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.app.UPDATE_AVAILABLE_NOTICE_SCOPE
import io.github.brealorg.subshell.ui.app.UPDATE_NOTICE_HIDDEN_FOR_MILLIS
import io.github.brealorg.subshell.ui.app.UpdateAvailableNotice
import io.github.brealorg.subshell.ui.app.updateNoticeShows
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE286_UPDATE_NOTICE_COMES_BACK_AFTER_A_DAY_V1: after the update dialog,
 * the tray offers the newer version; closing it hides it for a day, a newer
 * release brings it back at once, and Update opens Settings › Updates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue286UpdateNoticeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = UpdateStateStore(context)
    private val day = UPDATE_NOTICE_HIDDEN_FOR_MILLIS
    private val start = 1_800_000_000_000L

    private fun release(versionCode: Int = BuildConfig.VERSION_CODE + 1) = UpdateManifestRelease(
        schemaVersion = UPDATE01_MANIFEST_SCHEMA_VERSION,
        channel = UPDATE01_BETA_CHANNEL,
        versionCode = versionCode,
        versionName = "9.$versionCode-beta",
        publishedAt = "2026-11-02T10:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v9.$versionCode-beta",
        apkSha256 = "a".repeat(64),
        headline = "A newer subshell",
    )

    @Before
    fun clean() {
        context.getSharedPreferences(UpdateStateStore.PREFERENCES_NAME, 0).edit().clear().commit()
        UpdatePresentationCoordinator.clear()
    }

    @After
    fun tidy() = clean()

    @Test
    fun theNoticeFollowsTheDialogAndADay() {
        val next = release(300)
        fun shows(presented: Int, dismissed: Int, at: Long, now: Long) =
            updateNoticeShows(next, 299, presented, dismissed, at, now)

        assertFalse("not before the dialog", shows(presented = 0, dismissed = 0, at = 0L, now = start))
        assertTrue(shows(presented = 300, dismissed = 0, at = 0L, now = start))
        assertFalse("closed an hour ago", shows(300, 300, start, start + day / 24))
        assertTrue("a day later", shows(300, 300, start, start + day))
        assertTrue("a newer release than the closed one", shows(300, 299, start, start + 1))
        assertTrue("the clock went back", shows(300, 300, start, start - 1))
        assertFalse("already installed", updateNoticeShows(next, 300, 300, 0, 0L, start))
        assertFalse(updateNoticeShows(null, 299, 300, 0, 0L, start))
    }

    @Test
    fun closingTheNoticeHidesItForADayAndUpdateOpensUpdates() {
        val next = release()
        store.saveAvailableRelease(next)
        store.markUpdatePresented(next.versionCode, start)
        val host = ViewportErrorHostState()
        val now = mutableLongStateOf(start)
        var opened = 0
        composeRule.setContent {
            // A new clock value is a new composition of the effect, as a return to the app is.
            val clock = now.longValue
            androidx.compose.runtime.key(clock) {
                UpdateAvailableNotice(
                    enabled = true,
                    noticeHost = host,
                    onOpenUpdates = { opened++ },
                    clock = { clock },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(UPDATE_AVAILABLE_NOTICE_SCOPE, host.currentRequest?.scopeKey)
        assertEquals("subshell ${next.versionName} is ready", host.currentRequest?.presentation?.title)

        host.dismissCurrent()
        assertEquals(next.versionCode, store.updateNoticeDismissedVersionCode())
        assertEquals(start, store.updateNoticeDismissedAtEpochMillis())

        now.longValue = start + day / 2
        composeRule.waitForIdle()
        assertNull(host.currentRequest)

        now.longValue = start + day
        composeRule.waitForIdle()
        assertEquals(UPDATE_AVAILABLE_NOTICE_SCOPE, host.currentRequest?.scopeKey)

        host.retryCurrent()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        assertNull(host.currentRequest)
        assertEquals(start + day, store.updateNoticeDismissedAtEpochMillis())
    }

    @Test
    fun noNoticeWhileTheFirstRunSetupIsOpen() {
        val next = release()
        store.saveAvailableRelease(next)
        store.markUpdatePresented(next.versionCode, start)
        val host = ViewportErrorHostState()
        composeRule.setContent {
            UpdateAvailableNotice(enabled = false, noticeHost = host, onOpenUpdates = {}, clock = { start })
        }
        composeRule.waitForIdle()
        assertNull(host.currentRequest)
    }
}
