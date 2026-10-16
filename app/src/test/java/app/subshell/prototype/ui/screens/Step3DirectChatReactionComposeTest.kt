package app.subshell.prototype.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.DirectChatReaction
import app.subshell.prototype.data.account.DirectChatReactionCachedTarget
import app.subshell.prototype.data.account.directChatReactionCacheTextSha256
import app.subshell.prototype.data.account.redditDirectChatReactionCatalog
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02e2a): a live message can be reacted to: "Message actions"
 * → React opens Reddit's reaction picker and the chosen reaction is sent for
 * that message's event; reactions known only from the stored snapshot (the
 * message is not live, or the account cannot write) are shown but offer no
 * way to react.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DirectChatReactionComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = "reddit:test"
    private val ownLive = MessageBubble(id = "\$own1", sender = "me", age = "now", body = "mine", outgoing = true)
    private val reacted = mutableListOf<Pair<String, String>>()

    private fun chat(conversation: List<MessageBubble>) = InboxItem(
        id = "chat_1",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = "peer",
        age = "1h",
        preview = conversation.last().body,
        conversation = conversation,
    )

    private fun live(
        messages: List<MessageBubble> = emptyList(),
        reactions: List<DirectChatReaction> = emptyList(),
        cached: List<DirectChatReactionCachedTarget> = emptyList(),
    ) = DirectChatLiveUiState(
        accountStableId = account,
        inboxItemId = "chat_1",
        participantUsername = "peer",
        live = true,
        messages = messages,
        reactions = reactions,
        cachedReactionTargets = cached,
    )

    private fun show(item: InboxItem, state: DirectChatLiveUiState, writeEnabled: Boolean = true) {
        composeRule.setContent {
            ConversationScreen(
                item = item,
                onBack = {},
                writeEnabled = writeEnabled,
                accountStableId = account,
                ownUsername = "me",
                directChatLiveState = state,
                directChatParticipantUsername = "peer",
                onReactDirectChatMessage = { eventId, key -> reacted += eventId to key },
            )
        }
        composeRule.waitForIdle()
    }

    private fun click(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun aLiveMessageIsReactedToThroughRedditsPicker() {
        show(
            chat(listOf(MessageBubble(id = "m1", sender = "peer", age = "1h", body = "hi"))),
            live(messages = listOf(ownLive)),
        )

        click(androidx.compose.ui.test.hasContentDescription("Message actions") and hasClickAction())
        click(hasText("React") and hasClickAction())

        assertEquals(1, composeRule.onAllNodesWithText("React to message").fetchSemanticsNodes().size)
        // The picker's choices are the buttons in its window (a modal bottom
        // sheet since #225, M3225_REACTION_PICKER_IS_A_SHEET_V1, so the sheet's
        // drag handle is not one of them); the first is Reddit's first reaction
        // (Robolectric does not decode the GIFs, so their labels are not asserted).
        val choice = hasClickAction() and hasAnyAncestor(isDialog()) and !hasText("Cancel") and
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Role,
                androidx.compose.ui.semantics.Role.Button,
            )
        assertTrue(composeRule.onAllNodes(choice).fetchSemanticsNodes().isNotEmpty())
        click(choice)

        assertEquals(listOf("\$own1" to redditDirectChatReactionCatalog.first().key), reacted)
    }

    @Test
    fun reactionsKnownOnlyFromTheSnapshotAreShownWithoutAWayToReact() {
        val stored = MessageBubble(
            id = "\$cached1",
            sender = "me",
            age = "1h",
            body = "hello",
            outgoing = true,
            createdEpochSeconds = 1_800_000_000L,
        )
        show(
            chat(listOf(stored)),
            live(
                reactions = listOf(DirectChatReaction("\$r1", "\$cached1", "thumbs", senderIsSelf = false)),
                cached = listOf(
                    DirectChatReactionCachedTarget(
                        targetEventId = "\$cached1",
                        outgoing = true,
                        createdEpochSeconds = 1_800_000_000L,
                        mediaId = null,
                        bodySha256 = directChatReactionCacheTextSha256("hello"),
                    ),
                ),
            ),
        )

        // The chip is read out as its label (directChatReactionChipLabel).
        assertEquals(1, composeRule.onAllNodesWithContentDescription("thumbs, 1 reaction").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Message actions").fetchSemanticsNodes().size)
        assertEquals(emptyList<Pair<String, String>>(), reacted)
    }

    @Test
    fun withoutWriteAccessALiveMessageOffersNoReaction() {
        show(
            chat(listOf(MessageBubble(id = "m1", sender = "peer", age = "1h", body = "hi"))),
            live(messages = listOf(ownLive)),
            writeEnabled = false,
        )

        assertEquals(1, composeRule.onAllNodesWithText("mine").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Message actions").fetchSemanticsNodes().size)
    }
}
