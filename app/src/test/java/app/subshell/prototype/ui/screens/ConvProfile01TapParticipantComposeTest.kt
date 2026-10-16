package app.subshell.prototype.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CONVPROFILE01_TAP_PARTICIPANT_OPENS_PROFILE_V1 (owner request): tapping the
 * participant in a chat or private message thread opens their profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ConvProfile01TapParticipantComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private fun opensProfileOf(username: String) = SemanticsMatcher("opens u/$username") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Open u/$username profile"
    }

    private fun show(item: InboxItem): MutableList<String> {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            ConversationScreen(item = item, onBack = {}, onOpenUser = { opened += it })
        }
        composeRule.waitForIdle()
        return opened
    }

    @Test
    fun tappingTheChatParticipantOpensTheirProfile() {
        val opened = show(
            InboxItem(
                id = "chat_1",
                kind = ActivityKind.MESSAGE,
                title = "[direct chat room]",
                sender = "someone_else",
                age = "1h",
                preview = "hi",
                conversation = listOf(MessageBubble(id = "m1", sender = "someone_else", age = "1h", body = "hi")),
            ),
        )

        composeRule.onAllNodes(opensProfileOf("someone_else")).onFirst().performClick()

        assertEquals(listOf("someone_else"), opened)
    }

    @Test
    fun onlyRealUsersCanBeOpened() {
        assertEquals("someone_else", conversationProfileUsername("u/someone_else"))
        assertNull(conversationProfileUsername("reddit"))
        assertNull(conversationProfileUsername("r/subshell"))
        assertNull(conversationProfileUsername("ab"))
    }
}
