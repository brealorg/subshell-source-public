package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageSnapshot
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.applyPostManageMutation
import app.subshell.prototype.data.network.reddit.redditPostManageMutationFormRequest
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.ui.screens.appearanceThemeLabel
import app.subshell.prototype.ui.screens.conversationDayLabel
import app.subshell.prototype.ui.screens.conversationTimeLabel
import app.subshell.prototype.ui.screens.diagnosticEventTime
import app.subshell.prototype.ui.screens.restorePreviewAccountLabel
import app.subshell.prototype.ui.screens.settingsSearchResult
import app.subshell.prototype.ui.theme.ThemeMode
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOWUX3: LOWUX3_RESTORE_PREVIEW_NAMES_ACCOUNTS_V1 (OCB-176),
 * LOWUX3_PREVIEW_BADGE_NAMES_PURE_BLACK_V1 (OCB-255),
 * LOWUX3_CONVERSATION_DATES_AND_TIMES_READ_RIGHT_V1 (OCB-200),
 * LOWUX3_DIAGNOSTIC_EVENTS_CARRY_THEIR_DATE_V1 (OCB-214),
 * LOWUX3_SETTINGS_SEARCH_SKIPS_ROWS_THE_STATE_HIDES_V1 (OCB-267/278),
 * LOWUX3_POST_FLAIR_CAN_BE_REMOVED_V1 (OCB-233).
 */
class LowUx3Test {
    private fun epoch(year: Int, month: Int, day: Int, hour: Int = 14, minute: Int = 5): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toEpochSecond()

    @Test
    fun theRestorePreviewNamesAccounts() {
        val alice = AccountSessionSummary(
            stableId = "reddit:native_production:1x2y3z",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        assertEquals("u/alice", restorePreviewAccountLabel("reddit:native_production:1x2y3z", listOf(alice)))
        assertEquals("u/alice", restorePreviewAccountLabel("Alice", listOf(alice)))
        assertEquals("another account", restorePreviewAccountLabel("reddit:native_production:gone", listOf(alice)))
    }

    @Test
    fun thePreviewBadgeNamesPureBlack() {
        assertEquals("Pure black", appearanceThemeLabel(ThemeMode.DARK, pureBlack = true))
        assertEquals("Dark", appearanceThemeLabel(ThemeMode.DARK))
    }

    @Test
    fun conversationDatesCarryTheYearAndTimesFollowTheClockSetting() {
        val now = epoch(2026, 10, 8)
        val lastYear = MessageBubble(id = "m1", sender = "a", age = "", body = "", createdEpochSeconds = epoch(2025, 3, 5))
        val thisYear = MessageBubble(id = "m2", sender = "a", age = "", body = "", createdEpochSeconds = epoch(2026, 3, 5))

        assertTrue(conversationDayLabel(lastYear, now).contains("2025"))
        assertFalse(conversationDayLabel(thisYear, now).contains("2026"))
        assertEquals("14:05", conversationTimeLabel(thisYear, use24Hour = true))
        assertTrue(conversationTimeLabel(thisYear, use24Hour = false).startsWith("2:05"))
    }

    @Test
    fun aDiagnosticEventFromAnotherDayShowsItsDate() {
        val now = epoch(2026, 10, 8, hour = 10) * 1_000L
        val today = epoch(2026, 10, 8, hour = 9) * 1_000L
        val yesterday = epoch(2026, 10, 7, hour = 9) * 1_000L

        assertNotEquals(diagnosticEventTime(today, now).length, diagnosticEventTime(yesterday, now).length)
        assertTrue(diagnosticEventTime(yesterday, now).length > diagnosticEventTime(today, now).length)
    }

    @Test
    fun aSettingTheStateHidesIsNotASearchHit() {
        val shown = settingsSearchResult("lock after")
        val hidden = settingsSearchResult("lock after", hiddenKeys = setOf("app-lock-delay", "app-lock-notifications"))

        assertTrue("app-lock-delay" in shown.itemKeys)
        assertFalse("app-lock-delay" in hidden.itemKeys)
        assertFalse(hidden.isSectionVisible("Privacy") && hidden.itemKeys.none { it.startsWith("app-lock") })
    }

    @Test
    fun postFlairCanBeRemoved() {
        val target = PostManageTarget(
            accountStableId = "reddit:me",
            postFullname = "t3_abc",
            postAuthor = "me",
            community = "subshell",
        )
        val remove = PostManageMutationRequest(target = target, operation = PostManageOperation.SET_FLAIR)
        val fields = redditPostManageMutationFormRequest(remove)!!.fields.toMap()

        assertEquals("", fields["flair_template_id"])
        val flaired = PostManageSnapshot(
            postFullname = "t3_abc",
            author = "me",
            community = "subshell",
            flairTemplateId = "tmpl",
            flairText = "Discussion",
        )
        val cleared = applyPostManageMutation(flaired, remove)
        assertEquals(null, cleared.flairTemplateId)
        assertEquals(null, cleared.flairText)
    }
}
