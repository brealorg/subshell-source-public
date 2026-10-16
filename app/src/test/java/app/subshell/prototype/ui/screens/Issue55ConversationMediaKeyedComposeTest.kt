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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE55_COMPOSER_KEYED_ON_CONVERSATION_V1: the two-pane inbox swaps the
 * conversation in place; an image attached in one chat must not follow to
 * the next (it would be sent to the wrong person).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue55ConversationMediaKeyedComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The image picker answers at once with one picked image.
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
                    "picked.png",
                ).apply { writeBytes(byteArrayOf(1, 2, 3)) }
                dispatchResult(requestCode, Uri.fromFile(picked))
            }
        }
    }

    private fun chat(id: String, with: String) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = "[direct chat room]",
        sender = with,
        age = "1h",
        preview = "Hi from $with",
    )

    private fun attachedImages() =
        composeRule.onAllNodes(hasContentDescription("Remove inline media")).fetchSemanticsNodes().size

    @Test
    fun anImageAttachedInOneChatDoesNotFollowToTheNext() {
        val chatA = chat("chat_a", "alice")
        val chatB = chat("chat_b", "bob")
        var current by mutableStateOf(chatA)
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                ConversationScreen(
                    item = current,
                    onBack = {},
                    accountStableId = "reddit:me",
                    replyParentFullname = "t4_${current.id}",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("Image") and hasClickAction()).performClick()
        // The picked image is read on IO (LOWUI2_PICKED_MEDIA_READ_OFF_MAIN_V1).
        composeRule.waitUntil(timeoutMillis = 5_000) { attachedImages() == 1 }
        assertEquals(1, attachedImages())

        current = chatB
        composeRule.waitForIdle()
        assertEquals(0, attachedImages())

        current = chatA
        composeRule.waitForIdle()
        assertEquals(0, attachedImages())
    }
}
