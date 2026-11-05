package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.moderatorReportTargetOrNull
import io.github.brealorg.subshell.data.account.reportTarget
import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.model.Community
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE281_READS_AVOID_API_INFO_V1 (#281, 2026-10-30): Reddit answers /api/info
 * with a 403 "blocked by network security" page for this app's bearer, so an
 * inbox reply's post "couldn't be opened" and every write read-back came back
 * unknown. A post or comment is read from its thread instead.
 */
class Issue281ReadsAvoidApiInfoTest {
    private val paths = java.util.Collections.synchronizedList(mutableListOf<String>())

    /** Reddit as measured on 2026-10-30: /api/info blocked, threads answered. */
    private fun reddit(thread: (String) -> Pair<Int, String>) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            paths += path
            val (code, body) = if (path.startsWith("/api/info") || path.startsWith("/by_id")) {
                403 to "<html><body>You've been blocked by network security.</body></html>"
            } else {
                thread(path)
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("answer")
                .body(body.toResponseBody((if (code == 403) "text/html" else "application/json").toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
        bearerTokenProvider = NetworkBearerTokenProvider { "token" },
    )

    private val post =
        """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"p1","name":"t3_p1",
        "title":"A post","subreddit":"subshell","author":"me","created_utc":1.0,"num_comments":3}}]}}"""

    private fun comment(author: String, body: String) =
        """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"c1","name":"t1_c1",
        "link_id":"t3_p1","parent_id":"t3_p1","subreddit":"subshell","author":"$author","body":"$body",
        "can_mod_post":true,"ignore_reports":false,"user_reports":[["Spam",2]],"mod_reports":[]}}]}}"""

    @Test
    fun anInboxReplysPostOpensFromItsThread() = runBlocking {
        val client = reddit { path -> if (path == "/comments/p1") 200 to postThreadAnswer(post) else 404 to "{}" }

        val result = RedditInboxTargetNetworkDataSource(client).post("t3_p1")

        assertEquals("t3_p1", (result as NetworkResult.Success).value?.id)
        assertEquals(listOf("/comments/p1"), paths)
    }

    @Test
    fun aDeletedCommentIsReadBackFromItsThread() = runBlocking {
        val client = reddit { path ->
            if (path == "/comments/p1/_/c1") 200 to commentThreadAnswer(comment("[deleted]", "[deleted]")) else 404 to "{}"
        }

        val readback = RedditCommentDeleteNetworkDataSource(client, postId = "t3_p1").readBack("t1_c1")

        assertTrue("$readback", readback is RedditCommentDeleteReadback.Deleted)
        assertEquals(listOf("/comments/p1/_/c1"), paths)
    }

    @Test
    fun anEditedCommentIsReadBackFromItsThread() = runBlocking {
        val client = reddit { path ->
            if (path == "/comments/p1/_/c1") 200 to commentThreadAnswer(comment("me", "new text")) else 404 to "{}"
        }

        val readback = RedditCommentEditNetworkDataSource(client, postId = "t3_p1").readBack("t1_c1")

        assertEquals("new text", (readback as RedditCommentEditReadback.Body).text)
    }

    @Test
    fun aPostIsReadBackFromItsThreadAndA404ThreadIsNotProofOfDeletion() = runBlocking {
        val shown = RedditPostDeleteNetworkDataSource(
            reddit { path -> if (path == "/comments/p1") 200 to postThreadAnswer(post) else 404 to "{}" },
        ).readBack("t3_p1")
        assertEquals(RedditPostDeleteReadback.Present, shown)

        val gone = RedditPostDeleteNetworkDataSource(reddit { 404 to """{"message":"Not Found","error":404}""" })
            .readBack("t3_p1")
        // ISSUE36_A_404_THREAD_IS_NOT_PROOF_OF_DELETION_V1: a 404 can also be a
        // banned community or a routing error; the outcome stays unknown.
        assertEquals(RedditPostDeleteReadback.Unreadable, gone)

        val edited = RedditPostEditNetworkDataSource(
            reddit { path -> if (path == "/comments/p1") 200 to postThreadAnswer(post) else 404 to "{}" },
        ).readBack("t3_p1")
        assertTrue("$edited", edited is RedditPostEditReadback.Body)
        assertTrue(paths.none { path -> path.startsWith("/api/info") })
    }

    @Test
    fun aReportedCommentIsReadFromItsThreadWhenItsPostIsKnown() = runBlocking {
        val client = reddit { path ->
            if (path == "/comments/p1/_/c1") 200 to commentThreadAnswer(comment("someone", "rude")) else 404 to "{}"
        }

        val item = RedditModerationNetworkDataSource(client)
            .reportTarget(ModeratorReportTarget("t1_c1", "subshell", postFullname = "t3_p1"))

        assertEquals(2, (item as NetworkResult.Success).value?.reportDetails?.totalCount)
        // The post comes from a link, the thread on screen or the queue item's permalink;
        // a bare comment id has none.
        assertEquals(
            "t3_p1",
            moderatorReportTargetOrNull("https://www.reddit.com/r/subshell/comments/p1/x/c1/")?.postFullname,
        )
        assertEquals("t3_p1", moderatorReportTargetOrNull("t1_c1", postHint = "p1")?.postFullname)
        assertNull(moderatorReportTargetOrNull("t1_c1")?.postFullname)
        val queued = RedditModeratorQueueItem(
            fullname = "t1_c1",
            kind = RedditModeratorQueueKind.COMMENT,
            communityName = "subshell",
            author = "someone",
            summary = "rude",
            permalink = "/r/subshell/comments/p1/x/c1/",
        )
        assertEquals("t3_p1", queued.reportTarget().postFullname)
    }

    @Test
    fun communityIconsComeFromEachAboutPageAndAGoneCommunityKeepsItsLetter() = runBlocking {
        val client = reddit { path ->
            when (path) {
                "/r/subshell/about" -> 200 to """{"kind":"t5","data":{"display_name":"subshell",
                    "community_icon":"https://styles.redditmedia.com/icon.png"}}"""
                else -> 404 to """{"reason":"banned"}"""
            }
        }
        fun community(name: String) = Community(id = "t5_$name", name = name, description = "", members = "1", accent = 0L)

        val result = RedditIdentityNetworkDataSource(client).communities(listOf(community("subshell"), community("gone")))

        val communities = (result as NetworkResult.Success).value
        // ISSUE34_EVERY_COMMUNITY_IS_ASKED_ONCE_V1: a gone community comes back
        // as it was asked (no icon), so the caller counts it as asked.
        assertEquals(listOf("gone", "subshell"), communities.map(Community::name).sorted())
        assertEquals(null, communities.first { it.name == "gone" }.iconImageUri)
        assertEquals("https://styles.redditmedia.com/icon.png", communities.first { it.name == "subshell" }.iconImageUri)
        assertEquals(setOf("/r/subshell/about", "/r/gone/about"), paths.toSet())
    }
}
