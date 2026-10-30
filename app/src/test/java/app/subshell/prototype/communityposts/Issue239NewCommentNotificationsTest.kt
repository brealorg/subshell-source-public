package app.subshell.prototype.communityposts

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityNewPostsLoader
import app.subshell.prototype.data.auth.CommunityComment
import app.subshell.prototype.data.auth.reddit.RedditCommunityCommentsListingDto
import app.subshell.prototype.data.auth.reddit.redditCommunityCommentsJsonRequest
import app.subshell.prototype.data.auth.reddit.toCommunityComments
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.putSubshellPreference
import app.subshell.prototype.ui.screens.NewPostNotificationsSettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1 (owner 2026-10-29: "også
 * kommentarer", "la bruker få bestemme selv i settings"): with "Also new
 * comments" on (off by default), a watched community also notifies about each
 * new comment, on its own channel; the reader's own posts and comments never
 * notify.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue239NewCommentNotificationsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = "reddit:me"

    @Before
    fun clearDeviceState() {
        context.getSharedPreferences(COMMUNITY_POST_SEEN_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun post(id: String, author: String = "someone") = Post(
        id = "t3_$id",
        community = "kotlin",
        author = author,
        age = "1m",
        title = "Post $id",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun comment(id: String, author: String = "someone", nsfw: Boolean = false) = CommunityComment(
        id = id,
        author = author,
        body = "Comment  $id\n\nmore",
        postTitle = "Coroutines",
        permalink = "/r/kotlin/comments/abc/coroutines/$id/",
        nsfw = nsfw,
    )

    private class FakeLoader(
        var posts: List<Post> = emptyList(),
        var comments: List<CommunityComment> = emptyList(),
    ) : AuthenticatedRedditCommunityNewPostsLoader {
        var commentReads = 0
        override suspend fun load(accountStableId: String, communityName: String): List<Post> = posts
        override suspend fun loadComments(accountStableId: String, communityName: String): List<CommunityComment> {
            commentReads += 1
            return comments
        }
    }

    private class Notified {
        val posts = mutableListOf<String>()
        val comments = mutableListOf<String>()
    }

    private fun check(loader: FakeLoader, notified: Notified, includeComments: Boolean, now: Long) = runBlocking {
        runCommunityPostCheck(
            accountStableId = account,
            watched = listOf("kotlin"),
            mutedNames = emptySet(),
            loader = loader,
            seen = CommunityPostSeenStore(context),
            nowEpochMillis = now,
            recordFailure = {},
            notify = { _, posts -> notified.posts += posts.map(Post::id); posts.size },
            ownUsername = "Me_Myself",
            includeComments = includeComments,
            notifyComments = { _, comments -> notified.comments += comments.map(CommunityComment::id); comments.size },
        )
    }

    @Test
    fun ownPostsMoveTheWatermarkButNeverNotify() {
        val first = checkForNewPosts(listOf(post("a")), watermark = null, ownUsername = "me_myself")
        val next = checkForNewPosts(
            listOf(post("c", author = "Me_Myself"), post("b"), post("a")),
            first.watermark,
            ownUsername = "me_myself",
        )
        assertEquals(listOf("t3_b"), next.newPosts.map(Post::id))
        assertEquals(redditPostOrdinal("c"), next.watermark)
    }

    @Test
    fun commentsAreReadOnlyWhileTheSettingIsOnSilentFirstAndNeverTheReadersOwn() {
        val loader = FakeLoader(posts = listOf(post("a")), comments = listOf(comment("x1")))
        val notified = Notified()
        check(loader, notified, includeComments = false, now = 10_000L)
        assertEquals(0, loader.commentReads)

        check(loader, notified, includeComments = true, now = 20_000L)
        assertEquals("the first comment check is a silent baseline", emptyList<String>(), notified.comments)

        loader.comments = listOf(comment("x4", author = "me_myself"), comment("x3"), comment("x2"), comment("x1"))
        val run = check(loader, notified, includeComments = true, now = 30_000L)
        assertEquals(listOf("x3", "x2"), notified.comments)
        assertEquals(2, run.posted)
        assertEquals(emptyList<String>(), notified.posts)

        check(loader, notified, includeComments = true, now = 40_000L)
        assertEquals("each comment notifies once", listOf("x3", "x2"), notified.comments)
    }

    // Found on the Pixel 6 (2026-10-29): r/AskReddit's comment ids have 13
    // characters; with a 12-character limit no comment ever had a number.
    @Test
    fun thirteenCharacterCommentIdsAreNumberedAndOrdered() {
        val newer = redditCommentOrdinal("t1_19rum59qz15eq")
        val older = redditCommentOrdinal("19rum572ipjlv")
        assertTrue(newer != null && older != null && newer > older)
        assertEquals(null, redditCommentOrdinal("zzzzzzzzzzzzz"))
        val check = checkForNewComments(listOf(comment("19rum59qz15eq"), comment("19rum572ipjlv")), older)
        assertEquals(listOf("19rum59qz15eq"), check.newComments.map(CommunityComment::id))
    }

    @Test
    fun theCommentStreamKeepsItsOwnSeenStateWhileWatched() {
        val seen = CommunityPostSeenStore(context)
        seen.save(account, "kotlin", 5L, 1_000L)
        seen.save(account, "kotlin", 9L, 1_000L, CommunityStream.COMMENTS)
        seen.forgetAllBut(account, listOf("kotlin"))
        assertEquals(5L, seen.watermark(account, "kotlin", 2_000L))
        assertEquals(9L, seen.watermark(account, "kotlin", 2_000L, CommunityStream.COMMENTS))
        seen.forgetAllBut(account, emptyList())
        assertEquals(null, seen.watermark(account, "kotlin", 2_000L, CommunityStream.COMMENTS))
    }

    @Test
    fun aCommentNotificationSaysWhoAndWhereButAnNsfwOneOrHiddenContentSaysLess() {
        assertEquals(
            CommunityPostNotificationText("r/kotlin · u/someone", "On “Coroutines”: Comment x1 more"),
            communityCommentNotificationText("kotlin", comment("x1"), hideContent = false),
        )
        assertEquals(
            CommunityPostNotificationText("r/kotlin", "New comment in an NSFW post"),
            communityCommentNotificationText("kotlin", comment("x1", nsfw = true), hideContent = false),
        )
        assertEquals(
            CommunityPostNotificationText("subshell", "New comments"),
            communityCommentNotificationText("kotlin", comment("x1"), hideContent = true),
        )
    }

    @Test
    fun eachNewCommentIsANotificationOnItsOwnChannelThatOpensTheComment() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(2, CommunityPostNotificationCenter.postNewComments(context, account, "kotlin", listOf(comment("x2"), comment("x1"))))
        val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).activeNotifications
        val singles = shown.filterNot { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
        assertEquals(2, singles.size)
        assertTrue(shown.all { it.notification.channelId == COMMUNITY_COMMENTS_NOTIFICATION_CHANNEL_ID })
        val open = shadowOf(singles.first().notification.contentIntent).savedIntent
        assertTrue(open.dataString!!.startsWith("https://www.reddit.com/r/kotlin/comments/abc/coroutines/x"))
    }

    @Test
    fun theCommunityCommentStreamIsReadAndDeletedCommentsAreLeftOut() {
        val request = redditCommunityCommentsJsonRequest("r/Kotlin")
        assertEquals(listOf("r", "Kotlin", "comments"), request.pathSegments)
        val listing = SubshellNetworkJson.providerJson.decodeFromString(
            RedditCommunityCommentsListingDto.serializer(),
            """{"data":{"children":[
                {"kind":"t1","data":{"id":"x2","author":"a","body":"hi","link_title":"T","permalink":"/r/k/comments/p/t/x2/","over_18":true}},
                {"kind":"t1","data":{"id":"x1","author":"[deleted]","body":"[deleted]"}},
                {"kind":"more","data":{"id":"m"}}
            ]}}""",
        )
        assertEquals(
            listOf(CommunityComment("x2", "a", "hi", "T", "/r/k/comments/p/t/x2/", nsfw = true)),
            listing.toCommunityComments(),
        )
    }

    @Test
    fun theSettingIsOffByDefaultBackedUpAndReadByTheBackgroundCheck() {
        assertFalse(SubshellSettings().notifyNewComments)
        assertFalse(newCommentNotificationsEnabled(context))
        val restored = SubshellSettings.fromStorageMap(SubshellSettings(notifyNewComments = true).toStorageMap())
        assertTrue(restored.notifyNewComments)
        context.getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, Context.MODE_PRIVATE).edit {
            putSubshellPreference(SubshellSettings.NOTIFY_NEW_COMMENTS, "true")
        }
        assertTrue(newCommentNotificationsEnabled(context))
    }

    @Test
    fun theReaderTurnsCommentsOnInNewPostSettings() {
        val persisted = mutableMapOf<String, String>()
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(
                AccountSessionSummary(
                    stableId = account,
                    username = "me_myself",
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                ),
            ),
            initialActiveAccountId = account,
            persistPreference = { key, value -> persisted[key] = value },
        )
        composeRule.setContent { NewPostNotificationsSettingsScreen(state = state, onBack = {}) }
        composeRule.onNodeWithText("Also new comments").assertIsOff().performClick()
        composeRule.onNodeWithText("Also new comments").assertIsOn()
        assertTrue(state.notifyNewComments)
        assertEquals("true", persisted[SubshellSettings.NOTIFY_NEW_COMMENTS])
    }
}
