package app.subshell.prototype.communityposts

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.subshell.prototype.backup.SettingsSnapshot
import app.subshell.prototype.backup.SettingsSnapshotCodec
import app.subshell.prototype.backup.SnapshotKind
import app.subshell.prototype.backup.normalizeWatchedCommunityNamesByAccount
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityNewPostsLoader
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.keepCommunityPostCheckScheduled
import app.subshell.prototype.ui.state.CommunityPostWatchChange
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE239 (#239, owner decisions 2026-10-21): new posts in the communities a
 * signed-in account watches (at most 10) are checked in the background; the
 * first check is a silent baseline, each new post is one notification
 * (grouped per community, at most 5 per check, NSFW without its title), the
 * seen state stays on the device and the watched list is a backup module
 * (schema v11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue239CommunityPostNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val first = authenticated("reddit:first", "first_user")
    private val second = authenticated("reddit:second", "second_user")
    private val stored = mutableMapOf<String, Set<String>>()

    @Before
    fun clearDeviceState() {
        context.getSharedPreferences(COMMUNITY_POST_SEEN_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun post(id: String, title: String = "Post $id", nsfw: Boolean = false) = Post(
        id = "t3_$id",
        community = "kotlin",
        author = "someone",
        age = "1m",
        title = title,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        nsfw = nsfw,
        permalink = "/r/kotlin/comments/$id/slug/",
    )

    // ---- what is new ----

    @Test
    fun theFirstCheckIsSilentAndLaterOnlyNewerPostsAreNewNewestFirst() {
        val baseline = checkForNewPosts(listOf(post("b"), post("a")), watermark = null)
        assertEquals(emptyList<Post>(), baseline.newPosts)
        assertEquals(redditPostOrdinal("b"), baseline.watermark)

        val later = checkForNewPosts(listOf(post("d"), post("c"), post("b"), post("a")), baseline.watermark)
        assertEquals(listOf("t3_d", "t3_c"), later.newPosts.map(Post::id))
        assertEquals(redditPostOrdinal("d"), later.watermark)
    }

    @Test
    fun aCheckNotifiesAtMostFivePostsAndTheWatermarkNeverGoesBack() {
        val many = ('b'..'k').map { post(it.toString()) }
        val check = checkForNewPosts(many, watermark = redditPostOrdinal("a"))
        assertEquals(MAX_NOTIFIED_POSTS_PER_COMMUNITY, check.newPosts.size)
        assertEquals("t3_k", check.newPosts.first().id)
        assertEquals(redditPostOrdinal("k"), check.watermark)

        // The newest post was deleted: what is left is not new again.
        val afterDelete = checkForNewPosts(listOf(post("j"), post("i")), watermark = check.watermark)
        assertEquals(emptyList<Post>(), afterDelete.newPosts)
        assertEquals(redditPostOrdinal("k"), afterDelete.watermark)
    }

    @Test
    fun postIdsAreBase36AndAnythingElseIsIgnored() {
        assertEquals(36L, redditPostOrdinal("t3_10"))
        assertTrue(redditPostOrdinal("t3_1abc")!! > redditPostOrdinal("t3_zzz")!!)
        assertNull(redditPostOrdinal("t3_"))
        assertNull(redditPostOrdinal("t3_AB-C"))
    }

    @Test
    fun watchedNamesAreValidDistinctLowercaseAndAtMostTen() {
        assertEquals(listOf("kotlin"), normalizeWatchedCommunityNames(listOf("r/Kotlin", "kotlin", "not a name", "")))
        // Only an edited backup holds more; the first ten by name are kept.
        val many = normalizeWatchedCommunityNames((1..15).map { "c%02d".format(it) })
        assertEquals((1..MAX_WATCHED_COMMUNITIES).map { "c%02d".format(it) }, many)
    }

    // ---- the seen store ----

    @Test
    fun theSeenStateStartsAgainAfterTwoHoursAndForgetsUnwatchedCommunities() {
        val store = CommunityPostSeenStore(context)
        assertNull(store.watermark(first.stableId, "kotlin", nowEpochMillis = 0L))
        store.save(first.stableId, "kotlin", watermark = 42L, nowEpochMillis = 1_000L)
        store.save(first.stableId, "android", watermark = 7L, nowEpochMillis = 1_000L)
        assertEquals(42L, store.watermark(first.stableId, "kotlin", nowEpochMillis = 1_000L + 60_000L))
        assertNull(store.watermark(first.stableId, "kotlin", 1_000L + COMMUNITY_POST_BASELINE_MAX_AGE_MILLIS + 1))

        store.forgetAllBut(first.stableId, listOf("android"))
        assertNull(store.watermark(first.stableId, "kotlin", nowEpochMillis = 2_000L))
        assertEquals(7L, store.watermark(first.stableId, "android", nowEpochMillis = 2_000L))
        // Another account's state is its own.
        assertNull(store.watermark(second.stableId, "android", nowEpochMillis = 2_000L))
    }

    // ---- one background check ----

    private class FakeLoader(var pages: Map<String, List<Post>>) : AuthenticatedRedditCommunityNewPostsLoader {
        val reads = mutableListOf<String>()
        var failure: Exception? = null
        override suspend fun load(accountStableId: String, communityName: String): List<Post>? {
            reads += communityName
            failure?.let { throw it }
            return pages[communityName]
        }
    }

    private fun check(
        loader: FakeLoader,
        watched: List<String> = listOf("kotlin"),
        muted: Set<String> = emptySet(),
        failures: MutableList<FailureDetails> = mutableListOf(),
        notified: MutableList<Pair<String, List<String>>> = mutableListOf(),
        now: Long = 10_000L,
    ) = runBlocking {
        runCommunityPostCheck(
            accountStableId = first.stableId,
            watched = watched,
            mutedNames = muted,
            loader = loader,
            seen = CommunityPostSeenStore(context),
            nowEpochMillis = now,
            recordFailure = { failures += it },
            notify = { community, posts ->
                if (posts.isNotEmpty()) notified += community to posts.map(Post::id)
                posts.size
            },
        )
    }

    @Test
    fun aCheckIsSilentFirstThenNotifiesEachNewPostOnce() {
        val loader = FakeLoader(mapOf("kotlin" to listOf(post("b"), post("a"))))
        val notified = mutableListOf<Pair<String, List<String>>>()
        assertEquals(0, check(loader, notified = notified).posted)

        loader.pages = mapOf("kotlin" to listOf(post("c"), post("b"), post("a")))
        assertEquals(1, check(loader, notified = notified, now = 20_000L).posted)
        assertEquals(0, check(loader, notified = notified, now = 30_000L).posted)
        assertEquals(listOf("kotlin" to listOf("t3_c")), notified)
    }

    @Test
    fun aMutedCommunityIsNotReadAndAFailureIsRecordedWithoutStoppingTheOthers() {
        val loader = FakeLoader(mapOf("kotlin" to listOf(post("a")), "android" to listOf(post("b"))))
        check(loader, watched = listOf("android", "kotlin"), muted = setOf("Kotlin"))
        assertEquals(listOf("android"), loader.reads)

        val failures = mutableListOf<FailureDetails>()
        loader.failure = UnknownHostException("offline")
        val run = check(loader, watched = listOf("android", "kotlin"), failures = failures)
        assertTrue(run.transientNetworkFailure)
        assertEquals(2, failures.size)
        assertEquals(NetworkTransportFailureKind.DNS, failures.first().transportKind)

        loader.failure = IOException("reset")
        assertTrue(check(loader, watched = listOf("android")).transientNetworkFailure)
    }

    @Test
    fun watchingACommunityAgainStartsWithASilentBaseline() {
        val loader = FakeLoader(mapOf("kotlin" to listOf(post("a"))))
        check(loader)
        check(loader, watched = listOf("android"))
        loader.pages = mapOf("kotlin" to listOf(post("c"), post("b"), post("a")))
        val notified = mutableListOf<Pair<String, List<String>>>()
        assertEquals(0, check(loader, notified = notified).posted)
    }

    // ---- notifications ----

    @Test
    fun anNsfwPostIsNotifiedWithoutItsTitleAndHiddenContentSaysNothing() {
        assertEquals(
            CommunityPostNotificationText("r/kotlin", "Coroutines are great"),
            communityPostNotificationText("kotlin", post("a", "Coroutines are great"), hideContent = false),
        )
        assertEquals(
            CommunityPostNotificationText("r/kotlin", "New NSFW post"),
            communityPostNotificationText("kotlin", post("a", "secret title", nsfw = true), hideContent = false),
        )
        assertEquals(
            CommunityPostNotificationText("subshell", "New posts"),
            communityPostNotificationText("kotlin", post("a", "Coroutines are great"), hideContent = true),
        )
        assertEquals("https://www.reddit.com/r/kotlin/comments/a/slug/", communityPostLink("kotlin", post("a")))
        assertEquals(
            "https://www.reddit.com/r/kotlin/comments/a/",
            communityPostLink("kotlin", post("a").copy(permalink = null)),
        )
    }

    @Test
    fun eachNewPostIsItsOwnNotificationGroupedPerCommunityAndOpensThePost() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val posted = CommunityPostNotificationCenter.postNewPosts(
            context,
            first.stableId,
            "kotlin",
            listOf(post("b", "Second"), post("a", "First", nsfw = true)),
        )
        assertEquals(2, posted)
        val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).activeNotifications
        val singles = shown.filterNot { it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0 }
        assertEquals(2, singles.size)
        assertEquals(1, shown.size - singles.size)
        val texts = singles.map { it.notification.extras.getCharSequence("android.text").toString() }.toSet()
        assertEquals(setOf("Second", "New NSFW post"), texts)
        assertEquals(1, shown.map { it.notification.group }.toSet().size)
        assertEquals(COMMUNITY_POSTS_NOTIFICATION_CHANNEL_ID, singles.first().notification.channelId)
        val open = shadowOf(singles.first { it.notification.extras.getCharSequence("android.text").toString() == "Second" }
            .notification.contentIntent).savedIntent
        assertEquals("https://www.reddit.com/r/kotlin/comments/b/slug/", open.dataString)
        assertEquals(android.content.Intent.ACTION_VIEW, open.action)
    }

    @Test
    fun withoutThePermissionNothingIsPosted() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(0, CommunityPostNotificationCenter.postNewPosts(context, first.stableId, "kotlin", listOf(post("a"))))
    }

    // ---- scheduling ----

    @Test
    fun theCheckRunsOnlyWhileACommunityIsWatched() {
        ensureWorkManagerInitialized(context)
        fun state() = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(COMMUNITY_POST_CHECK_UNIQUE_WORK).get()
            .map(WorkInfo::state)

        keepCommunityPostCheckScheduled(context, first.stableId, watching = true)
        assertEquals(listOf(WorkInfo.State.ENQUEUED), state())
        keepCommunityPostCheckScheduled(context, first.stableId, watching = false)
        assertEquals(listOf(WorkInfo.State.CANCELLED), state())
    }

    // ---- the watched list in SubshellState ----

    private fun subshellState(
        accounts: List<AccountSessionSummary>,
        activeId: String,
        stage: (Map<String, Set<String>>?) -> Unit = {},
    ) = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = accounts,
        initialActiveAccountId = activeId,
        loadWatchedCommunityNames = { id -> stored[id].orEmpty() },
        persistWatchedCommunityNames = { id, names -> stored[id] = names },
        persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _, watched ->
            stage(watched)
            watched?.forEach { (id, names) -> stored[id] = names }
        },
    )

    @Test
    fun aSignedInAccountWatchesAtMostTenCommunitiesOfItsOwn() {
        val state = subshellState(listOf(first, second), first.stableId)
        assertEquals(CommunityPostWatchChange.CHANGED, state.setWatchingCommunityPosts("r/Kotlin", watch = true))
        assertTrue(state.isWatchingCommunityPosts("kotlin"))
        assertEquals(setOf("kotlin"), stored[first.stableId])
        assertEquals(CommunityPostWatchChange.UNCHANGED, state.setWatchingCommunityPosts("kotlin", watch = true))

        (1 until MAX_WATCHED_COMMUNITIES).forEach { state.setWatchingCommunityPosts("c$it", watch = true) }
        assertEquals(CommunityPostWatchChange.LIMIT_REACHED, state.setWatchingCommunityPosts("eleventh", watch = true))
        assertEquals(MAX_WATCHED_COMMUNITIES, state.watchedCommunityNames.size)

        state.switchAccount(second.stableId)
        assertEquals(emptyList<String>(), state.watchedCommunityNames)
        state.switchAccount(first.stableId)
        state.setWatchingCommunityPosts("kotlin", watch = false)
        assertFalse("kotlin" in stored.getValue(first.stableId))
    }

    @Test
    fun aLocalProfileCannotWatch() {
        val local = AccountSessionSummary(
            stableId = "local:root",
            username = "root",
            source = AccountSessionSource.LOCAL_FIXTURE,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Local",
        )
        val state = subshellState(listOf(local), local.stableId)
        assertFalse(state.communityPostNotificationsAvailable)
        assertEquals(CommunityPostWatchChange.UNAVAILABLE, state.setWatchingCommunityPosts("kotlin", watch = true))
        assertTrue(stored.isEmpty())
    }

    @Test
    fun everySignedInAccountsListIsInTheBackupAndARestoreFollowsTheModuleRules() {
        stored[second.stableId] = setOf("android")
        var staged: Map<String, Set<String>>? = null
        val state = subshellState(listOf(first, second), first.stableId) { staged = it }
        state.setWatchingCommunityPosts("kotlin", watch = true)
        val captured = state.capturePortableBackupState().watchedCommunityNamesByAccount
        assertEquals(mapOf(first.stableId to setOf("kotlin"), second.stableId to setOf("android")), captured)

        state.restoreBackupState(settings = state.captureSettings(), joinedCommunityIdsByAccount = null)
        assertNull("missing module: nothing staged", staged)
        assertEquals(listOf("kotlin"), state.watchedCommunityNames)

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            watchedCommunityNamesByAccount = mapOf(first.stableId to setOf("pics")),
        )
        assertEquals(listOf("pics"), state.watchedCommunityNames)

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            watchedCommunityNamesByAccount = emptyMap(),
        )
        assertEquals(emptyMap<String, Set<String>>(), staged)
        assertEquals(emptyList<String>(), state.watchedCommunityNames)
    }

    // ---- the backup module ----

    private fun snapshot(watched: Map<String, Set<String>>?) = SettingsSnapshot(
        id = "00000000-0000-4000-8000-000000000239",
        name = "ISSUE239",
        createdAtEpochMillis = 1_000L,
        appVersion = "test",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = SubshellSettings(),
        watchedCommunityNamesByAccount = watched,
    )

    @Test
    fun theModuleRoundTripsAndHonoursMissingEmptyCorruptAndOlderSchemas() {
        val watched = mapOf(first.stableId to setOf("Kotlin", "android"), second.stableId to setOf("pics"))
        val encoded = SettingsSnapshotCodec.encode(snapshot(watched))
        assertTrue(encoded.contains("\"schemaVersion\": 11"))
        assertTrue(encoded.contains("\"watchedCommunities\""))
        assertEquals(
            normalizeWatchedCommunityNamesByAccount(watched),
            SettingsSnapshotCodec.decode(encoded).watchedCommunityNamesByAccount,
        )

        val schemaTen = encoded.replace("\"schemaVersion\": 11", "\"schemaVersion\": 10")
        assertNull(SettingsSnapshotCodec.decode(schemaTen).watchedCommunityNamesByAccount)

        val empty = SettingsSnapshotCodec.encode(snapshot(emptyMap()))
        assertEquals(emptyMap<String, Set<String>>(), SettingsSnapshotCodec.decode(empty).watchedCommunityNamesByAccount)
        val absent = SettingsSnapshotCodec.encode(snapshot(null))
        assertFalse(absent.contains("watchedCommunities"))
        assertNull(SettingsSnapshotCodec.decode(absent).watchedCommunityNamesByAccount)

        val corrupt = JsonObject(
            Json.parseToJsonElement(encoded).jsonObject + ("watchedCommunities" to JsonArray(emptyList())),
        ).toString()
        assertNull(SettingsSnapshotCodec.decode(corrupt).watchedCommunityNamesByAccount)
    }

    private fun authenticated(id: String, username: String) = AccountSessionSummary(
        stableId = id,
        username = username,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
}
