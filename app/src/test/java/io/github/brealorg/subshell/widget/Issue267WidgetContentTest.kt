package io.github.brealorg.subshell.widget

import android.app.Application
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditFeedSort
import io.github.brealorg.subshell.data.RepositoryLoadOperation
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.RepositoryQueryKey
import io.github.brealorg.subshell.data.cache.MutableCacheClock
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.cache.SubshellCacheDatabase
import io.github.brealorg.subshell.data.liveCommunityPostFeedQueryKey
import io.github.brealorg.subshell.data.liveHomePostFeedQueryKey
import io.github.brealorg.subshell.data.network.NetworkPage
import io.github.brealorg.subshell.inbox.takeInboxNotificationTarget
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.shortcuts.LauncherShortcutTarget
import io.github.brealorg.subshell.shortcuts.takeLauncherShortcutTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE267_WIDGETS_SHOW_WHAT_THE_APP_HAS_STORED_V1 and
 * ISSUE267_INBOX_AND_POSTS_WIDGETS_V1 (#267): what the Inbox and Posts
 * widgets show, where the Posts widget finds its stored feed, and that a tap
 * is taken by the app as a notification or shortcut tap is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue267WidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000L)
    private val cache = RoomSubshellCacheStore(database, clock = clock)

    @After
    fun close() {
        cache.close()
    }

    private fun item(id: String, unread: Boolean) = InboxItem(
        id = id,
        kind = ActivityKind.REPLY,
        title = "Replied to your comment",
        sender = "someone",
        age = "1h",
        preview = "Text $id",
        unread = unread,
        postTitle = "A post",
    )

    private fun post(id: String, community: String = "r/test", nsfw: Boolean = false, pinned: Boolean = false) = Post(
        id = id,
        community = community,
        author = "someone",
        age = "1h",
        title = "Post $id",
        score = 1_234,
        comments = 5,
        kind = PostKind.TEXT,
        nsfw = nsfw,
        pinned = pinned,
        permalink = "/r/${community.removePrefix("r/")}/comments/${id.removePrefix("t3_")}/post/",
    )

    private fun write(key: RepositoryQueryKey, posts: List<Post>, atEpochMs: Long) {
        clock.epochMs = atEpochMs
        cache.writeNetworkPostPage(
            RepositoryPageRequest(cursor = null, limit = 25, queryKey = key, operation = RepositoryLoadOperation.INITIAL),
            NetworkPage(posts, null),
            receivedAtEpochMs = atEpochMs,
        )
    }

    @Test
    fun theInboxWidgetCountsUnreadAsTheInboxDoesAndHidesEverythingWhileLocked() {
        val items = listOf(item("t1_a", unread = true), item("t1_b", unread = true), item("t1_c", unread = false))

        val shown = inboxWidgetContent("reddit:me", items, locallySeenIds = setOf("t1_b"), hidesContent = false)
            as InboxWidgetContent.Shown
        assertEquals(1, shown.unreadCount)
        assertEquals(listOf(true, false, false), shown.rows.map(InboxWidgetRow::unread))
        assertEquals("Replied to your comment · A post", shown.rows.first().title)

        assertEquals(InboxWidgetContent.Locked, inboxWidgetContent("reddit:me", items, emptySet(), hidesContent = true))
        assertEquals(InboxWidgetContent.SignedOut, inboxWidgetContent(null, items, emptySet(), hidesContent = false))
        assertEquals(InboxWidgetContent.NotStored("reddit:me"), inboxWidgetContent("reddit:me", null, emptySet(), false))
    }

    @Test
    fun thePostsWidgetLeavesOutNsfwHiddenAndPinnedPostsAndLinksIntoTheApp() {
        val posts = listOf(
            post("t3_pin", pinned = true),
            post("t3_ok"),
            post("t3_nsfw", nsfw = true),
            post("t3_hidden", community = "r/Secret"),
        )

        val shown = postsWidgetContent(
            accountStableId = "reddit:me",
            source = PostsWidgetSource.Home,
            posts = posts,
            hidesContent = false,
            showNsfwPosts = false,
            hiddenCommunities = setOf("secret"),
        ) as PostsWidgetContent.Shown
        assertEquals(listOf("t3_ok"), shown.rows.map(PostsWidgetRow::id))
        assertEquals("https://www.reddit.com/r/test/comments/ok/post/", shown.rows.single().link)

        val withNsfw = postsWidgetContent("reddit:me", PostsWidgetSource.Home, posts, false, true, emptySet())
            as PostsWidgetContent.Shown
        assertEquals(listOf("t3_ok", "t3_nsfw", "t3_hidden"), withNsfw.rows.map(PostsWidgetRow::id))

        assertEquals(
            PostsWidgetContent.Locked,
            postsWidgetContent("reddit:me", PostsWidgetSource.Home, posts, true, true, emptySet()),
        )
        assertEquals(
            PostsWidgetContent.NotStored(PostsWidgetSource("norge")),
            postsWidgetContent("reddit:me", PostsWidgetSource("norge"), null, false, false, emptySet()),
        )
    }

    @Test
    fun thePostsWidgetReadsTheFeedOfItsPlaceRefreshedLastWhateverTheSort() {
        write(liveHomePostFeedQueryKey("reddit:me", RedditFeedSort.BEST), listOf(post("t3_best")), atEpochMs = 2_000L)
        write(liveHomePostFeedQueryKey("reddit:me", RedditFeedSort.HOT), listOf(post("t3_hot")), atEpochMs = 3_000L)
        write(liveHomePostFeedQueryKey("reddit:other", RedditFeedSort.NEW), listOf(post("t3_other")), atEpochMs = 9_000L)
        write(
            liveCommunityPostFeedQueryKey("reddit:me", "norge", RedditFeedSort.TOP),
            listOf(post("t3_norge", community = "r/norge")),
            atEpochMs = 4_000L,
        )
        // "norge_x" must not answer for "norge": the prefix ends at the name.
        write(
            liveCommunityPostFeedQueryKey("reddit:me", "norge_x", RedditFeedSort.HOT),
            listOf(post("t3_norge_x", community = "r/norge_x")),
            atEpochMs = 5_000L,
        )

        fun newest(source: PostsWidgetSource) =
            cache.newestPostFeedSnapshot(postsWidgetQueryPrefix("reddit:me", source))?.posts?.map(Post::id)

        assertEquals(listOf("t3_hot"), newest(PostsWidgetSource.Home))
        assertEquals(listOf("t3_norge"), newest(PostsWidgetSource("r/Norge")))
        assertNull(newest(PostsWidgetSource("motogp")))
    }

    @Test
    fun aLikeWildcardInTheAccountIdMatchesOnlyItself() {
        write(liveHomePostFeedQueryKey("reddit:a_b", RedditFeedSort.BEST), listOf(post("t3_mine")), atEpochMs = 2_000L)
        write(liveHomePostFeedQueryKey("reddit:aXb", RedditFeedSort.BEST), listOf(post("t3_theirs")), atEpochMs = 3_000L)

        val newest = cache.newestPostFeedSnapshot(postsWidgetQueryPrefix("reddit:a_b", PostsWidgetSource.Home))

        assertEquals(listOf("t3_mine"), newest?.posts?.map(Post::id))
    }

    @Test
    fun anInboxItemTapIsTakenAsANotificationTapAndOpensAgainNextTime() {
        val actions = InboxWidgetActions(context)

        val first = actions.openItem("reddit:me", "t1_a")
        val again = actions.openItem("reddit:me", "t1_a")
        val target = takeInboxNotificationTarget(context, Intent(first))

        assertEquals("reddit:me", target?.accountStableId)
        assertEquals("t1_a", target?.inboxItemId)
        assertNotEquals(
            first.getStringExtra(io.github.brealorg.subshell.inbox.INBOX_NOTIFICATION_EVENT_EXTRA),
            again.getStringExtra(io.github.brealorg.subshell.inbox.INBOX_NOTIFICATION_EVENT_EXTRA),
        )
        // Without Subshell's launch key the same intent opens nothing.
        val forged = Intent(first).apply { removeExtra(io.github.brealorg.subshell.inbox.NOTIFICATION_LAUNCH_KEY_EXTRA) }
        assertNull(takeInboxNotificationTarget(context, forged))
    }

    @Test
    fun theInboxHeaderOpensTheInboxOfItsAccount() {
        val target = takeLauncherShortcutTarget(InboxWidgetActions(context).openInbox("reddit:me"), requestId = 7L)

        assertTrue(target is LauncherShortcutTarget.Inbox)
        assertEquals("reddit:me", target?.accountStableId)
    }

    @Test
    fun aPickedCommunityKeepsHowItWritesItsName() {
        PostsWidgetSourceStore.write(context, 41, PostsWidgetSource("r/BoostForReddit"))
        assertEquals("r/BoostForReddit", PostsWidgetSourceStore.read(context, 41).label)

        PostsWidgetSourceStore.write(context, 41, PostsWidgetSource.Home)
        assertEquals(PostsWidgetSource.Home, PostsWidgetSourceStore.read(context, 41))

        PostsWidgetSourceStore.write(context, 42, PostsWidgetSource("norge"))
        PostsWidgetSourceStore.forget(context, intArrayOf(42))
        assertEquals(PostsWidgetSource.Home, PostsWidgetSourceStore.read(context, 42))
    }

    @Test
    fun countsAndAgesUseTheShortForms() {
        assertEquals("999", widgetCount(999))
        assertEquals("1.2k", widgetCount(1_234))
        assertEquals("1k", widgetCount(1_000))
        assertEquals("34k", widgetCount(34_567))
        assertEquals("1.5m", widgetCount(1_500_000))
        assertEquals("now", widgetAge(1_000, 1_030))
        assertEquals("5m", widgetAge(1_000, 1_300))
        assertEquals("3h", widgetAge(0, 3 * 3_600))
        assertEquals("2d", widgetAge(0, 2 * 86_400))
        assertNull(widgetAge(null, 1_000))
    }
}
