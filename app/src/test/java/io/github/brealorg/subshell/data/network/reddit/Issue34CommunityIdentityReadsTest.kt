package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.network.NetworkBearerTokenProvider
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.model.Community
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE34_EVERY_COMMUNITY_IS_ASKED_ONCE_V1 (#34, /code-review of #281): every
 * community asked for is read from its about page, a few at a time; a refused
 * one (403/404) comes back as its fallback so the caller counts it as asked; a
 * transport failure is Offline, so the caller stops instead of asking on.
 */
class Issue34CommunityIdentityReadsTest {
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val inFlight = AtomicInteger()
    private val mostInFlight = AtomicInteger()

    private fun reddit(answer: (String) -> Int) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            paths += path
            val now = inFlight.incrementAndGet()
            mostInFlight.accumulateAndGet(now, ::maxOf)
            try {
                Thread.sleep(40)
                val code = answer(path)
                if (code == 0) throw IOException("offline")
                val name = path.removePrefix("/r/").removeSuffix("/about")
                val body = if (code == 200) {
                    """{"kind":"t5","data":{"display_name":"$name","name":"t5_$name","community_icon":"https://styles.redditmedia.com/$name.png"}}"""
                } else {
                    """{"reason":"private"}"""
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("answer")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            } finally {
                inFlight.decrementAndGet()
            }
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
        bearerTokenProvider = NetworkBearerTokenProvider { "token" },
    )

    private fun communities(count: Int) =
        (1..count).map { index -> Community("t5_c$index", "c$index", "", "", 0L) }

    @Test
    fun everyNameIsReadAFewAtATime() = runBlocking {
        val result = RedditIdentityNetworkDataSource(reddit { 200 }).communities(communities(20))

        val read = (result as NetworkResult.Success).value
        assertEquals(20, read.size)
        assertEquals(20, paths.size)
        assertTrue(read.all { it.iconImageUri != null })
        assertTrue("at most 4 at once, was ${mostInFlight.get()}", mostInFlight.get() in 2..4)
    }

    @Test
    fun aRefusedCommunityComesBackAsItsFallback() = runBlocking {
        val result = RedditIdentityNetworkDataSource(
            reddit { path -> if (path == "/r/c2/about") 403 else if (path == "/r/c3/about") 404 else 200 },
        ).communities(communities(3))

        val read = (result as NetworkResult.Success).value
        assertEquals(listOf("c1", "c2", "c3"), read.map(Community::name).sorted())
        assertEquals(null, read.first { it.name == "c2" }.iconImageUri)
    }

    @Test
    fun aTransportFailureIsOffline() = runBlocking {
        val result = RedditIdentityNetworkDataSource(
            reddit { path -> if (path == "/r/c4/about") 0 else 200 },
        ).communities(communities(6))

        assertTrue(result.toString(), result is NetworkResult.TransportFailure)
    }
}
