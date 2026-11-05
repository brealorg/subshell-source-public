package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.account.ModeratorReportTarget
import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.model.UserFixture
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HOSTILEREDDIT01_EVERY_READ_SURVIVES_A_HOSTILE_REDDIT_V1 (2026-10-31): Reddit
 * has started to answer reads with things the app never saw before (#281: a
 * 190 KB HTML block page for /api/info). Every read source the app uses gets
 * every kind of bad answer — block page, 429, 500, HTML with 200, cut-off or
 * empty JSON, JSON of the wrong shape or with the wrong types, a reset
 * connection — and must come back with a result (a typed failure or an empty
 * page), never throw, and never loop on Reddit.
 */
// Robolectric: some sources log through android.util.Log (debug builds).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HostileReddit01ReadsTest {
    private enum class Answer(val code: Int, val contentType: String, val body: String) {
        BLOCK_PAGE(403, "text/html", "<html>" + " ".repeat(189_000) + "You've been blocked by network security.</html>"),
        RATE_LIMITED(429, "application/json", """{"message": "Too Many Requests", "error": 429}"""),
        SERVER_ERROR(500, "text/html", "<html>500</html>"),
        HTML_200(200, "text/html", "<html><body>Reddit</body></html>"),
        TRUNCATED(200, "application/json", """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"a"""),
        EMPTY(200, "application/json", ""),
        EMPTY_OBJECT(200, "application/json", "{}"),
        EMPTY_ARRAY(200, "application/json", "[]"),
        JSON_NULL(200, "application/json", "null"),
        JSON_STRING(200, "application/json", "\"reddit\""),
        NULL_DATA(200, "application/json", """{"kind":"Listing","data":null}"""),
        NULL_CHILDREN(200, "application/json", """{"kind":"Listing","data":{"children":null,"after":null}}"""),
        EMPTY_THINGS(
            200, "application/json",
            """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{}},{"kind":"t1","data":{}},""" +
                """{"kind":"t5","data":{}},{"kind":"more","data":{}},{"kind":"t4","data":{}}]}}""",
        ),
        WRONG_TYPES(
            200, "application/json",
            """{"kind":"Listing","data":{"after":5,"children":[{"kind":"t3","data":{"id":5,"name":[],""" +
                """"title":{},"created_utc":"x","score":"big","subreddit":null,"num_comments":-1}}]}}""",
        ),
        THREAD_OF_EMPTIES(200, "application/json", "[{},{}]"),
        THREAD_WITH_EMPTY_THINGS(
            200, "application/json",
            """[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{}}]}},""" +
                """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{}},{"kind":"more","data":{}}]}}]""",
        ),
        CONNECTION_RESET(0, "", ""),
    }

    private var answer = Answer.EMPTY
    private val requests = AtomicInteger()

    private val client = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            val current = answer
            if (current == Answer.CONNECTION_RESET) throw IOException("connection reset")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(current.code).message("answer")
                .apply { if (current.code == 429) header("Retry-After", "5") }
                .body(current.body.toResponseBody(current.contentType.toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
        bearerTokenProvider = NetworkBearerTokenProvider { "token" },
    )

    private val community = Community("t5_x", "test", "", "", 0L)
    private val user = UserFixture("tester", "", "")
    private val page = RepositoryPageRequest()

    /** Every read source the app uses, with ordinary arguments. */
    private val reads: List<Pair<String, suspend () -> Any?>> = listOf(
        "listing.posts(home)" to { RedditListingNetworkDataSource(client).posts() },
        "listing.posts(community)" to { RedditListingNetworkDataSource(client).posts(community = "test") },
        "listing.searchPosts" to { RedditListingNetworkDataSource(client).searchPosts("reddit") },
        "listing.userSubmittedPosts" to { RedditListingNetworkDataSource(client).userSubmittedPosts("tester") },
        "history.posts" to { RedditAccountHistoryNetworkDataSource(client).posts("tester", RedditAccountHistoryKind.UPVOTED, page) },
        "history.comments" to { RedditAccountHistoryNetworkDataSource(client).comments("tester", RedditAccountHistoryKind.COMMENTS, page) },
        "history.saved" to { RedditAccountHistoryNetworkDataSource(client).saved("tester", page) },
        "blocked.load" to { RedditBlockedAccountsNetworkDataSource(client).load() },
        "comments.thread" to { RedditCommentNetworkDataSource(client).comments("p1", RedditCommentSort.BEST) },
        "comments.focused" to { RedditCommentNetworkDataSource(client).comments("p1", RedditCommentSort.BEST, focusedCommentId = "c1") },
        "comments.moreChildren" to { RedditCommentNetworkDataSource(client).moreChildren("p1", listOf("c1", "c2"), RedditCommentSort.BEST) },
        "comments.moreChildrenByFocus" to {
            RedditCommentNetworkDataSource(client).moreChildrenByFocus("p1", listOf("c1", "c2"), RedditCommentSort.BEST, childDepth = 1)
        },
        "discovery.search" to { RedditCommunityDiscoveryNetworkDataSource(client).page("reddit", io.github.brealorg.subshell.model.CommunityDiscoverySort.RELEVANCE, null, null, 25, null) },
        "discovery.popular" to { RedditCommunityDiscoveryNetworkDataSource(client).page(null, null, null, null, 25, true) },
        "info.sidebar" to { RedditCommunityInfoNetworkDataSource(client).sidebarInfo("test") },
        "info.rules" to { RedditCommunityInfoNetworkDataSource(client).rules("test") },
        "info.moderators" to { RedditCommunityInfoNetworkDataSource(client).moderators("test") },
        "wiki.index" to { RedditCommunityWikiNetworkDataSource(client).index("test") },
        "wiki.page" to { RedditCommunityWikiNetworkDataSource(client).page("test", "index") },
        "identity.user" to { RedditIdentityNetworkDataSource(client).user("tester", user) },
        "identity.community" to { RedditIdentityNetworkDataSource(client).community("test", community) },
        "identity.communities" to { RedditIdentityNetworkDataSource(client).communities(listOf(community, community.copy(name = "other"))) },
        "identity.authorAvatars" to { RedditIdentityNetworkDataSource(client).authorAvatars(listOf("t2_a", "t2_b")) },
        "inbox.page" to { RedditInboxNetworkDataSource(client).page(null, 25, "tester", true) },
        "inbox.target" to { RedditInboxTargetNetworkDataSource(client).post("p1") },
        "modlog.page" to { RedditModerationLogNetworkDataSource(client).page("test", null) },
        "moderation.communities" to { RedditModerationNetworkDataSource(client).moderatedCommunities(null) },
        "moderation.queue" to { RedditModerationNetworkDataSource(client).modQueue("test", null) },
        "moderation.reportTarget(post)" to { RedditModerationNetworkDataSource(client).reportTarget(ModeratorReportTarget("t3_p1")) },
        "moderation.reportTarget(comment)" to {
            RedditModerationNetworkDataSource(client).reportTarget(ModeratorReportTarget("t1_c1", postFullname = "t3_p1"))
        },
        "users.banned" to { RedditModeratorUserManagementNetworkDataSource(client).bannedUsersPage("test", null) },
        "users.muted" to { RedditModeratorUserManagementNetworkDataSource(client).mutedUsersPage("test", null) },
        "users.approved" to { RedditModeratorUserManagementNetworkDataSource(client).approvedUsersPage("test", null) },
        "modmail.communities" to { RedditModmailNetworkDataSource(client).mailCommunities() },
        "modmail.conversations" to { RedditModmailNetworkDataSource(client).conversations() },
        "modmail.unread" to { RedditModmailNetworkDataSource(client).unreadConversations() },
        "modmail.unreadCounts" to { RedditModmailNetworkDataSource(client).unreadCounts() },
        "modmail.conversation" to { RedditModmailNetworkDataSource(client).conversation("abc12") },
        "people.search" to { RedditPeopleSearchNetworkDataSource(client).page("reddit", null, 10) },
        "subscriptions.page" to { RedditSubscriptionNetworkDataSource(client).page(null, 25) },
        "postManage.load" to { RedditPostManageNetworkDataSource(client).load("t3_p1", flairScopeGranted = true) },
        "removalReasons" to { RedditRemovalReasonNetworkDataSource(client).reasons("test") },
        "submitReconciliation" to { RedditSubmitReconciliationDataSource(client).submitted("tester") },
        "postEdit.readBack" to { RedditPostEditNetworkDataSource(client).readBack("t3_p1") },
        "postDelete.readBack" to { RedditPostDeleteNetworkDataSource(client).readBack("t3_p1") },
        "commentEdit.readBack" to { RedditCommentEditNetworkDataSource(client, "p1").readBack("t1_c1") },
        "commentDelete.readBack" to { RedditCommentDeleteNetworkDataSource(client, "p1").readBack("t1_c1") },
        "nsfwPreferences" to { RedditNsfwPreferencesNetworkDataSource(client).read() },
        "onlineVisibility" to { RedditOnlineVisibilityNetworkDataSource(client).read() },
        "accessProbe" to { RedditAccessProbeNetworkDataSource(client).probe { true } },
    )

    @Test
    fun everyReadComesBackFromEveryBadAnswerWithoutThrowingOrLooping() = runBlocking {
        val problems = mutableListOf<String>()
        for (bad in Answer.entries) {
            answer = bad
            for ((name, read) in reads) {
                requests.set(0)
                try {
                    withTimeout(30_000) { read() }
                } catch (cancelled: CancellationException) {
                    problems += "$name / $bad: did not finish (${cancelled.javaClass.simpleName})"
                } catch (failure: Throwable) {
                    problems += "$name / $bad: threw ${failure.javaClass.simpleName}: ${failure.message?.take(120)}"
                }
                // The access check asks 17 things; a read that keeps asking is a loop.
                val limit = if (name == "accessProbe") REDDIT_ACCESS_PROBE_ENDPOINTS.size else MAX_REQUESTS_PER_READ
                if (requests.get() > limit) problems += "$name / $bad: ${requests.get()} requests"
            }
        }
        assertTrue(problems.joinToString(separator = "\n", prefix = "${problems.size} problems:\n"), problems.isEmpty())
    }

    private companion object {
        // Inbox reads Inbox and Sent and may balance one more page each; a
        // delete read-back asks a few times with pauses.
        const val MAX_REQUESTS_PER_READ = 8
    }
}
