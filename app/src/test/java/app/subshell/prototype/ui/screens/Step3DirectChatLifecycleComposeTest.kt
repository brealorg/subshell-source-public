package app.subshell.prototype.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c7): an open direct chat reads live only while it is on
 * screen: it starts when shown, stops when the app goes to the background and
 * starts again when it returns, and stops when the chat is closed; a private
 * message thread never starts a live read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DirectChatLifecycleComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val account = "reddit:me"
    private val chat = InboxItem(
        id = "chat_1",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = "peer",
        age = "1h",
        preview = "hi",
    )
    private val thread = InboxItem(
        id = "t4_y",
        kind = ActivityKind.MESSAGE,
        title = "Plans",
        sender = "bob",
        age = "1h",
        preview = "See you",
    )

    private val events = mutableListOf<String>()

    // Starting the live read again while it runs is a no-op in
    // DirectChatCoordinator; the screen may ask twice on entry (once at once,
    // once when its lifecycle observer replays ON_START), so repeats in a row
    // count as one request.
    private fun requests(): List<String> =
        events.filterIndexed { index, event -> index == 0 || events[index - 1] != event }
    private var shown by mutableStateOf(true)

    private fun show(item: InboxItem, participant: String?) {
        composeRule.setContent {
            if (shown) {
                ConversationScreen(
                    item = item,
                    onBack = {},
                    accountStableId = account,
                    directChatParticipantUsername = participant,
                    onStartDirectChatLive = { acct, id, peer -> events += "start:$acct:$id:$peer" },
                    onStopDirectChatLive = { acct, id -> events += "stop:$acct:$id" },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anOpenDirectChatStartsAndClosingItStops() {
        show(chat, "peer")
        assertEquals(listOf("start:reddit:me:chat_1:peer"), requests())

        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()

        assertEquals(listOf("start:reddit:me:chat_1:peer", "stop:reddit:me:chat_1"), requests())
    }

    @Test
    fun goingToTheBackgroundStopsAndComingBackStartsAgain() {
        show(chat, "peer")

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        assertEquals("stop:reddit:me:chat_1", events.last())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertEquals(
            listOf("start:reddit:me:chat_1:peer", "stop:reddit:me:chat_1", "start:reddit:me:chat_1:peer"),
            requests(),
        )
    }

    @Test
    fun aPrivateMessageThreadNeverStartsALiveRead() {
        show(thread, null)
        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), events)
    }
}
