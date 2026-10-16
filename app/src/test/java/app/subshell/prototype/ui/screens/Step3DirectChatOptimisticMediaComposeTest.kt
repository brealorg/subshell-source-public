package app.subshell.prototype.ui.screens

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c6r5, 02c6r5r1, 02e2a2): an image sent in a direct chat
 * leaves the composer at once and shows in the chat with "Sending…" (no
 * "Uploading and sending…" line); in a private message thread the composer
 * keeps it and says "Uploading and sending…"; after a partial send (image
 * sent, text not) the image leaves the composer and the text stays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DirectChatOptimisticMediaComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:me"

    private val pickerOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val picked = java.io.File(
                    ApplicationProvider.getApplicationContext<android.app.Application>().cacheDir,
                    "picked.gif",
                ).apply { writeBytes("GIF89a".toByteArray()) }
                dispatchResult(requestCode, Uri.fromFile(picked))
            }
        }
    }

    private val chat = InboxItem(
        id = "chat_a",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = "alice",
        age = "1h",
        preview = "Hi from alice",
    )
    private val thread = InboxItem(
        id = "t4_y",
        kind = ActivityKind.MESSAGE,
        title = "Plans",
        sender = "bob",
        age = "1h",
        preview = "See you",
    )

    private var reply by mutableStateOf(MessageReplyUiState())
    private var text by mutableStateOf("")
    private val texts = mutableListOf<String>()

    private fun show(item: InboxItem) {
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                ConversationScreen(
                    item = item,
                    onBack = {},
                    accountStableId = account,
                    replyText = text,
                    onReplyTextChange = {
                        texts += it
                        text = it
                    },
                    replyParentFullname = "t4_${item.id.removePrefix("t4_")}",
                    messageReplyState = reply,
                    directChatParticipantUsername = "alice".takeIf { item === chat },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun pickGif() {
        composeRule.onNode(hasContentDescription("GIF") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { inlineMedia() == 1 }
    }

    private fun inlineMedia() =
        composeRule.onAllNodes(hasContentDescription("Remove inline media")).fetchSemanticsNodes().size

    private fun count(value: String) = composeRule.onAllNodesWithText(value).fetchSemanticsNodes().size

    private fun sending(item: InboxItem) = MessageReplyUiState(
        phase = MessageReplyPhase.SENDING,
        accountStableId = account,
        inboxItemId = item.id,
        parentFullname = "t4_${item.id.removePrefix("t4_")}",
        sentHadMedia = true,
        optimisticMediaUri = "file:///cache/picked.gif".takeIf { item === chat },
        optimisticMediaMimeType = "image/gif".takeIf { item === chat },
    )

    @Test
    fun aDirectChatImageLeavesTheComposerAndShowsInTheChatWithSending() {
        show(chat)
        pickGif()

        composeRule.runOnIdle { reply = sending(chat) }
        composeRule.waitForIdle()

        assertEquals(0, inlineMedia())
        assertEquals(1, count("Sending…"))
        assertEquals(0, count("Uploading and sending…"))
    }

    @Test
    fun aPrivateThreadImageStaysInTheComposerWhileUploading() {
        show(thread)
        pickGif()

        composeRule.runOnIdle { reply = sending(thread) }
        composeRule.waitForIdle()

        assertEquals(1, inlineMedia())
        assertEquals(1, count("Uploading and sending…"))
        assertEquals(0, count("Sending…"))
    }

    @Test
    fun afterAPartialSendTheImageLeavesTheComposerAndTheTextStays() {
        text = "and some words"
        show(chat)
        pickGif()

        composeRule.runOnIdle {
            reply = MessageReplyUiState(
                phase = MessageReplyPhase.FAILED,
                accountStableId = account,
                inboxItemId = chat.id,
                parentFullname = "t4_chat_a",
                failureKind = MessageReplyFailureKind.HTTP,
                sentText = "and some words",
                sentDraftText = "and some words",
                sentHadMedia = true,
                completedAtEpochSeconds = 1_800_000_000L,
                completionRevision = 1L,
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, inlineMedia())
        assertEquals(1, count("Media sent, text not sent"))
        assertEquals("and some words", text)
        assertFalse("" in texts)
    }
}
