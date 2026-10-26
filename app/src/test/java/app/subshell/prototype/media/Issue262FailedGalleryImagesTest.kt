package app.subshell.prototype.media

import android.graphics.Bitmap
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditListingDto
import app.subshell.prototype.data.network.reddit.toNetworkPage
import app.subshell.prototype.model.PostKind
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #262: Reddit marks every image of a gallery posted through its public API
 * "failed" (no address, no type) though the images exist and reddit.com shows
 * them; the post read as a link to itself. Such images get an internal
 * address the loader resolves on i.redd.it by trying .jpeg, .png and .gif.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue262FailedGalleryImagesTest {
    private fun gallery(statuses: List<String>): app.subshell.prototype.model.Post {
        val ids = statuses.indices.map { "img$it" }
        val items = ids.joinToString(",") { """{"media_id":"$it","id":1}""" }
        val metadata = ids.zip(statuses).joinToString(",") { (id, status) ->
            if (status == "valid") {
                """"$id":{"status":"valid","e":"Image","m":"image/png","s":{"u":"https://i.redd.it/$id.png","x":10,"y":10}}"""
            } else {
                """"$id":{"status":"$status"}"""
            }
        }
        val data = """{
            "id":"g262","name":"t3_g262","subreddit":"u_tester","title":"Gallery","author":"tester",
            "is_gallery":true,"url_overridden_by_dest":"https://www.reddit.com/gallery/g262",
            "gallery_data":{"items":[$items]},"media_metadata":{$metadata}
        }"""
        return SubshellNetworkJson.providerJson.decodeFromString(
            RedditListingDto.serializer(),
            """{"data":{"children":[{"kind":"t3","data":$data}]}}""",
        ).toNetworkPage(nowEpochSeconds = 10_000L).items.single()
    }

    @Test
    fun aGalleryRedditMarkedFailedIsStillAGallery() {
        val post = gallery(listOf("failed", "failed", "valid"))

        assertEquals(PostKind.GALLERY, post.kind)
        assertEquals(
            listOf("subshell-reddit-image://img0", "subshell-reddit-image://img1", "https://i.redd.it/img2.png"),
            post.gallery.map { it.remoteImageUri },
        )
    }

    @Test
    fun onlyFailedImagesGetTheFallbackNotUnprocessedOnes() {
        val post = gallery(listOf("unprocessed", "failed"))
        assertEquals(listOf("subshell-reddit-image://img1"), post.gallery.map { it.remoteImageUri })
        assertNull(failedGalleryImageCandidates("https://i.redd.it/a.png"))
        // A real (non-404) failure is reported as i.redd.it's, not "other".
        assertEquals("media_image_i_redd_it", remoteMediaDiagnosticScope("media_image", "subshell-reddit-image://abc"))
        assertEquals(
            listOf("jpeg", "png", "gif", "webp").map { "https://i.redd.it/abc.$it" },
            failedGalleryImageCandidates("subshell-reddit-image://abc"),
        )
    }

    @Test
    fun theLoaderTriesTheExtensionsUntilIReddItServesOne() = runBlocking {
        val requested = CopyOnWriteArrayList<String>()
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                requested += url
                val found = url.endsWith(".png")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (found) 200 else 404)
                    .message("scripted")
                    .body(
                        if (found) png.toResponseBody("image/png".toMediaType())
                        else "missing".toResponseBody("image/png".toMediaType()),
                    )
                    .build()
            }
            .build()

        val result = RemoteImageLoader.load("subshell-reddit-image://issue262png", client)

        assertTrue(result is RemoteMediaLoadResult.Ready)
        assertEquals(
            listOf("https://i.redd.it/issue262png.jpeg", "https://i.redd.it/issue262png.png"),
            requested,
        )
    }
}
