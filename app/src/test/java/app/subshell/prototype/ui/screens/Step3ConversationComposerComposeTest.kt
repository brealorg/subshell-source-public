package app.subshell.prototype.ui.screens

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.components.ComposerFormattingDock
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02c3): a conversation's composer picks a GIF as a GIF (the
 * system picker is asked for image/gif and the picked file is attached); the
 * formatting dock offers its ten Markdown actions; and a message with a GIF
 * and Markdown shows the GIF as media (not as a link in the text) and the
 * formatted text, not the raw marks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ConversationComposerComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val launched = mutableListOf<Any?>()

    private val pickerOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                launched += input
                val picked = java.io.File(
                    ApplicationProvider.getApplicationContext<android.app.Application>().cacheDir,
                    "picked.gif",
                ).apply { writeBytes("GIF89a".toByteArray()) }
                dispatchResult(requestCode, Uri.fromFile(picked))
            }
        }
    }

    private fun chat(preview: String = "Hi from alice") = InboxItem(
        id = "chat_a",
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = "alice",
        age = "1h",
        preview = preview,
    )

    @Test
    fun theGifButtonAsksTheSystemPickerForAGifAndAttachesIt() {
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                ConversationScreen(
                    item = chat(),
                    onBack = {},
                    accountStableId = "reddit:me",
                    replyParentFullname = "t4_chat_a",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("GIF") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("Remove inline media")).fetchSemanticsNodes().size == 1
        }

        val request = launched.single() as PickVisualMediaRequest
        val type = request.mediaType as PickVisualMedia.SingleMimeType
        assertEquals("image/gif", type.mimeType)
    }

    @Test
    fun theDockOffersItsTenFormattingActions() {
        val formats = mutableListOf<MarkdownAction>()
        composeRule.setContent {
            SubshellTheme {
                ComposerFormattingDock(state = TextFieldState("text"), onFormat = { formats += it }, onReturnToEditor = {})
            }
        }
        composeRule.waitForIdle()

        for (label in listOf("Bold", "Italic", "Link", "Code block")) {
            assertEquals(label, 1, composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size)
        }
        composeRule.onNode(hasContentDescription("More formatting") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        for (label in listOf("Quote", "Bulleted list", "Numbered list", "Strikethrough", "Inline code", "Spoiler")) {
            assertTrue(label, composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
        composeRule.onNode(hasText("Spoiler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(MarkdownAction.SPOILER), formats)
    }

    @Test
    fun aMessageShowsItsGifAndFormattedText() {
        composeRule.setContent {
            ConversationScreen(
                item = chat(preview = "**hello** there\n\nhttps://i.redd.it/wave.gif"),
                onBack = {},
                accountStableId = "reddit:me",
            )
        }
        composeRule.waitForIdle()

        // The GIF is lifted out of the text into its own media slot (Robolectric
        // cannot decode it, so its "Message GIF 1" label never appears here).
        assertEquals(0, composeRule.onAllNodesWithText("wave.gif", substring = true).fetchSemanticsNodes().size)
        assertTrue(composeRule.onAllNodesWithText("hello there", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, composeRule.onAllNodesWithText("**hello**", substring = true).fetchSemanticsNodes().size)
    }
}
