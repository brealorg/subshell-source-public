package app.subshell.prototype.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditMediaImageSourceDto
import app.subshell.prototype.data.network.reddit.RedditMediaMetadataDto
import app.subshell.prototype.data.network.reddit.bodyWithRedditMediaMetadata
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #8 (ISSUE8_INLINE_REDDIT_MEDIA_V1): an emote is drawn inside the line
 * (one inline image, still loading under Robolectric), never as raw
 * placeholder text and never as a full-width image; a self post's inline
 * image shows in the open post, not in a feed preview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue8InlineRedditMediaComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val snoo = "emote|free_emotes_pack|joy"
    private val emote = RedditMediaMetadataDto(
        status = "valid",
        mediaKind = "Image",
        mimeType = "image/png",
        source = RedditMediaImageSourceDto(url = "https://www.redditstatic.com/emotes/joy.png", width = 20, height = 20),
    )
    private val image = RedditMediaMetadataDto(
        status = "valid",
        mediaKind = "Image",
        mimeType = "image/jpeg",
        source = RedditMediaImageSourceDto(url = "https://i.redd.it/pic123.jpg"),
    )

    private fun loadingImages(): Int =
        composeRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .fetchSemanticsNodes().size

    @Test
    fun anEmoteIsOneInlineImageInTheTextWithoutRawPlaceholder() {
        val body = RedditCommentDataDto(body = "so good ![img]($snoo) really", mediaMetadata = mapOf(snoo to emote))
            .bodyWithRedditMediaMetadata()

        composeRule.setContent { SubshellTheme { CommentContent(source = body) } }

        composeRule.onNode(hasText("so good :joy: really")).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("emote|", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("subshell-media", substring = true).fetchSemanticsNodes().size)
        // Exactly one image: the inline emote. No full-width preview below.
        assertEquals(1, loadingImages())
    }

    @Test
    fun aSelfPostShowsItsInlineImageOpenButNotInTheFeed() {
        val body = "Look at this\n\n![img](pic123 \"subshell-media https://i.redd.it/pic123.jpg\")\n\nand ![img]($snoo \"subshell-media https://www.redditstatic.com/emotes/joy.png 20x20\")"
        val post = Post(
            id = "t3_inline",
            community = "android",
            author = "alice",
            age = "1h",
            title = "A self post",
            body = body,
            score = 1,
            comments = 0,
            kind = PostKind.TEXT,
        )
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("local")),
            initialActiveAccountId = localFixtureAccountSummaries(listOf("local")).first().stableId,
        )
        var open by mutableStateOf(false)
        composeRule.setContent {
            SubshellTheme {
                PostCard(
                    post = post,
                    state = state,
                    onOpen = {},
                    bodyMaxLines = if (open) Int.MAX_VALUE else 4,
                )
            }
        }

        // Feed preview: text and the inline emote, no large image.
        composeRule.onNodeWithText("Look at this", substring = true).assertIsDisplayed()
        assertEquals(1, loadingImages())

        composeRule.runOnIdle { open = true }
        // Open post: the image follows the text, the emote stays inline.
        composeRule.onNodeWithText("Look at this").assertIsDisplayed()
        composeRule.onNode(hasText("and :joy:")).assertIsDisplayed()
        assertEquals(2, loadingImages())
        assertEquals(0, composeRule.onAllNodesWithText("pic123", substring = true).fetchSemanticsNodes().size)
    }
}
