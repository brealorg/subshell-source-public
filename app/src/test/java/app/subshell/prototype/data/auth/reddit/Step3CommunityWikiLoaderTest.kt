package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizer
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiTarget
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
 * STEP3 (wiki01a1, wiki01a2): a community's wiki is read only for the active
 * signed-in account, with its own token, once it has granted wiki reading
 * (otherwise it asks to authorize and sends nothing); the configuration may
 * carry more scopes than the wiki needs; pages and the page list are read
 * as raw Markdown from Reddit's wiki answers; a wiki Reddit refuses or does
 * not have is "unavailable".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CommunityWikiLoaderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun configuration(scopes: Set<String> = RedditOAuth.liveCommunityWikiReadScopes + "history") = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = scopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private val a = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "a")
    private val b = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "b")
    private val requests = mutableListOf<Request>()
    private var answer: Pair<Int, String> = 200 to "{}"

    private val recording = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        val (code, body) = answer
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("answer")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun session(token: String, providerId: String, configuration: OAuthProviderConfiguration) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, "refresh-$token", "bearer", RedditOAuth.liveCommunityWikiReadScopes.joinToString(" "), Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerId, "user-$providerId"),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    private fun record(id: String, providerId: String) = StoredAuthAccountRecord(
        accountId = id,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerId,
        username = "user-$providerId",
        createdAtEpochMs = 1L,
        lastUsedAtEpochMs = 1L,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private class Authorizer(private val answer: () -> AuthSessionResult) : AuthCapabilityAuthorizer {
        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner) = "https://example.test/authorize"
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = false
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner) = false
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) = Unit
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner): AuthCapabilityAuthorizationResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = answer()
    }

    private fun loader(
        configuration: OAuthProviderConfiguration = configuration(),
        authorization: ((StoredOAuthSession) -> AuthSessionResult)? = null,
    ): RedditCommunityWikiLoader {
        val store = EncryptedAuthSessionStore(application, cipher = PlainCipher)
        store.clearAll()
        val sessionB = session("token-b", "b", configuration)
        store.saveSession(a, session("token-a", "a", configuration))
        store.saveSession(b, sessionB)
        store.saveAccountRegistry(StoredAuthAccountRegistry(accounts = listOf(record(a, "a"), record(b, "b")), activeAccountId = b))
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = OfflineCallFactory, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { coordinator.hydrateDirectory() }
        return RedditCommunityWikiLoader(
            capability = Authorizer { authorization?.invoke(sessionB) ?: AuthSessionResult.Authenticated(sessionB) },
            callFactory = recording,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private val faq = AuthenticatedRedditCommunityWikiTarget.page("norge", "faq")

    private fun AuthenticatedRedditCommunityWikiResult.kind() = (this as? AuthenticatedRedditCommunityWikiResult.Failed)?.kind

    @Test
    fun aPageIsReadAsRawMarkdownWithTheActiveAccountsToken() = runBlocking {
        answer = 200 to """{"kind":"wikipage","data":{"content_md":"# FAQ\n\nAsk here.","revision_id":"r1"}}"""

        val result = loader().load(b, faq)

        val page = (result as AuthenticatedRedditCommunityWikiResult.PageLoaded).page
        assertEquals("# FAQ\n\nAsk here.", page.markdown)
        assertEquals("r1", page.revisionId)
        val request = requests.single()
        assertEquals("/r/norge/wiki/faq", request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("raw_json"))
        assertEquals("Bearer token-b", request.header("Authorization"))
    }

    @Test
    fun thePageListIsRead() = runBlocking {
        answer = 200 to """{"kind":"wikipagelisting","data":["index","config/sidebar"]}"""

        val result = loader().load(b, AuthenticatedRedditCommunityWikiTarget.index("norge"))

        val paths = (result as AuthenticatedRedditCommunityWikiResult.IndexLoaded).index.pages.map { it.path }
        assertTrue(paths.toString(), paths.containsAll(listOf("index", "config/sidebar")))
        assertEquals("/r/norge/wiki/pages", requests.single().url.encodedPath)
    }

    @Test
    fun anAccountThatIsNotActiveSendsNothing() = runBlocking {
        val result = loader().load(a, faq)

        assertEquals(AuthenticatedRedditCommunityWikiFailureKind.CLIENT_CONFIGURATION, result.kind())
        assertEquals(emptyList<Request>(), requests)
    }

    @Test
    fun aConfigurationWithoutWikiReadingSendsNothing() = runBlocking {
        val result = loader(configuration(scopes = RedditOAuth.coreReadScopes)).load(b, faq)

        assertEquals(AuthenticatedRedditCommunityWikiFailureKind.CLIENT_CONFIGURATION, result.kind())
        assertEquals(emptyList<Request>(), requests)
    }

    @Test
    fun aMissingWikiGrantAsksToAuthorizeAndSendsNothing() = runBlocking {
        val result = loader(authorization = { AuthSessionResult.AuthorizationDenied("missing_incremental_scope") }).load(b, faq)

        assertEquals(AuthenticatedRedditCommunityWikiResult.AuthorizationRequired, result)
        assertEquals(emptyList<Request>(), requests)
    }

    @Test
    fun aWikiRedditRefusesOrDoesNotHaveIsUnavailable() = runBlocking {
        answer = 403 to """{"reason":"WIKI_DISABLED"}"""
        assertEquals(AuthenticatedRedditCommunityWikiFailureKind.UNAVAILABLE, loader().load(b, faq).kind())
        answer = 404 to "{}"
        assertEquals(AuthenticatedRedditCommunityWikiFailureKind.UNAVAILABLE, loader().load(b, faq).kind())
        answer = 500 to "{}"
        assertEquals(AuthenticatedRedditCommunityWikiFailureKind.HTTP, loader().load(b, faq).kind())
    }

    private object OfflineCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = error("no network in this test")
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
