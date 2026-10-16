package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchResult
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
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
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (search01c2): a people search goes out only for the active signed-in
 * account, with that account's own token and read grant — otherwise nothing
 * is sent — and stores nothing in the cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3PeopleSearchLoaderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cacheStore = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
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
    private val a = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "a")
    private val b = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "b")
    private val authorizations = mutableListOf<String?>()
    private val people = """{"data":{"children":[{"kind":"t2","data":{"name":"alice","id":"abc"}}]}}"""

    private val recording = OkHttpClient.Builder().addInterceptor { chain ->
        authorizations += chain.request().header("Authorization")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(people.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    @After
    fun close() {
        database.close()
    }

    private fun record(id: String, providerId: String) = StoredAuthAccountRecord(
        accountId = id,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerId,
        username = "user-$providerId",
        createdAtEpochMs = 1L,
        lastUsedAtEpochMs = 1L,
        clientMode = configuration.clientMode,
    )

    private fun session(token: String, providerId: String, scopes: Set<String>) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, "refresh-$token", "bearer", scopes.joinToString(" "), Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerId, "user-$providerId"),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    private fun loader(bScopes: Set<String> = RedditOAuth.coreReadScopes): RedditCommunityDiscoveryLoader {
        val store = EncryptedAuthSessionStore(application, cipher = PlainCipher)
        store.clearAll()
        store.saveSession(a, session("token-a", "a", RedditOAuth.coreReadScopes))
        store.saveSession(b, session("token-b", "b", bScopes))
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
        return RedditCommunityDiscoveryLoader(
            callFactory = recording,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cacheStore,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private val alice = AuthenticatedRedditPeopleSearchTarget.search("alice")

    private fun cachedRows(): Int {
        val db = database.openHelper.readableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table')").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return tables.sumOf { table -> db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) } }
    }

    @Test
    fun theActiveAccountSearchesWithItsOwnTokenAndStoresNothing() = runBlocking {
        val before = cachedRows()

        loader().searchPeople(b, alice)

        assertTrue(authorizations.isNotEmpty())
        assertEquals(setOf<String?>("Bearer token-b"), authorizations.toSet())
        assertEquals(before, cachedRows())
    }

    @Test
    fun anAccountThatIsNotActiveSendsNothing() = runBlocking {
        val result = loader().searchPeople(a, alice)

        assertEquals(
            AuthenticatedRedditCommunityDiscoveryFailureKind.CLIENT_CONFIGURATION,
            (result as AuthenticatedRedditPeopleSearchResult.Failed).kind,
        )
        assertEquals(emptyList<String?>(), authorizations)
    }

    @Test
    fun aSessionWithoutTheReadGrantSendsNothing() = runBlocking {
        val result = loader(bScopes = setOf("identity")).searchPeople(b, alice)

        assertTrue("$result", result is AuthenticatedRedditPeopleSearchResult.Failed)
        assertEquals(emptyList<String?>(), authorizations)
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
