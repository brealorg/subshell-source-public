package app.subshell.prototype.offline

import android.graphics.Bitmap
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.media.RemoteImageLoader
import app.subshell.prototype.media.RemoteMediaLoadResult
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.Post
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE265_SAVE_FOR_OFFLINE_V1 (owner 2026-10-27): "Save for offline" in the
 * Home menu stores the top 50 posts of the feed, each Best comment thread and
 * the posts' images (500 MB, least recently read go first); the image loader
 * reads saved images before the network. Videos are not saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue265SaveForOfflineTest {
    private val directory: File = Files.createTempDirectory("offline-images").toFile()

    @After
    fun tearDown() {
        RemoteImageLoader.offlineImageStore = null
        directory.deleteRecursively()
    }

    @Test
    fun theStoreKeepsImagesAndDropsTheLeastRecentlyReadOverItsLimit() {
        var now = 1_000L
        val store = OfflineImageStore(directory, maxBytes = 25, nowEpochMillis = { now })
        assertTrue(store.write("https://i.redd.it/a.png", ByteArray(10) { 1 }))
        now += 10_000
        assertTrue(store.write("https://i.redd.it/b.png", ByteArray(10) { 2 }))
        now += 10_000
        assertArrayEquals(ByteArray(10) { 1 }, store.read("https://i.redd.it/a.png"))
        now += 10_000
        assertTrue(store.write("https://i.redd.it/c.png", ByteArray(10) { 3 }))

        assertTrue(store.contains("https://i.redd.it/a.png"))
        assertFalse("b was read least recently", store.contains("https://i.redd.it/b.png"))
        assertTrue(store.contains("https://i.redd.it/c.png"))
        assertEquals(20L, store.sizeBytes())
        assertFalse("larger than the whole store", store.write("https://i.redd.it/big.png", ByteArray(26)))

        store.clear()
        assertEquals(0L, store.sizeBytes())
        assertNull(store.read("https://i.redd.it/a.png"))
    }

    private val base = SampleData.posts.first()

    private fun post(id: String, nsfw: Boolean = false, comments: Int = 3) = base.copy(
        id = "t3_$id",
        nsfw = nsfw,
        comments = comments,
        remoteImageUri = "https://i.redd.it/$id.png",
        gallery = listOf(GalleryItem(id = "g", label = "g", format = app.subshell.prototype.model.MediaFormat.IMAGE, remoteImageUri = "https://i.redd.it/$id-g.png")),
    )

    private val key = RepositoryQueryKey("account-feed")

    private class FakeComments(private val failing: Set<String> = emptySet()) : AuthenticatedRedditCommentLoader {
        val read = mutableListOf<Pair<String, RedditCommentSort>>()
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) = error("unused")
        override suspend fun synchronize(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
        ): AuthenticatedRedditCommentResult {
            read += postId to sort
            if (postId in failing) throw IllegalStateException("thread failed")
            return AuthenticatedRedditCommentResult.Loaded(comments = emptyList<Comment>(), fromCache = false)
        }
        override suspend fun loadMore(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
        ) = error("unused")
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ) = error("unused")
    }

    @Test
    fun aSaveReadsPagesUntilFiftyPostsThenEveryThreadAndImage() = runBlocking {
        val stored = mutableListOf<Post>()
        val operations = mutableListOf<RepositoryLoadOperation>()
        val updater = AuthenticatedRedditFeedUpdater { _, operation, _ ->
            operations += operation
            repeat(30) { stored += post("p${stored.size}") }
            AuthenticatedRedditFeedUpdateResult.Applied(key, operation, 30, stored.size, endReached = false)
        }
        val comments = FakeComments(failing = setOf("t3_p1"))
        val savedImages = mutableListOf<String>()
        val progress = mutableListOf<Pair<Int, Int>>()

        val run = runOfflineSave(
            accountStableId = "a",
            target = AuthenticatedRedditFeedTarget(),
            includeNsfw = false,
            feedUpdater = updater,
            storedPosts = { stored.toList() },
            commentLoader = comments,
            saveImage = { uri -> savedImages += uri; true },
            progress = { done, total -> progress += done to total },
        )

        assertEquals(listOf(RepositoryLoadOperation.REFRESH, RepositoryLoadOperation.APPEND), operations)
        assertEquals(OfflineSaveRun(posts = 50, threads = 49, images = 100, feedFailed = false), run)
        assertEquals(50, comments.read.size)
        assertTrue(comments.read.all { it.second == RedditCommentSort.BEST })
        assertTrue("https://i.redd.it/p0-g.png" in savedImages)
        assertEquals(50 to 50, progress.last())
    }

    @Test
    fun nsfwImagesAreSavedOnlyWhileNsfwPostsAreShown() = runBlocking {
        val stored = listOf(post("safe"), post("adult", nsfw = true))
        val updater = AuthenticatedRedditFeedUpdater { _, operation, _ ->
            AuthenticatedRedditFeedUpdateResult.Applied(key, operation, 2, 2, endReached = true)
        }
        suspend fun saved(includeNsfw: Boolean) = mutableListOf<String>().also { images ->
            runOfflineSave("a", AuthenticatedRedditFeedTarget(), includeNsfw, updater, { stored }, null, { images += it; true }, { _, _ -> })
        }
        assertEquals(listOf("https://i.redd.it/safe.png", "https://i.redd.it/safe-g.png"), saved(includeNsfw = false))
        assertEquals(4, saved(includeNsfw = true).size)
    }

    @Test
    fun aFeedThatCannotBeReadSavesNothing() = runBlocking {
        val updater = AuthenticatedRedditFeedUpdater { _, _, _ ->
            AuthenticatedRedditFeedUpdateResult.Failed(AuthenticatedRedditFeedUpdateFailureKind.entries.first(), retryable = true)
        }
        var images = 0
        val run = runOfflineSave("a", AuthenticatedRedditFeedTarget(), true, updater, { emptyList() }, FakeComments(), { images += 1; true }, { _, _ -> })
        assertTrue(run.feedFailed)
        assertEquals(0, images)
    }

    @Test
    fun videosAreNotSavedOnlyTheirStillImages() {
        val video = base.copy(remoteImageUri = "https://i.redd.it/poster.png", remoteVideoUri = "https://v.redd.it/v/DASH.mp4", gallery = emptyList())
        assertEquals(listOf("https://i.redd.it/poster.png"), offlineImageUris(video))
    }

    private val png = ByteArrayOutputStream().also {
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private val offline = object : Call.Factory {
        override fun newCall(request: Request): Call = error("offline: no network in this test")
    }

    @Test
    fun theLoaderShowsASavedImageWithoutTheNetwork() = runBlocking {
        val store = OfflineImageStore(directory)
        store.write("https://i.redd.it/saved265.png", png)
        RemoteImageLoader.offlineImageStore = store

        val result = RemoteImageLoader.load("https://i.redd.it/saved265.png", callFactory = offline)

        assertTrue(result.toString(), result is RemoteMediaLoadResult.Ready)
    }

    @Test
    fun aRefusedAddressNeverGetsASavedImage() = runBlocking {
        val refused = "http://user:secret@insecure.example/saved265.png"
        val store = OfflineImageStore(directory)
        store.write(refused, png)
        RemoteImageLoader.offlineImageStore = store

        val result = RemoteImageLoader.load(refused, callFactory = offline)

        assertTrue(result.toString(), result is RemoteMediaLoadResult.Failed)
    }

    @Test
    fun savingUsesTheDisplayRulesAndStoresTheImage() = runBlocking {
        val store = OfflineImageStore(directory)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(png.toResponseBody("image/png".toMediaType())).build()
        }.build()

        assertTrue(RemoteImageLoader.saveForOffline("https://i.redd.it/fresh265.png", store, client))
        assertArrayEquals(png, store.read("https://i.redd.it/fresh265.png"))
        assertFalse("not an https public host", RemoteImageLoader.saveForOffline("http://10.0.0.1/x.png", store, client))
    }
}
