package app.subshell.prototype.media

import android.net.Uri
import androidx.media3.datasource.DataSpec
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditListingDto
import app.subshell.prototype.data.network.reddit.toNetworkPage
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.remoteVideoMediaSources
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #256: an NSFW RedGIFs post plays the RedGIFs original (with sound) first, in
 * HD then SD, and Reddit's silent copies follow as fallbacks; a RedGIFs source
 * has its own cache key, and a slow RedGIFs gives way after 8 seconds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue256RedGifsWithSoundTest {
    @Test
    fun theRedGifsOriginalPlaysInHdThenSdThenRedditsCopies() {
        val post = listingPost(over18 = true, gifMp4 = true, redditPreview = true)

        assertEquals(PostKind.VIDEO, post.kind)
        val sources = remoteVideoMediaSources(post)
        assertEquals(
            listOf(
                "subshell-redgifs://squeakyhelplesswisent",
                "subshell-redgifs://squeakyhelplesswisent/sd",
                "https://external-preview.redd.it/clip.mp4",
                "https://v.redd.it/abc/HLSPlaylist.m3u8",
            ),
            sources.map { it.uri },
        )
        assertEquals(
            listOf(
                "subshell:redgifs:squeakyhelplesswisent:hd",
                "subshell:redgifs:squeakyhelplesswisent:sd",
                null,
                null,
            ),
            sources.map { it.cacheKey },
        )
    }

    @Test
    fun aSafeForWorkRedGifsLinkAndAnOrdinaryVideoStayAsTheyWere() {
        val sfw = listingPost(over18 = false, gifMp4 = true, redditPreview = true)
        assertEquals("https://external-preview.redd.it/clip.mp4", sfw.remoteVideoUri)
        assertEquals(listOf(null), remoteVideoMediaSources(sfw).map { it.cacheKey }.distinct())

        assertEquals(listOf("https://v.redd.it/x/HLSPlaylist.m3u8"), redGifsQualitySources("https://v.redd.it/x/HLSPlaylist.m3u8"))
        assertEquals(listOf("subshell-redgifs://clip/sd"), redGifsQualitySources("subshell-redgifs://clip/sd"))
    }

    @Test
    fun theSdSourceAsksForSdOnlyAndTheHdSourceForHdFirst() {
        val both = """{"hd":"https://media.redgifs.com/Clip.mp4","sd":"https://media.redgifs.com/Clip-mobile.mp4"}"""
        val resolver = RedGifsDataSpecResolver(resolver(both))
        assertEquals(
            "https://media.redgifs.com/Clip.mp4",
            resolver.resolveDataSpec(DataSpec(Uri.parse("subshell-redgifs://clip"))).uri.toString(),
        )
        assertEquals(
            "https://media.redgifs.com/Clip-mobile.mp4",
            resolver.resolveDataSpec(DataSpec(Uri.parse("subshell-redgifs://clip/sd"))).uri.toString(),
        )
        try {
            resolver("""{"hd":"https://media.redgifs.com/Clip.mp4"}""").resolve("clip", RedGifsQuality.SD)
            fail("an SD source without SD fails, so Reddit's copy plays next")
        } catch (_: VideoProviderResponseException) {
        }
    }

    @Test
    fun aRedGifsLookupWaitsAtMostEightSeconds() {
        val timeouts = mutableListOf<Long>()
        val resolver = RedGifsClipResolver(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        timeouts += chain.call().timeout().timeoutNanos()
                        answer(chain.request(), """{"hd":"https://media.redgifs.com/Clip.mp4"}""")
                    },
                )
                .build(),
            apiBase = "https://api.example.test",
            allowed = { true },
        )

        resolver.resolve("clip")

        assertEquals(listOf(TimeUnit.SECONDS.toNanos(8), TimeUnit.SECONDS.toNanos(8)), timeouts)
    }

    @Test
    fun theClipIdIgnoresTheQuality() {
        assertEquals("clip", redGifsClipIdOfVideoUri("subshell-redgifs://clip/sd"))
        assertEquals(RedGifsQuality.SD, redGifsQualityOfVideoUri("subshell-redgifs://clip/sd"))
        assertEquals(RedGifsQuality.HD, redGifsQualityOfVideoUri("subshell-redgifs://clip"))
        assertNull(redGifsCacheKeyOf("https://v.redd.it/x/DASH_720.mp4"))
    }

    private fun resolver(urls: String) = RedGifsClipResolver(
        callFactory = OkHttpClient.Builder().addInterceptor(Interceptor { chain -> answer(chain.request(), urls) }).build(),
        apiBase = "https://api.example.test",
        allowed = { true },
    )

    private fun answer(request: Request, urls: String): Response {
        val body = if (request.url.encodedPath == "/v2/auth/temporary") {
            """{"token":"t"}"""
        } else {
            """{"gif":{"id":"clip","urls":$urls}}"""
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("x")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun listingPost(over18: Boolean, gifMp4: Boolean, redditPreview: Boolean): Post {
        val variants = if (gifMp4) {
            ""","variants":{"mp4":{"source":{"url":"https://external-preview.redd.it/clip.mp4","width":480,"height":640}}}"""
        } else {
            ""
        }
        val videoPreview = if (redditPreview) {
            ""","reddit_video_preview":{"hls_url":"https://v.redd.it/abc/HLSPlaylist.m3u8","width":480,"height":640}"""
        } else {
            ""
        }
        val data = """{
            "id":"src256","name":"t3_src256","subreddit":"clips","title":"A clip","author":"a",
            "post_hint":"rich:video","domain":"redgifs.com","over_18":$over18,
            "url_overridden_by_dest":"https://www.redgifs.com/watch/SqueakyHelplessWisent",
            "preview":{"images":[{"source":{"url":"https://external-preview.redd.it/p.jpg","width":480,"height":640}$variants}]$videoPreview}
        }"""
        val listing = SubshellNetworkJson.providerJson.decodeFromString(
            RedditListingDto.serializer(),
            """{"data":{"children":[{"kind":"t3","data":$data}]}}""",
        )
        return listing.toNetworkPage(nowEpochSeconds = 10_000L).items.single()
    }
}
