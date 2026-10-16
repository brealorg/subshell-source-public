package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.model.projectCommentContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * ISSUE8_INLINE_REDDIT_MEDIA_V1 follow-up (device report 2026-09-27): the
 * profile comment list, Saved and the inbox read the raw body, where Reddit
 * writes "*Processing img <id>...*" and keeps the GIF in media_metadata. They
 * resolve media like a thread comment now.
 */
class Issue8MediaInCommentListsTest {
    private val json = SubshellNetworkJson.providerJson

    private val commentData = """
        "id": "c1",
        "name": "t1_c1",
        "author": "me_user",
        "subreddit": "test",
        "subreddit_name_prefixed": "r/test",
        "link_id": "t3_p1",
        "link_title": "A post",
        "parent_id": "t3_p1",
        "created_utc": 9000.0,
        "was_comment": true,
        "body": "look\n\n*Processing img gif123...*",
        "media_metadata": {
            "gif123": {
                "status": "valid",
                "e": "AnimatedImage",
                "m": "image/gif",
                "s": { "gif": "https://i.redd.it/gif123.gif", "x": 320, "y": 240 }
            }
        }
    """.trimIndent()

    private fun assertResolvedGif(body: String) {
        val projection = projectCommentContent(body)
        assertFalse(body, body.contains("Processing img"))
        assertEquals("look", projection.displayText)
        assertEquals("https://i.redd.it/gif123.gif", projection.media.single().uri)
        assertEquals(RemoteCommentMediaKind.GIF, projection.media.single().kind)
    }

    @Test
    fun profileCommentsResolveTheirGif() {
        val listing = json.decodeFromString<RedditProfileCommentsListingDto>(
            """{"data":{"children":[{"kind":"t1","data":{$commentData}}]}}""",
        )
        assertResolvedGif(listing.toProfileCommentsPage(nowEpochSeconds = 10_000L).items.single().body)
    }

    @Test
    fun savedCommentsResolveTheirGif() {
        val listing = json.decodeFromString<RedditSavedListingDto>(
            """{"data":{"children":[{"kind":"t1","data":{$commentData}}]}}""",
        )
        val item = listing.toSavedHistoryPage(json = json, nowEpochSeconds = 10_000L).items.single()
        val body = (item as RedditSavedHistoryItem.CommentItem).comment.body
        assertResolvedGif(body)
    }

    @Test
    fun inboxCommentRepliesResolveTheirGifAndPreviewReadably() {
        val listing = json.decodeFromString<RedditInboxListingDto>(
            """{"data":{"children":[{"kind":"t1","data":{$commentData}}]}}""",
        )
        val item = listing.toNetworkPage(nowEpochSeconds = 10_000L, currentUsername = "someone").items.single()

        assertFalse(item.preview, item.preview.contains("Processing img"))
        assertFalse(item.preview, item.preview.contains("subshell-media"))
        assertEquals("look https://i.redd.it/gif123.gif", item.preview)
        item.conversation.firstOrNull()?.let { bubble -> assertResolvedGif(bubble.body) }
    }
}
