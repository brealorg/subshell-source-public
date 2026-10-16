package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditInboxNetworkDataSourceTest {
    @Test
    fun requestIsReadOnlyUsesRemoteAfterCursorAndBoundsReplies() {
        val request = redditInboxJsonRequest(
            cursor = "t4_after",
            limit = REDDIT_INBOX_MAX_PAGE_SIZE,
        )

        assertEquals(listOf("message", "inbox"), request.pathSegments)
        assertEquals(
            listOf(
                "raw_json" to "1",
                "mark" to "false",
                "max_replies" to REDDIT_INBOX_MAX_REPLIES.toString(),
                "limit" to "100",
                "after" to "t4_after",
            ),
            request.queryParameters,
        )
        assertTrue(REDDIT_INBOX_MAX_REPLIES in 1..300)
        assertFalse(request.pathSegments.joinToString("/").contains("api/read"))
    }

    @Test
    fun blankAfterCursorIsNotSent() {
        val request = redditInboxJsonRequest(cursor = "  ", limit = 25)

        assertNull(request.queryParameters.firstOrNull { pair -> pair.first == "after" })
    }

    @Test
    fun targetRequestUsesReadOnlyApiInfoWithNormalizedPostFullname() {
        val request = redditInboxTargetJsonRequest("post123")

        assertEquals(listOf("api", "info"), request.pathSegments)
        assertEquals(
            listOf(
                "raw_json" to "1",
                "id" to "t3_post123",
            ),
            request.queryParameters,
        )
    }

    @Test
    fun listingNormalizesReplyTargetsSeparatesTypesAndSortsNewestFirst() {
        val page = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                after = "t4_next",
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t1",
                        data = RedditInboxDataDto(
                            name = "t1_reply",
                            author = "reply_user",
                            subject = "comment reply",
                            body = "Reply body",
                            unread = true,
                            createdUtc = 1_000.0,
                            wasComment = true,
                            linkId = "t3_post",
                            parentId = "t3_post",
                            linkTitle = "Post title",
                            subredditNamePrefixed = "r/test",
                            context = "/r/test/comments/post/title/reply/",
                        ),
                    ),
                    RedditInboxThingDto(
                        kind = "t1",
                        data = RedditInboxDataDto(
                            name = "t1_mention",
                            author = "mention_user",
                            subject = "username mention",
                            body = "Mention body",
                            createdUtc = 1_100.0,
                            wasComment = true,
                            context = "/r/test/comments/post2/title/mention/",
                            linkTitle = "Mentioned post",
                        ),
                    ),
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_message",
                            author = "message_user",
                            subject = "Hello",
                            body = "Message body",
                            createdUtc = 1_200.0,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(
            nowEpochSeconds = 1_300L,
            currentUsername = "me",
        )

        assertEquals("t4_next", page.nextCursor)
        assertEquals(listOf("t4_message", "t1_mention", "t1_reply"), page.items.map { it.id })

        val reply = page.items.single { it.id == "t1_reply" }
        assertEquals(app.subshell.prototype.model.ActivityKind.POST_REPLY, reply.kind)
        assertEquals("post", reply.targetPostId)
        assertEquals("t1_reply", reply.targetCommentId)
        assertEquals("r/test", reply.subreddit)
        assertEquals("Post title", reply.postTitle)
        assertEquals(1_000L, reply.createdEpochSeconds)
        assertTrue(reply.unread)

        val mention = page.items.single { it.id == "t1_mention" }
        assertEquals(app.subshell.prototype.model.ActivityKind.MENTION, mention.kind)
        assertEquals("post2", mention.targetPostId)
        assertEquals("t1_mention", mention.targetCommentId)

        val message = page.items.single { it.id == "t4_message" }
        assertEquals(app.subshell.prototype.model.ActivityKind.MESSAGE, message.kind)
        assertEquals("Message body", message.conversation.single().body)
        assertFalse(message.conversation.single().outgoing)
    }

    @Test
    fun messageConversationSortsRepliesChronologicallyAndMarksCurrentUserOutgoing() {
        val replies = Json.parseToJsonElement(
            """
            {
              "kind": "Listing",
              "data": {
                "children": [
                  {
                    "kind": "t4",
                    "data": {
                      "id": "theirs",
                      "name": "t4_theirs",
                      "author": "other_user",
                      "body": "Their later reply",
                      "created_utc": 1250
                    }
                  },
                  {
                    "kind": "t4",
                    "data": {
                      "id": "mine",
                      "name": "t4_mine",
                      "author": "CurrentUser",
                      "body": "My earlier reply",
                      "created_utc": 1200
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val message = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            id = "root",
                            name = "t4_root",
                            author = "other_user",
                            subject = "Thread",
                            body = "Root message",
                            createdUtc = 1_100.0,
                            replies = replies,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(
            nowEpochSeconds = 1_300L,
            currentUsername = "currentuser",
        ).items.single()

        assertEquals(3, message.conversation.size)
        assertEquals(
            listOf("Root message", "My earlier reply", "Their later reply"),
            message.conversation.map { it.body },
        )
        assertFalse(message.conversation[0].outgoing)
        assertTrue(message.conversation[1].outgoing)
        assertFalse(message.conversation[2].outgoing)
        assertEquals("Their later reply", message.preview)
        assertEquals("other_user", message.sender)
        // STEP3 (messages02b): every message in the thread keeps its own time.
        assertEquals(listOf(1_100L, 1_200L, 1_250L), message.conversation.map { it.createdEpochSeconds })
    }

    @Test
    fun messageThreadsUseFirstMessageIdentityAndLatestReplyForListOrder() {
        fun replies(id: String, body: String, created: Int) = Json.parseToJsonElement(
            """
            {
              "kind": "Listing",
              "data": {
                "children": [
                  {
                    "kind": "t4",
                    "data": {
                      "id": "$id",
                      "name": "t4_$id",
                      "author": "peer",
                      "body": "$body",
                      "created_utc": $created
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val page = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_old-root",
                            firstMessageName = "t4_old-root",
                            author = "peer",
                            subject = "Older root with newest reply",
                            body = "Old root",
                            createdUtc = 1_000.0,
                            replies = replies("newest", "Newest reply", 1_400),
                        ),
                    ),
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_recent-root",
                            firstMessageName = "t4_recent-root",
                            author = "peer2",
                            subject = "Recent root",
                            body = "Recent root body",
                            createdUtc = 1_300.0,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(
            nowEpochSeconds = 1_500L,
            currentUsername = "me",
        )

        assertEquals(listOf("t4_old-root", "t4_recent-root"), page.items.map { it.id })
        assertEquals("Newest reply", page.items.first().preview)
    }
    @Test
    fun sentRequestIsReadOnlyAndDoesNotExpandReplies() {
        val request = redditSentJsonRequest(REDDIT_INBOX_MAX_PAGE_SIZE)

        assertEquals(listOf("message", "sent"), request.pathSegments)
        assertEquals(
            listOf(
                "raw_json" to "1",
                "mark" to "false",
                "max_replies" to "0",
                "limit" to "100",
            ),
            request.queryParameters,
        )
        assertFalse(request.pathSegments.joinToString("/").contains("api/read"))
    }

    @Test
    fun inboxAndSentListingsMergeOwnRepliesIntoOneConversation() {
        val inbox = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                after = "t4_next",
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_root",
                            author = "peer",
                            dest = "CurrentUser",
                            subject = "Thread subject",
                            body = "Incoming root",
                            createdUtc = 1_000.0,
                        ),
                    ),
                    RedditInboxThingDto(
                        kind = "t1",
                        data = RedditInboxDataDto(
                            name = "t1_reply",
                            author = "commenter",
                            subject = "comment reply",
                            body = "Activity stays intact",
                            wasComment = true,
                            linkId = "t3_post",
                            createdUtc = 1_050.0,
                        ),
                    ),
                ),
            ),
        )
        val sent = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_mine",
                            firstMessageName = "t4_root",
                            author = "currentuser",
                            dest = "peer",
                            subject = "re: Thread subject",
                            body = "My sent reply",
                            createdUtc = 1_100.0,
                        ),
                    ),
                ),
            ),
        )

        val page = mergeInboxAndSentListings(inbox, sent).toNetworkPage(
            nowEpochSeconds = 1_200L,
            currentUsername = "CurrentUser",
        )

        assertEquals("t4_next", page.nextCursor)
        val thread = page.items.single { it.id == "t4_root" }
        assertEquals(2, thread.conversation.size)
        assertEquals(
            listOf("Incoming root", "My sent reply"),
            thread.conversation.map { it.body },
        )
        assertFalse(thread.conversation[0].outgoing)
        assertTrue(thread.conversation[1].outgoing)
        assertEquals("My sent reply", thread.preview)
        assertEquals("peer", thread.sender)

        val activity = page.items.single { it.id == "t1_reply" }
        assertEquals(app.subshell.prototype.model.ActivityKind.REPLY, activity.kind)
    }

    @Test
    fun sentRequestSupportsOpaqueHistoryCursorWithoutWrites() {
        val request = redditSentJsonRequest(
            limit = REDDIT_INBOX_MAX_PAGE_SIZE,
            cursor = "t4_sent-next",
        )

        assertEquals(
            listOf(
                "raw_json" to "1",
                "mark" to "false",
                "max_replies" to "0",
                "limit" to "100",
                "after" to "t4_sent-next",
            ),
            request.queryParameters,
        )
    }

    @Test
    fun historyBalanceFetchesOnlyNewerSideAndIsBounded() {
        assertTrue(
            shouldFetchOlderRedditInboxHistoryPage(
                currentOldestEpochSeconds = 2_000L,
                targetOldestEpochSeconds = 1_000L,
                nextCursor = "t4_next",
                pagesLoaded = 1,
            ),
        )
        assertFalse(
            shouldFetchOlderRedditInboxHistoryPage(
                currentOldestEpochSeconds = 1_000L,
                targetOldestEpochSeconds = 2_000L,
                nextCursor = "t4_next",
                pagesLoaded = 1,
            ),
        )
        assertFalse(
            shouldFetchOlderRedditInboxHistoryPage(
                currentOldestEpochSeconds = 2_000L,
                targetOldestEpochSeconds = 1_000L,
                nextCursor = "t4_next",
                pagesLoaded = REDDIT_INBOX_HISTORY_BALANCE_MAX_PAGES,
            ),
        )
    }

    @Test
    fun combinedHistoryPagesPreserveOldestFetchedCursorAndAllChildren() {
        val combined = combineRedditInboxListings(
            listOf(
                RedditInboxListingDto(
                    data = RedditInboxListingDataDto(
                        after = "t4_page2",
                        children = listOf(
                            RedditInboxThingDto(
                                kind = "t4",
                                data = RedditInboxDataDto(name = "t4_new"),
                            ),
                        ),
                    ),
                ),
                RedditInboxListingDto(
                    data = RedditInboxListingDataDto(
                        after = "t4_page3",
                        children = listOf(
                            RedditInboxThingDto(
                                kind = "t4",
                                data = RedditInboxDataDto(name = "t4_old"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("t4_page3", combined.data.after)
        assertEquals(
            listOf("t4_new", "t4_old"),
            combined.data.children.map { child -> child.data.name },
        )
    }


    @Test
    fun moderatorDistinguishedCommunityMessagesRemainInMessageThread() {
        val inbox = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_root",
                            author = "#MorpheApp",
                            dest = "CurrentUser",
                            distinguished = "moderator",
                            subreddit = "MorpheApp",
                            subredditNamePrefixed = "r/MorpheApp",
                            subject = "Morphe patches",
                            body = "Community reply",
                            createdUtc = 1_000.0,
                        ),
                    ),
                ),
            ),
        )
        val sent = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_mine",
                            firstMessageName = "t4_root",
                            author = "CurrentUser",
                            dest = "#MorpheApp",
                            subject = "re: Morphe patches",
                            body = "My reply",
                            createdUtc = 1_100.0,
                        ),
                    ),
                ),
            ),
        )

        val thread = mergeInboxAndSentListings(inbox, sent).toNetworkPage(
            nowEpochSeconds = 1_200L,
            currentUsername = "CurrentUser",
        ).items.single()

        assertEquals(app.subshell.prototype.model.ActivityKind.MESSAGE, thread.kind)
        assertEquals("r/MorpheApp", thread.sender)
        assertEquals(listOf("Community reply", "My reply"), thread.conversation.map { it.body })
        assertFalse(thread.conversation[0].outgoing)
        assertTrue(thread.conversation[1].outgoing)
    }

    @Test
    fun standaloneRedditSystemMessageRemainsActivityInsteadOfConversation() {
        val item = RedditInboxListingDto(
            data = RedditInboxListingDataDto(
                children = listOf(
                    RedditInboxThingDto(
                        kind = "t4",
                        data = RedditInboxDataDto(
                            name = "t4_system",
                            author = "reddit",
                            subject = "System notice",
                            body = "Notice",
                            createdUtc = 1_000.0,
                        ),
                    ),
                ),
            ),
        ).toNetworkPage(
            nowEpochSeconds = 1_100L,
            currentUsername = "CurrentUser",
        ).items.single()

        assertEquals(app.subshell.prototype.model.ActivityKind.MOD, item.kind)
    }

}
