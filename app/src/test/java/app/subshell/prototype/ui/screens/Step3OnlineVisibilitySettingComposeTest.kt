package app.subshell.prototype.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.OnlineVisibilityConfirmation
import app.subshell.prototype.data.account.OnlineVisibilityIssue
import app.subshell.prototype.data.account.OnlineVisibilityPhase
import app.subshell.prototype.data.account.OnlineVisibilityUiState
import app.subshell.prototype.ui.components.OnlineVisibilityRuntime
import app.subshell.prototype.ui.components.OnlineVisibilitySettingRow
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (userstatus01a1): the "Show online status" row in Account settings.
 * It reads the account's Reddit setting when shown and stops when left; it
 * never shows a switch for a value it does not know (Retry instead); a tap
 * only proposes the change, which Reddit gets only after "Show status" in
 * the confirmation; a state for another account is ignored; an account that
 * must sign in again gets "Sign in again" and no confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3OnlineVisibilitySettingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val me = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val events = mutableListOf<String>()
    private var confirmed: OnlineVisibilityConfirmation? = null
    private var reconnects = 0

    private fun runtime(state: OnlineVisibilityUiState) = OnlineVisibilityRuntime(
        state = state,
        onOpen = { events += "open:$it" },
        onClose = { events += "close:$it" },
        onRefresh = { events += "refresh:$it" },
        onPropose = { id, value -> events += "propose:$id:$value" },
        onConfirm = { confirmation ->
            events += "confirm"
            confirmed = confirmation
        },
        onCancel = { events += "cancel" },
    )

    private fun show(
        state: OnlineVisibilityUiState,
        account: AccountSessionSummary = me,
        shown: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(true),
    ) {
        composeRule.setContent {
            SubshellTheme {
                if (shown.value) {
                    OnlineVisibilitySettingRow(
                        account = account,
                        runtime = runtime(state),
                        onReconnect = { reconnects += 1 },
                        interactionEnabled = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun ready(value: Boolean?, accountId: String = me.stableId) = OnlineVisibilityUiState(
        accountStableId = accountId,
        value = value,
        phase = OnlineVisibilityPhase.READY,
    )

    private fun toggleableNodes() = composeRule.onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
    ).fetchSemanticsNodes()

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun theRowReadsTheSettingWhenShownAndStopsWhenLeft() {
        val shown = mutableStateOf(true)
        show(ready(true), shown = shown)
        assertEquals(listOf("open:reddit:me"), events.toList())

        composeRule.runOnIdle { shown.value = false }
        composeRule.waitForIdle()

        assertEquals(listOf("open:reddit:me", "close:reddit:me"), events.toList())
    }

    @Test
    fun anotherAccountsStateIsNotShownForThisOne() {
        // Reddit's answer for another account: this row knows nothing yet.
        show(ready(true, accountId = "reddit:other"))

        assertTrue("no switch for an unknown value", toggleableNodes().isEmpty())
        assertEquals(1, composeRule.onAllNodes(hasText("Retry")).fetchSemanticsNodes().size)
    }

    @Test
    fun anUnknownValueHasNoSwitchAndOffersRetry() {
        show(ready(null))

        assertTrue("no switch for an unknown value", toggleableNodes().isEmpty())
        click("Retry")

        assertEquals(listOf("open:reddit:me", "refresh:reddit:me"), events.toList())
    }

    @Test
    fun aTapOnlyProposesTheChange() {
        show(ready(false))
        assertEquals(1, toggleableNodes().size)

        composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("open:reddit:me", "propose:reddit:me:true"), events.toList())
        assertEquals(null, confirmed)
    }

    @Test
    fun theConfirmationSendsOnlyOnShowStatus() {
        val confirmation = OnlineVisibilityConfirmation(
            accountStableId = me.stableId,
            expectedValue = false,
            desiredValue = true,
            revision = 3L,
        )
        show(ready(false).copy(confirmation = confirmation))
        assertEquals(1, composeRule.onAllNodes(hasText("Show online status?")).fetchSemanticsNodes().size)

        click("Show status")

        assertEquals(confirmation, confirmed)
        assertEquals(listOf("open:reddit:me", "confirm"), events.toList())
    }

    @Test
    fun cancelSendsNothing() {
        val confirmation = OnlineVisibilityConfirmation(me.stableId, false, true, 3L)
        show(ready(false).copy(confirmation = confirmation))

        click("Cancel")

        assertEquals(listOf("open:reddit:me", "cancel"), events.toList())
        assertEquals(null, confirmed)
    }

    @Test
    fun anAccountThatMustSignInAgainGetsSignInAndNoConfirmation() {
        val stale = me.copy(state = AccountSessionState.REAUTHENTICATION_REQUIRED)
        val confirmation = OnlineVisibilityConfirmation(me.stableId, false, true, 3L)
        show(
            OnlineVisibilityUiState(
                accountStableId = me.stableId,
                phase = OnlineVisibilityPhase.FAILED,
                issue = OnlineVisibilityIssue.AUTHORIZATION,
                confirmation = confirmation,
            ),
            account = stale,
        )

        assertEquals(0, composeRule.onAllNodes(hasText("Show online status?")).fetchSemanticsNodes().size)
        click("Sign in again")

        assertEquals(1, reconnects)
        assertEquals(null, confirmed)
    }
}
