package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedManageFailureKind
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.ui.app.switchToNotifiedAccount
import app.subshell.prototype.ui.error.customFeedManageErrorPresentation
import app.subshell.prototype.ui.screens.composerTitleOnOneLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUX1: LOWUX1_NOTIFICATION_SWITCHES_TO_ITS_ACCOUNT_V1 (OCB-111),
 * LOWUX1_FEED_DELETE_FAILURE_SAYS_DELETE_V1 (OCB-187),
 * LOWUX1_EMPTY_FILTER_VALUE_IS_REFUSED_V1 (OCB-192),
 * LOWUX1_PASTED_TITLE_LINES_BECOME_SPACES_V1 (OCB-206).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowUx1Test {
    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:$name",
        username = name,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun aNotificationOfAnotherSignedInAccountSwitchesToIt() {
        val alice = account("alice")
        val bob = account("bob")
        val state = SubshellState(
            initialAccountSessions = listOf(alice, bob),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        )

        assertTrue(switchToNotifiedAccount(state, bob.stableId))
        assertEquals(bob.stableId, state.activeAccountId)
        // An account no longer signed in here is not switched to.
        assertFalse(switchToNotifiedAccount(state, "reddit:gone"))
        assertEquals(bob.stableId, state.activeAccountId)
    }

    @Test
    fun aFailedFeedDeleteSaysDelete() {
        val failed = CustomFeedsUiState(manageFailureKind = CustomFeedManageFailureKind.HTTP)

        assertEquals("Couldn't delete custom feed", customFeedManageErrorPresentation(failed, deleting = true).title)
        assertEquals("Couldn't update custom feed", customFeedManageErrorPresentation(failed).title)
    }

    @Test
    fun aFilterValueThatNormalisesToNothingIsRefused() {
        assertEquals(
            ContentFilterDraftError.VALUE,
            contentFilterDraftError(ContentFilterType.DOMAIN, "https://", draftCommunity = ""),
        )
    }

    @Test
    fun aPastedTwoLineTitleBecomesOneLine() {
        assertEquals("Breaking news from the coast", composerTitleOnOneLine("Breaking news\r\nfrom the coast"))
    }
}
