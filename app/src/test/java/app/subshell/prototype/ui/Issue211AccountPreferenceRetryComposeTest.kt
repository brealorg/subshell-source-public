package app.subshell.prototype.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.NsfwAccountPreferences
import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.ui.app.NSFW_ACCOUNT_PREFERENCE_SCOPE
import app.subshell.prototype.ui.app.NsfwAccountPreferenceNotice
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE211_ACCOUNT_PREFERENCE_READ_RETRIES_V1 (#211): on the owner's phone the
 * over_18 read failed at a cold start without network, and the notice was
 * skipped for the whole run. A failed read is tried again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue211AccountPreferenceRetryComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aFailedReadIsTriedAgainAndThenShowsTheNotice() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.updateShowNsfwPosts(true)
        val answers = ArrayDeque(
            listOf(
                NsfwPreferencesResult.Failed,
                NsfwPreferencesResult.Known(NsfwAccountPreferences(over18 = false, searchIncludeOver18 = null)),
            ),
        )
        var reads = 0
        val host = ViewportErrorHostState()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NsfwAccountPreferenceNotice(
                state = state,
                readPreferences = { reads += 1; answers.removeFirst() },
                noticeHost = host,
            )
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, reads)
        assertNull(host.currentRequest)

        composeRule.mainClock.advanceTimeBy(15_000)
        assertEquals(2, reads)
        assertEquals(NSFW_ACCOUNT_PREFERENCE_SCOPE, host.currentRequest?.scopeKey)
    }
}
