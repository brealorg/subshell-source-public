package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.NsfwPreferencesResult
import io.github.brealorg.subshell.data.account.RedditAccessCheckResult
import io.github.brealorg.subshell.data.account.RedditAccessProbe
import io.github.brealorg.subshell.data.account.RedditAccessProbeOutcome
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.auth.authenticatedAccountId
import io.github.brealorg.subshell.data.diagnostics.RedditAccessCheckStore
import io.github.brealorg.subshell.data.diagnostics.appendRedditAccessCheck
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.NsfwPreferencesNetwork
import io.github.brealorg.subshell.data.network.reddit.REDDIT_ACCESS_PROBE_ENDPOINTS
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITCHECK01_CHECK_REDDIT_ACCESS_V1 (2026-10-31): Diagnostics › Check Reddit
 * access asks Reddit once for each kind of read, with the active account, never
 * writes, and keeps only endpoint names, statuses and reasons for the report.
 */
class RedditCheck01AccessCheckTest {
    private val config = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "test-client", redirectUri = "test://callback",
        scopes = setOf("identity", "read"), permanentAccess = true,
    )
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, config.clientMode, "me")
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity read", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = config.clientMode, clientIdFingerprint = config.clientIdFingerprint,
    )
    private val unusedNetwork = NsfwPreferencesNetwork { NsfwPreferencesResult.Failed }

    private val blockPage = "<html>" + " ".repeat(189_000) + "You've been blocked by network security.</html>"
    private val methods = mutableListOf<String>()
    private val paths = mutableListOf<String>()

    /** Reddit as measured on 2026-10-31: three id lookups blocked, the rest answered. */
    private fun reddit(answer: (String) -> Pair<Int, String>? = { null }) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            methods += request.method
            paths += path
            val (code, body) = answer(path) ?: when {
                path == "/api/v1/me" -> 200 to """{"name":"tester"}"""
                path == "/best" -> 200 to
                    """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"p1","subreddit":"subshell"}}]}}"""
                path == "/comments/p1" -> 200 to
                    """[{"kind":"Listing","data":{"children":[]}},""" +
                    """{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"id":"c1"}}]}}]"""
                path == "/api/info" || path.startsWith("/by_id") || path == "/api/morechildren" -> 403 to blockPage
                else -> 200 to "{}"
            }
            val type = if (body.startsWith("<")) "text/html" else "application/json"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("answer")
                .body(body.toResponseBody(type.toMediaType())).build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
    )

    private fun service(
        client: SubshellHttpClient?,
        active: () -> String? = { account },
    ) = ConfiguredRedditCommunityAccessService(
        configuration = config,
        activeAccountId = active,
        ensureSession = { _, _ -> AuthSessionResult.Authenticated(session) },
        networkForSession = { unusedNetwork },
        probeClientForSession = client?.let { c -> { c } },
    )

    @Test
    fun everyReadIsAskedOnceWithGetAndTheBlockedOnesAreNamed() = runBlocking {
        val result = service(reddit()).check(account) { true } as RedditAccessCheckResult.Checked

        assertEquals(REDDIT_ACCESS_PROBE_ENDPOINTS, result.probes.map { it.endpoint })
        assertEquals(setOf("GET"), methods.toSet())
        assertEquals(REDDIT_ACCESS_PROBE_ENDPOINTS.size, paths.size)
        val blocked = result.probes.filter { it.outcome == RedditAccessProbeOutcome.HTTP_FAILURE }
        assertEquals(listOf("api_info", "by_id", "morechildren"), blocked.map { it.endpoint })
        assertTrue(blocked.all { it.statusCode == 403 && it.detail == "network_security_block" })
        assertTrue(result.probes.filterNot { it in blocked }.all { it.outcome == RedditAccessProbeOutcome.ANSWERED })
        // Inputs come from the earlier answers.
        assertTrue("/user/tester/about" in paths)
        assertTrue("/r/subshell/about" in paths)
        assertTrue("/by_id/t3_p1" in paths)
    }

    @Test
    fun aReadWhoseInputFailedIsSkippedNotGuessed() = runBlocking {
        val result = service(
            reddit { path -> if (path == "/api/v1/me" || path == "/best") 429 to "{}" else null },
        ).check(account) { true } as RedditAccessCheckResult.Checked

        val skipped = result.probes.filter { it.outcome == RedditAccessProbeOutcome.SKIPPED }.map { it.endpoint }
        assertEquals(
            listOf(
                "community_about", "thread", "api_info", "by_id", "morechildren", "duplicates",
                "user_about", "user_comments",
            ),
            skipped,
        )
        assertEquals(429, result.probes.first { it.endpoint == "me" }.statusCode)
        assertFalse(paths.any { it.startsWith("/user/") || it.startsWith("/comments/") })
    }

    @Test
    fun anAccountChangeStopsTheCheck() = runBlocking {
        var active: String? = account
        val client = reddit { path ->
            if (path == "/best") active = "another"
            null
        }

        assertEquals(RedditAccessCheckResult.NotChecked, service(client) { active }.check(account) { true })
        assertFalse("/message/inbox" in paths)
    }

    @Test
    fun noProbeClientOrNoSessionIsNotChecked() = runBlocking {
        assertEquals(RedditAccessCheckResult.NotChecked, service(null).check(account) { true })
        assertEquals(RedditAccessCheckResult.NotChecked, service(reddit()) { "another" }.check(account) { true })
        assertTrue(paths.isEmpty())
    }

    @Test
    fun theReportKeepsNamesStatusesAndReasonsOnly() {
        val directory = Files.createTempDirectory("reddit-access-check").toFile()
        val store = RedditAccessCheckStore(directory)
        store.write(
            listOf(
                RedditAccessProbe("me", RedditAccessProbeOutcome.ANSWERED, 200, null, 120),
                RedditAccessProbe("api_info", RedditAccessProbeOutcome.HTTP_FAILURE, 403, "network_security_block", 80),
                RedditAccessProbe("inbox", RedditAccessProbeOutcome.TRANSPORT_FAILURE, null, "timeout", 30_000),
                RedditAccessProbe("user_about", RedditAccessProbeOutcome.SKIPPED),
                // Nothing unsafe reaches the file: a reason with a URL and a made-up endpoint name.
                RedditAccessProbe("thread", RedditAccessProbeOutcome.HTTP_FAILURE, 404, "https://x/u/tester", 10),
                RedditAccessProbe("/user/tester", RedditAccessProbeOutcome.ANSWERED, 200, null, 10),
            ),
            atEpochMs = 0L,
        )

        val report = buildString { appendRedditAccessCheck(store.read()) }

        assertEquals(
            """

            [reddit_access_check]
            checked_utc=1970-01-01T00:00:00.000Z
            me ok http=200 ms=120
            api_info http http=403 detail=network_security_block ms=80
            inbox transport detail=timeout ms=30000
            user_about skipped
            thread http http=404 ms=10

            """.trimIndent(),
            report,
        )
        store.clear()
        assertEquals("", buildString { appendRedditAccessCheck(store.read()) })
        directory.deleteRecursively()
    }
}
