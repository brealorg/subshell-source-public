package io.github.brealorg.subshell.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityDiscoveryFailureKind
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditPeopleSearchResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditPeopleSearchTarget
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import io.github.brealorg.subshell.data.auth.OAuthAccountIdentityClient
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.OAuthProvider
import io.github.brealorg.subshell.data.auth.OAuthProviderConfiguration
import io.github.brealorg.subshell.data.auth.OAuthTokenEndpointClient
import io.github.brealorg.subshell.data.auth.OAuthTokenSet
import io.github.brealorg.subshell.data.auth.StoredAuthAccountRecord
import io.github.brealorg.subshell.data.auth.StoredAuthAccountRegistry
import io.github.brealorg.subshell.data.auth.StoredOAuthSession
import io.github.brealorg.subshell.data.auth.authenticatedAccountId
import io.github.brealorg.subshell.data.cache.MutableCacheClock
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.cache.SubshellCacheDatabase
import io.github.brealorg.subshell.data.network.NetworkEndpoint
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
