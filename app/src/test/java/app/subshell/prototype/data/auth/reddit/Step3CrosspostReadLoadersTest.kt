package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (crosspost01a1c, crosspost01a1e): what a crosspost needs is read from
 * Reddit with the owner's own token and nothing is written — the destination
 * says whether it takes crossposts (Reddit's "crosspostable" first, else
 * "allow_post_crossposts"), and the source is read fresh by its fullname and
 * is available only while Reddit says it can be crossposted; without the
 * read grant nothing is asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CrosspostReadLoadersTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.coreReadScopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val owner = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "o")
    private val requests = mutableListOf<Request>()

    private fun server(answer: (String) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(answer(request.url.encodedPath).toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun coordinator(scopes: String = "identity mysubreddits read"): AuthAccountSessionCoordinator {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
        store.clearAll()
        store.saveSession(
            owner,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("token-o", "refresh-o", "bearer", scopes, Long.MAX_VALUE),
                identity = AuthAccountIdentity("o", "crossposter"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = owner,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "o",
                        username = "crossposter",
                        createdAtEpochMs = 1,
                        lastUsedAtEpochMs = 1,
                        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                    ),
                ),
                activeAccountId = owner,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = OfflineCallFactory, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { coordinator.hydrateDirectory() }
        return coordinator
    }

    private fun destination(about: String) = runBlocking {
        val result = ConfiguredRedditComposerPolicyLoader(
            accountCoordinator = coordinator(),
            callFactory = server { """{"kind":"t5","data":$about}""" },
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
        ).load(owner, "target")
        (result as AuthenticatedComposerPolicyResult.Loaded).policy.crosspostDestination
    }

    @Test
    fun theDestinationSaysWhetherItTakesCrosspostsForTheOwner() {
        val refused = destination("""{"crosspostable":false,"allow_post_crossposts":true,"subreddit_type":"public"}""")
        assertEquals(false, refused?.allowCrossposts)
        assertEquals(owner, refused?.accountStableId)

        val allowed = destination("""{"allow_post_crossposts":true,"subreddit_type":"public"}""")
        assertEquals(true, allowed?.allowCrossposts)

        assertTrue(requests.isNotEmpty())
        assertEquals(setOf("GET"), requests.map { it.method }.toSet())
        assertEquals(setOf("Bearer token-o"), requests.map { it.header("Authorization") }.toSet())
    }

    // ISSUE281_READS_AVOID_API_INFO_V1: the source comes from its thread
    // (post listing, then comments).
    private fun sourceListing(crosspostable: Boolean = true, removed: String? = null) =
        """[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
            "name":"t3_source123","id":"source123","subreddit":"source","author":"author",
            "title":"Original title","over_18":true,"spoiler":false,"is_crosspostable":$crosspostable
            ${removed?.let { ",\"removed_by_category\":\"$it\"" }.orEmpty()}
        }}]}},{"kind":"Listing","data":{"children":[]}}]"""

    private fun source(listing: String, scopes: String = "identity mysubreddits read") = runBlocking {
        ConfiguredRedditCrosspostSourceLoader(
            accountCoordinator = coordinator(scopes),
            callFactory = server { listing },
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
        ).load(owner, "t3_source123")
    }

    @Test
    fun theSourceIsReadFreshByItsFullnameWithTheOwnersToken() {
        val result = source(sourceListing())

        val evidence = (result as CrosspostSourceLoadResult.Available).evidence
        assertEquals("t3_source123", evidence.fullname)
        assertEquals(true, evidence.nsfw)
        assertEquals(false, evidence.spoiler)
        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/comments/source123", request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("limit"))
        assertEquals("Bearer token-o", request.header("Authorization"))
    }

    @Test
    fun aSourceRedditWillNotCrosspostOrHasRemovedIsUnavailable() {
        assertEquals(CrosspostSourceLoadResult.Unavailable, source(sourceListing(crosspostable = false)))
        assertEquals(CrosspostSourceLoadResult.Unavailable, source(sourceListing(removed = "moderator")))
    }

    @Test
    fun withoutTheReadGrantNothingIsAsked() {
        val result = source(sourceListing(), scopes = "identity")

        assertFalse(result is CrosspostSourceLoadResult.Available)
        assertTrue(requests.isEmpty())
    }

    private object OfflineCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = error("no network in this test")
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
