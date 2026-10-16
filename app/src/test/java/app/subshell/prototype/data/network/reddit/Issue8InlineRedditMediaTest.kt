package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.model.parseRedditMediaPlaceholders
import app.subshell.prototype.model.projectCommentContent
import app.subshell.prototype.model.reannotateRedditMedia
import app.subshell.prototype.model.redditReadableText
import app.subshell.prototype.model.redditSourceMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #8 (ISSUE8_INLINE_REDDIT_MEDIA_V1): emotes are small inline images,
 * images and Giphy stay media, and the body keeps Reddit's own placeholders so
 * an edit, a copy or a quote can hand back exactly what Reddit wrote.
 */
class Issue8InlineRedditMediaTest {
    private val emoteKey = "emote|t5_2qh1i|8217"
    private val emoteUrl = "https://reddit-econ-prod-assets-permanent.s3.amazonaws.com/asset-manager/t5_2qh1i/8217.png"
    private val emote = RedditMediaMetadataDto(
        status = "valid",
        mediaKind = "Image",
        mimeType = "image/png",
        source = RedditMediaImageSourceDto(url = emoteUrl, width = 20, height = 20),
    )
    private val image = RedditMediaMetadataDto(
        status = "valid",
        mediaKind = "Image",
        mimeType = "image/jpeg",
        source = RedditMediaImageSourceDto(url = "https://i.redd.it/pic123.jpg", width = 1080, height = 720),
    )

    private val raw = "nice ![img]($emoteKey) one\n\n![img](pic123)\n\n![gif](giphy|abcDEF)\n\nend"
    private fun comment(body: String = raw, metadata: Map<String, RedditMediaMetadataDto> = mapOf(emoteKey to emote, "pic123" to image)) =
        RedditCommentDataDto(body = body, mediaMetadata = metadata).bodyWithRedditMediaMetadata()

    @Test
    fun anEmoteStaysInTheLineWithItsAddressAndSize() {
        val body = comment()
        val emotePlaceholder = parseRedditMediaPlaceholders(body).single { it.isEmote }

        assertEquals(emoteUrl, emotePlaceholder.url)
        assertEquals(20, emotePlaceholder.width)
        assertEquals(20, emotePlaceholder.height)
        val projection = projectCommentContent(body)
        // The emote is not appended as a full-width image; it stays in the text.
        assertTrue(projection.displayText.startsWith("nice ![img]($emoteKey \"subshell-media $emoteUrl 20x20\") one"))
        assertFalse(projection.media.any { media -> media.uri == emoteUrl })
    }

    @Test
    fun imagesAndGiphyAreMediaInTheirOrderAndLeaveNoTextBehind() {
        val projection = projectCommentContent(comment())

        assertEquals(
            listOf("https://i.redd.it/pic123.jpg", "https://i.giphy.com/media/abcDEF/giphy.gif"),
            projection.media.map { media -> media.uri },
        )
        assertEquals(listOf(RemoteCommentMediaKind.IMAGE, RemoteCommentMediaKind.GIF), projection.media.map { it.kind })
        assertFalse(projection.displayText.contains("pic123"))
        assertFalse(projection.displayText.contains("giphy"))
        assertTrue(projection.displayText.endsWith("end"))
    }

    @Test
    fun theSourceIsExactlyWhatRedditWroteAndAnnotatingIsIdempotent() {
        val body = comment()
        assertEquals(raw, body.redditSourceMarkdown())
        assertEquals(body, RedditCommentDataDto(body = body, mediaMetadata = mapOf(emoteKey to emote, "pic123" to image)).bodyWithRedditMediaMetadata())
        // Text without annotations is untouched.
        assertEquals("plain ![img](x1)", "plain ![img](x1)".redditSourceMarkdown())
    }

    @Test
    fun mediaWithoutAPlaceholderIsAppendedAndStrippedAgain() {
        val body = comment(body = "only text", metadata = mapOf("orphan1" to image))

        assertEquals("https://i.redd.it/pic123.jpg", projectCommentContent(body).media.single().uri)
        assertEquals("only text", projectCommentContent(body).displayText)
        assertEquals("only text", body.redditSourceMarkdown())
        // A media-only comment keeps its media and its (empty) source.
        val mediaOnly = comment(body = "", metadata = mapOf("orphan1" to image))
        assertEquals(1, projectCommentContent(mediaOnly).media.size)
        assertEquals("", mediaOnly.redditSourceMarkdown())
    }

    @Test
    fun aSnoomojiHasAReadableNameAndAPackEmoteDoesNot() {
        val snoo = "emote|free_emotes_pack|joy"
        val body = comment(body = "ha ![img]($snoo) ![img]($emoteKey)", metadata = mapOf(snoo to emote, emoteKey to emote))
        val names = parseRedditMediaPlaceholders(body).map { it.emoteName }

        assertEquals(listOf("joy", null), names)
        assertEquals("ha :joy:", body.redditReadableText())
    }

    @Test
    fun aSelfPostResolvesItsPlaceholdersButNeverRepeatsItsGallery() {
        val dto = RedditPostDataDto(
            id = "p1",
            name = "t3_p1",
            subreddit = "test",
            author = "a",
            title = "Self post",
            selftext = "look ![img]($emoteKey)\n\n![img](pic123)",
            isSelf = true,
            mediaMetadata = mapOf(emoteKey to emote, "pic123" to image, "gallery9" to image.copy(source = RedditMediaImageSourceDto(url = "https://i.redd.it/gallery9.jpg"))),
        )
        val post = RedditListingDto(data = RedditListingDataDto(children = listOf(RedditThingDto(kind = "t3", data = dto))))
            .toNetworkPage(nowEpochSeconds = 10_000L)
            .items
            .single()

        assertEquals("look ![img]($emoteKey)\n\n![img](pic123)", post.body.redditSourceMarkdown())
        assertFalse(post.body.contains("gallery9"))
        assertEquals(listOf("https://i.redd.it/pic123.jpg"), projectCommentContent(post.body).media.map { it.uri })
    }

    @Test
    fun aLocalRebindAfterAnEditKeepsTheKnownAddresses() {
        val before = comment()
        val edited = "edited ![img]($emoteKey)\n\n![gif](giphy|abcDEF)"

        val rebound = reannotateRedditMedia(edited, previous = before)

        assertEquals(edited, rebound.redditSourceMarkdown())
        assertEquals(emoteUrl, parseRedditMediaPlaceholders(rebound).first { it.isEmote }.url)
        assertEquals("https://i.giphy.com/media/abcDEF/giphy.gif", projectCommentContent(rebound).media.single().uri)
    }

    @Test
    fun copiedTextNamesTheMediaInsteadOfPlaceholders() {
        assertEquals(
            "nice  one\n\nhttps://i.redd.it/pic123.jpg\n\nhttps://i.giphy.com/media/abcDEF/giphy.gif\n\nend",
            comment().redditReadableText(),
        )
    }
}
