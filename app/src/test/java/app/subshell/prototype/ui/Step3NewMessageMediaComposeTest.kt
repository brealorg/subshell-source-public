package app.subshell.prototype.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.DirectChatStartRequest
import app.subshell.prototype.data.account.DirectChatStartUiState
import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.NewMessageRouteHost
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (messages02d5c): the first message of a new chat is text or one
 * image/GIF, never both: a picked GIF is sent alone (no text) to the person
 * chosen; with text typed the Image and GIF buttons are off; with an image
 * attached, typing turns Send off; there is no video choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3NewMessageMediaComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:alice"
    private val starts = mutableListOf<DirectChatStartRequest>()

    private val pickerOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val picked = java.io.File(
                    ApplicationProvider.getApplicationContext<Application>().cacheDir,
                    "first.gif",
                ).apply { writeBytes("GIF89a".toByteArray()) }
                dispatchResult(requestCode, Uri.fromFile(picked))
            }
        }
    }

    private fun show() {
        val alice = AccountSessionSummary(
            stableId = account,
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.openSecondary(SecondaryDestination.NEW_MESSAGE)
        // A compose link picks the recipient, so the composer is open at once.
        state.openRedditTarget(RedditTarget.Compose("bob"))
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerOwner) {
                SubshellTheme {
                    NewMessageRouteHost(
                        state = state,
                        redditCommunity = CommunityEntryPoints(
                            redditCommunityDiscoveryState = RedditCommunityDiscoveryUiState(),
                        ),
                        redditInbox = InboxEntryPoints(),
                        directChatStartState = DirectChatStartUiState(),
                        onStartRedditDirectChat = { starts += it },
                        onResetRedditDirectChatStartState = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun clickable(matcher: SemanticsMatcher): SemanticsNodeInteraction =
        composeRule.onAllNodes(matcher and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).onFirst()

    private fun isEnabled(node: SemanticsNodeInteraction): Boolean =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) == null

    private fun click(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun pickGif() {
        click(clickable(hasContentDescription("GIF")))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("Remove inline media")).fetchSemanticsNodes().size == 1
        }
    }

    private fun type(text: String) {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        composeRule.waitForIdle()
    }

    @Test
    fun aPickedGifIsSentAloneToTheChosenPerson() {
        show()

        pickGif()
        click(clickable(hasContentDescription("Send message")))

        assertEquals(1, starts.size)
        val request = starts.single()
        assertEquals(account, request.accountStableId)
        assertEquals("bob", request.participantUsername)
        assertEquals("", request.text)
        assertEquals("image/gif", request.media?.mimeType)
    }

    @Test
    fun textAndAnImageNeverGoTogetherAndThereIsNoVideo() {
        show()
        assertEquals(0, composeRule.onAllNodesWithText("Video").fetchSemanticsNodes().size)

        type("Hello")
        assertFalse(isEnabled(clickable(hasContentDescription("Image"))))
        assertFalse(isEnabled(clickable(hasContentDescription("GIF"))))
    }

    @Test
    fun typingWithAnAttachedImageTurnsSendOff() {
        show()
        pickGif()
        assertEquals(true, isEnabled(clickable(hasContentDescription("Send message"))))

        type("and words")

        assertFalse(isEnabled(clickable(hasContentDescription("Send message"))))
        assertEquals(emptyList<DirectChatStartRequest>(), starts)
    }
}
