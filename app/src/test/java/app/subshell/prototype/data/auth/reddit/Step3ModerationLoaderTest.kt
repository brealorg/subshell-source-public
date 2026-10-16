package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthenticatedRedditModQueueResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratedCommunitiesResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationTargetResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthAccountIdentityClient
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredAuthAccountRecord
import app.subshell.prototype.data.auth.StoredAuthAccountRegistry
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (mod01a1, mod01a22): moderation is read only for the account that is
 * active and signed in, with its own token and only under the mysubreddits +
 * read grant. The moderator directory follows Reddit's cursor for at most ten
 * pages; the mod queue is one GET for one normalized community; an exact
 * target post is one api/info read; a bad community name or post id sends
 * nothing. Every request is a GET.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ModerationLoaderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = setOf("identity"),
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private val accountA = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "a")
    private val accountB = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "b")

    private data class Sent(val method: String, val path: String, val query: Map<String, String?>, val authorization: String?)

    private val sent = mutableListOf<Sent>()
    private var answer: (Request) -> String = { EMPTY_LISTING }

    private val recording = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        sent += Sent(
            method = request.method,
            path = request.url.encodedPath,
            query = request.url.queryParameterNames.associateWith { request.url.queryParameter(it) },
            authorization = request.header("Authorization"),
        )
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(answer(request).toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun session(providerAccountId: String, token: String, scopes: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, "refresh-$token", "bearer", scopes, Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId, "user-$providerAccountId"),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    private fun record(accountId: String, providerAccountId: String) = StoredAuthAccountRecord(
        accountId = accountId,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerAccountId,
        username = "user-$providerAccountId",
        createdAtEpochMs = 1,
        lastUsedAtEpochMs = 1,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private fun loader(scopesB: String = "identity mysubreddits read"): RedditModerationLoader {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        store.saveSession(accountA, session("a", "token-a", "identity mysubreddits read"))
        store.saveSession(accountB, session("b", "token-b", scopesB))
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(accountA, "a"), record(accountB, "b")),
                activeAccountId = accountB,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OfflineCallFactory,
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        // The app reads the account directory at start; the loader reads it as it is.
        runBlocking { coordinator.hydrateDirectory() }
        return RedditModerationLoader(
            callFactory = recording,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun directoryPage(name: String, after: String?) =
        """{"data":{"after":${after?.let { "\"$it\"" } ?: "null"},"children":[{"kind":"t5","data":{"display_name":"$name"}}]}}"""

    @Test
    fun anAccountThatIsNotActiveReadsNothing() = runBlocking {
        val loader = loader()

        val directory = loader.loadModeratorCommunities(accountA)
        val queue = loader.loadModQueue(accountA, "kotlin", null)

        assertEquals(
            AuthenticatedRedditModerationFailureKind.SESSION,
            (directory as AuthenticatedRedditModeratedCommunitiesResult.Failed).kind,
        )
        assertEquals(
            AuthenticatedRedditModerationFailureKind.SESSION,
            (queue as AuthenticatedRedditModQueueResult.Failed).kind,
        )
        assertEquals(emptyList<Sent>(), sent)
    }

    @Test
    fun theModeratorDirectoryFollowsTheCursorForAtMostTenPages() = runBlocking {
        var page = 0
        answer = { page += 1; directoryPage("community$page", "t5_next") }

        val endless = loader().loadModeratorCommunities(accountB)

        assertEquals(10, sent.size)
        assertTrue(sent.all { it.method == "GET" && it.path == "/subreddits/mine/moderator" })
        assertTrue(sent.all { it.authorization.equals("bearer token-b", ignoreCase = true) })
        assertEquals("t5_next", sent[1].query["after"])
        assertEquals(
            (1..10).map { "community$it" }.toSet(),
            (endless as AuthenticatedRedditModeratedCommunitiesResult.Loaded).communityNames.toSet(),
        )

        sent.clear()
        page = 0
        answer = { page += 1; directoryPage("community$page", if (page == 1) "t5_next" else null) }

        val twoPages = loader().loadModeratorCommunities(accountB)

        assertEquals(2, sent.size)
        assertEquals(
            setOf("community1", "community2"),
            (twoPages as AuthenticatedRedditModeratedCommunitiesResult.Loaded).communityNames.toSet(),
        )
    }

    @Test
    fun withoutTheMysubredditsGrantNothingIsSent() = runBlocking {
        val result = loader(scopesB = "identity read").loadModeratorCommunities(accountB)

        assertTrue(result is AuthenticatedRedditModeratedCommunitiesResult.Failed)
        assertEquals(emptyList<Sent>(), sent)
    }

    @Test
    fun theModQueueIsOneGetForOneNormalizedCommunity() = runBlocking {
        val loader = loader()

        val loaded = loader.loadModQueue(accountB, "Kotlin", "t3_c")

        assertTrue(loaded is AuthenticatedRedditModQueueResult.Loaded)
        assertEquals(1, sent.size)
        assertEquals("GET", sent.single().method)
        assertEquals("/r/kotlin/about/modqueue", sent.single().path)
        assertEquals("1", sent.single().query["raw_json"])
        assertEquals("t3_c", sent.single().query["after"])

        sent.clear()
        val refused = loader.loadModQueue(accountB, "bad name!", null)

        assertEquals(
            AuthenticatedRedditModerationFailureKind.PROTOCOL,
            (refused as AuthenticatedRedditModQueueResult.Failed).kind,
        )
        assertEquals(emptyList<Sent>(), sent)
    }

    @Test
    fun anExactTargetPostIsOneInfoReadAndABadIdSendsNothing() = runBlocking {
        val loader = loader()

        loader.resolveTargetPost(accountB, "t3_ABC")

        assertEquals(1, sent.size)
        assertEquals("GET", sent.single().method)
        assertEquals("/api/info", sent.single().path)
        assertEquals("t3_abc", sent.single().query["id"])

        sent.clear()
        val refused = loader.resolveTargetPost(accountB, "../x")

        assertEquals(AuthenticatedRedditModerationTargetResult.NotFound, refused)
        assertEquals(emptyList<Sent>(), sent)
    }

    private object OfflineCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = error("no network in this test")
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private companion object {
        const val EMPTY_LISTING = """{"data":{"after":null,"children":[]}}"""
    }
}
