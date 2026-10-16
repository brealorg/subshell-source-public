package app.subshell.prototype.ui.components

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.DirectChatReactionSummary
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.redditDirectChatReactionCatalog
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.screens.THREAD_FIND_OPEN_LABEL
import app.subshell.prototype.ui.screens.ThreadFindBar
import app.subshell.prototype.ui.screens.directChatReactionChipLabel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LOWUI7a: controls tell screen readers what they are and what state they are in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi7aSemanticsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    // LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1 (OCB-161)
    @Test
    fun theRemovalMessageRowIsALabelledSwitch() {
        val draft = RemovalResponseDraft()
        composeRule.setContent {
            RemovalResponseSection(draft = draft, kind = RedditModeratorQueueKind.POST)
        }

        val row = composeRule.onNodeWithText("Send a removal message", substring = true)
        val config = row.fetchSemanticsNode().config
        assertEquals(Role.Switch, config[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, config[SemanticsProperties.ToggleableState])
        row.performClick()
        composeRule.waitForIdle()
        row.assertIsOn()
        assertTrue(draft.enabled)
    }

    // LOWUI7_NOTICES_ARE_ANNOUNCED_V1 (OCB-132)
    @Test
    fun anErrorNoticeIsALiveRegion() {
        composeRule.setContent {
            ErrorNotice(
                presentation = ErrorPresentation(
                    kind = ErrorPresentationKind.INLINE,
                    title = "Couldn't load",
                    message = "Try again later.",
                ),
                onAction = {},
            )
        }

        val node = composeRule.onNodeWithText("Couldn't load", substring = true).fetchSemanticsNode()
        val liveRegion = generateSequence(node) { it.parent }
            .firstOrNull { SemanticsProperties.LiveRegion in it.config }
            ?.config
            ?.get(SemanticsProperties.LiveRegion)
        assertEquals(LiveRegionMode.Polite, liveRegion)
    }

    // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-279)
    @Test
    fun theFindFieldKeepsItsNameOnceTextIsTyped() {
        var query by mutableStateOf("")
        composeRule.setContent {
            ThreadFindBar(
                query = query,
                onQueryChange = { query = it },
                countLabel = null,
                countDescription = null,
                onNext = {},
                onPrevious = {},
                onClose = {},
            )
        }
        composeRule.onNodeWithText(THREAD_FIND_OPEN_LABEL).performTextInput("kotlin")
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(THREAD_FIND_OPEN_LABEL).fetchSemanticsNode()
    }

    // LOWUI7_REACTIONS_SAY_WHICH_AND_HOW_MANY_V1 (OCB-198)
    @Test
    fun aReactionChipSaysWhichReactionHowManyAndWhetherItIsYours() {
        val third = redditDirectChatReactionCatalog[2].key
        assertEquals(
            "Reddit reaction 3, 2 reactions, including yours",
            directChatReactionChipLabel(DirectChatReactionSummary(key = third, count = 2, self = true)),
        )
        assertEquals(
            "Reddit reaction 3, 1 reaction",
            directChatReactionChipLabel(DirectChatReactionSummary(key = third, count = 1, self = false)),
        )
    }
}
