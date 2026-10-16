package app.subshell.prototype.media

import android.net.Uri
import androidx.media3.datasource.DataSpec
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditListingDto
import app.subshell.prototype.data.network.reddit.toNetworkPage
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.redGifsClipId
import app.subshell.prototype.ui.SubshellState
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1 (#207). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue207RedGifsTest {
    @Test
    fun redGifsUrlShapesGiveTheClipIdInLowerCase() {
        assertEquals("squeakyhelplesswisent", redGifsClipId("https://www.redgifs.com/watch/SqueakyHelplessWisent"))
        assertEquals("squeakyhelplesswisent", redGifsClipId("https://redgifs.com/ifr/squeakyhelplesswisent?x=1"))
        assertEquals("squeakyhelplesswisent", redGifsClipId("https://v3.redgifs.com/watch/squeakyhelplesswisent#t"))
        assertEquals("squeakyhelplesswisent", redGifsClipId("https://thumbs2.redgifs.com/SqueakyHelplessWisent-mobile.mp4"))
        assertEquals("squeakyhelplesswisent", redGifsClipId("https://media.redgifs.com/SqueakyHelplessWisent.mp4"))

        assertNull(redGifsClipId("https://i.redgifs.com/i/squeakyhelplesswisent.jpg"))
        assertNull(redGifsClipId("https://www.redgifs.com/users/someone"))
        assertNull(redGifsClipId("https://www.redgifs.com/watch/"))
        assertNull(redGifsClipId("https://notredgifs.com/watch/abc"))
        assertNull(redGifsClipId("https://imgur.com/watch/abc"))
        assertNull(redGifsClipId(null))
    }

    @Test
    fun anNsfwRedGifsPostWithoutRedditsCopyBecomesAVideoThePlayerResolves() {
        val post = listingPost(over18 = true, redditPreview = false)

        assertEquals(PostKind.VIDEO, post.kind)
        assertEquals("subshell-redgifs://squeakyhelplesswisent", post.remoteVideoUri)
        assertEquals(PostKind.VIDEO, listingPost(over18 = true, redditPreview = false, crossposted = true).crosspost?.kind)
    }

    @Test
    fun redditsOwnCopyPlaysFirstAndAnUnmarkedPostStaysALink() {
        val withCopy = listingPost(over18 = true, redditPreview = true)
        assertEquals(PostKind.VIDEO, withCopy.kind)
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8", withCopy.remoteVideoUri)

        val unmarked = listingPost(over18 = false, redditPreview = false)
        assertEquals(PostKind.LINK, unmarked.kind)
        assertNull(unmarked.remoteVideoUri)
    }

    @Test
    fun aClipIsResolvedToHdWithOneFixedUserAgentAndTheTokenIsReused() {
        val server = FakeRedGifs()
        val resolver = server.resolver()

        assertEquals("https://media.redgifs.com/Clip.mp4", resolver.resolve("clip"))
        assertEquals("https://media.redgifs.com/Clip.mp4", resolver.resolve("clip"))

        assertEquals(listOf("/v2/auth/temporary", "/v2/gifs/clip", "/v2/gifs/clip"), server.paths)
        assertEquals(1, server.userAgents.distinct().size)
        assertEquals(listOf(null, "Bearer t1", "Bearer t1"), server.authorizations)
    }

    @Test
    fun aRefusedTokenIsRenewedOnceAndASecondRefusalFails() {
        val renewed = FakeRedGifs(refuseTokens = setOf("t1"))
        assertEquals("https://media.redgifs.com/Clip.mp4", renewed.resolver().resolve("clip"))
        assertEquals(2, renewed.paths.count { it == "/v2/auth/temporary" })

        val refused = FakeRedGifs(refuseTokens = setOf("t1", "t2"))
        try {
            refused.resolver().resolve("clip")
            fail("a second 401 is a failure")
        } catch (failure: VideoProviderHttpException) {
            assertEquals(401, failure.statusCode)
        }
        assertEquals(2, refused.paths.count { it == "/v2/auth/temporary" })
        assertEquals(2, refused.paths.count { it == "/v2/gifs/clip" })
    }

    @Test
    fun sdIsTheFallbackAndNoStreamOrAGoneClipIsAFailure() {
        assertEquals(
            "https://media.redgifs.com/Clip-mobile.mp4",
            FakeRedGifs(urls = """{"sd":"https://media.redgifs.com/Clip-mobile.mp4"}""").resolver().resolve("clip"),
        )
        try {
            FakeRedGifs(urls = "{}").resolver().resolve("clip")
            fail("no stream is a failure")
        } catch (_: VideoProviderResponseException) {
        }
        try {
            FakeRedGifs(lookupStatus = 404).resolver().resolve("clip")
            fail("a 404 means the clip is gone")
        } catch (_: VideoClipUnavailableException) {
        }
        val copy = videoPlaybackErrorCopy(
            errorCode = 2000,
            failure = VideoPlaybackFailure(
                mediaId = "t3_x",
                attemptId = 1L,
                sources = listOf(
                    videoSourceFailure("t3_x", 1L, 0, 2000, IOException(VideoClipUnavailableException())) { null }!!,
                ),
            ),
        )
        assertEquals("Clip not available", copy.title)
    }

    @Test
    fun withShowNsfwPostsOffNothingIsAskedOfRedGifs() {
        val server = FakeRedGifs()
        val state = SubshellState(initialShowNsfwPosts = true)
        assertTrue(RedGifsPlayback.allowed)
        val resolver = server.resolver(allowed = { RedGifsPlayback.allowed })
        resolver.resolve("clip")

        state.updateShowNsfwPosts(false)

        assertFalse(RedGifsPlayback.allowed)
        try {
            resolver.resolve("other")
            fail("RedGIFs is off with Show NSFW posts off")
        } catch (_: VideoProviderOffException) {
        }
        try {
            RedGifsDataSpecResolver(resolver).resolveDataSpec(DataSpec(Uri.parse("subshell-redgifs://other")))
            fail("the player is refused too")
        } catch (_: VideoProviderOffException) {
        }
        assertEquals(listOf("/v2/auth/temporary", "/v2/gifs/clip"), server.paths)
    }

    @Test
    fun thePlayerResolvesOnlyRedGifsSourcesAndCountsThemAsRemote() {
        val resolver = FakeRedGifs().resolver()
        val plain = DataSpec(Uri.parse("https://v.redd.it/abc/DASH_720.mp4"))
        assertTrue(RedGifsDataSpecResolver(resolver).resolveDataSpec(plain) === plain)
        assertEquals(
            "https://media.redgifs.com/Clip.mp4",
            RedGifsDataSpecResolver(resolver).resolveDataSpec(DataSpec(Uri.parse("subshell-redgifs://clip"))).uri.toString(),
        )
        assertTrue(isRemoteMediaUri("subshell-redgifs://clip"))
        assertEquals(
            "subshell:video:t3_x:source:0",
            progressiveRemoteCacheKey("subshell-redgifs://clip", VideoMediaSourceKind.PROGRESSIVE, null, "subshell:video:t3_x:source:0"),
        )
    }

    private class FakeRedGifs(
        private val refuseTokens: Set<String> = emptySet(),
        private val urls: String = """{"hd":"https://media.redgifs.com/Clip.mp4","sd":"https://media.redgifs.com/Clip-mobile.mp4"}""",
        private val lookupStatus: Int = 200,
    ) {
        val paths = mutableListOf<String>()
        val userAgents = mutableListOf<String?>()
        val authorizations = mutableListOf<String?>()
        private var issued = 0

        fun resolver(allowed: () -> Boolean = { true }) = RedGifsClipResolver(
            callFactory = OkHttpClient.Builder().addInterceptor(Interceptor { chain -> answer(chain.request()) }).build(),
            apiBase = "https://api.example.test",
            allowed = allowed,
        )

        private fun answer(request: Request): Response {
            val path = request.url.encodedPath
            paths += path
            userAgents += request.header("User-Agent")
            authorizations += request.header("Authorization")
            val (code, body) = when {
                path == "/v2/auth/temporary" -> 200 to """{"token":"t${++issued}"}"""
                request.header("Authorization")?.removePrefix("Bearer ") in refuseTokens -> 401 to "{}"
                lookupStatus != 200 -> lookupStatus to "{}"
                else -> 200 to """{"gif":{"id":"clip","urls":$urls}}"""
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun listingPost(over18: Boolean, redditPreview: Boolean, crossposted: Boolean = false): Post {
        val videoPreview = if (redditPreview) {
            ""","reddit_video_preview":{"hls_url":"https://v.redd.it/abc/HLSPlaylist.m3u8","width":480,"height":640}"""
        } else {
            ""
        }
        val source = """{
            "id":"src207","name":"t3_src207","subreddit":"clips","title":"A clip","author":"a",
            "post_hint":"rich:video","domain":"redgifs.com","over_18":$over18,
            "url_overridden_by_dest":"https://www.redgifs.com/watch/SqueakyHelplessWisent",
            "preview":{"images":[{"source":{"url":"https://external-preview.redd.it/p.jpg","width":480,"height":640}}]$videoPreview}
        }"""
        val data = if (crossposted) {
            """{"id":"x207","name":"t3_x207","subreddit":"other","title":"Crosspost","author":"b",
                "over_18":$over18,"crosspost_parent":"t3_src207","crosspost_parent_list":[$source]}"""
        } else {
            source
        }
        val listing = SubshellNetworkJson.providerJson.decodeFromString(
            RedditListingDto.serializer(),
            """{"data":{"children":[{"kind":"t3","data":$data}]}}""",
        )
        return listing.toNetworkPage(nowEpochSeconds = 10_000L).items.single()
    }
}
