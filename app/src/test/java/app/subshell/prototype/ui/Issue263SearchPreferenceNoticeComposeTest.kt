package app.subshell.prototype.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.NsfwAccountPreferences
import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.ui.app.NSFW_ACCOUNT_PREFERENCE_SCOPE
import app.subshell.prototype.ui.app.NsfwAccountPreferenceNotice
import app.subshell.prototype.ui.app.nsfwSearchPreferenceNeedsNotice
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE263_NSFW_SEARCH_PREFERENCE_NOTICE_V1 (#263, the reader's finding): with
 * mature content on but NSFW search results off, Reddit leaves NSFW out of
 * search and Random NSFW community finds none; the tray says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue263SearchPreferenceNoticeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun known(over18: Boolean, search: Boolean?) =
        NsfwPreferencesResult.Known(NsfwAccountPreferences(over18 = over18, searchIncludeOver18 = search))

    @Test
    fun onlyMatureContentOnWithSearchOffAsksForTheSearchNotice() {
        assertTrue(nsfwSearchPreferenceNeedsNotice(known(over18 = true, search = false)))
        assertFalse("Reddit did not say", nsfwSearchPreferenceNeedsNotice(known(over18 = true, search = null)))
        assertFalse(nsfwSearchPreferenceNeedsNotice(known(over18 = true, search = true)))
        assertFalse("the mature-content notice comes first", nsfwSearchPreferenceNeedsNotice(known(over18 = false, search = false)))
        assertFalse(nsfwSearchPreferenceNeedsNotice(NsfwPreferencesResult.Failed))
    }

    @Test
    fun theTraySaysSearchLeavesNsfwOut() {
        assertEquals("Your Reddit account leaves NSFW out of search", noticeTitleFor(known(over18 = true, search = false)))
    }

    @Test
    fun matureContentOffKeepsItsOwnNotice() {
        assertEquals("Your Reddit account hides NSFW content", noticeTitleFor(known(over18 = false, search = false)))
    }

    @Test
    fun bothOnSaysNothing() {
        assertNull(noticeTitleFor(known(over18 = true, search = true)))
    }

    private fun noticeTitleFor(result: NsfwPreferencesResult): String? {
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
        val host = ViewportErrorHostState()
        composeRule.setContent {
            NsfwAccountPreferenceNotice(state = state, readPreferences = { result }, noticeHost = host)
        }
        composeRule.waitForIdle()
        val request = host.currentRequest ?: return null
        assertEquals(NSFW_ACCOUNT_PREFERENCE_SCOPE, request.scopeKey)
        return request.presentation.title
    }
}
