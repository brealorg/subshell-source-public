package io.github.brealorg.subshell.data.auth.reddit

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.auth.AuthAccountIdentity
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
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
import io.github.brealorg.subshell.data.cache.RoomSubshellCacheStore
import io.github.brealorg.subshell.data.cache.SubshellCacheDatabase
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW5: a cache fault threw away community results Reddit had returned
 * (OCB-495); Direct Chat room discovery downloaded every room's full state with
 * no size bound (OCB-482); a limited (gapped) live sync dropped the messages
 * between the old cursor and the newest 50 (OCB-611).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low5ChatAndDiscoveryTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun communityResultsAreShownWhenStoringThemFails() = runBlocking<Unit> {
        val configuration = OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
            tokenEndpoint = "https://example.test/token".toHttpUrl(),
            revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
            clientId = "client-id-123",
            redirectUri = "subshell-auth-test://oauth/callback",
            scopes = RedditOAuth.coreReadScopes,
            permanentAccess = true,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )
        val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
        val store = EncryptedAuthSessionStore(application, cipher = PlainCipher).also { it.clearAll() }
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = accountId,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(
            accountId,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), Long.MAX_VALUE),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val tokenClient = OAuthTokenEndpointClient(callFactory = OkHttpClient(), configuration = configuration, userAgent = "subshell-test/1.0")
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        ).also { it.hydratedSnapshot() }
        // A cache that fails every write.
        val cache = RoomSubshellCacheStore(
            Room.inMemoryDatabaseBuilder(application as Context, SubshellCacheDatabase::class.java)
                .allowMainThreadQueries()
                .build(),
        ).also { it.close() }
        val loader = RedditCommunityDiscoveryLoader(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(
                        """{"kind":"Listing","data":{"after":null,"children":[
                          {"kind":"t5","data":{"name":"t5_a","display_name":"norge"}}]}}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cache,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = loader.synchronize(accountId, AuthenticatedRedditCommunityDiscoveryTarget.Popular)

        assertTrue("$result", result is AuthenticatedRedditCommunityDiscoveryResult.Loaded)
        result as AuthenticatedRedditCommunityDiscoveryResult.Loaded
        assertEquals(listOf("norge"), result.items.map { it.community.name })
        assertFalse(result.fromCache)
    }

    @Test
    fun roomDiscoveryAsksOnlyForMembershipState() {
        val filter = Json.parseToJsonElement(REDDIT_CHAT_MATRIX_ROOM_DISCOVERY_FILTER).jsonObject
        val room = filter.getValue("room").jsonObject

        assertEquals("0", room.getValue("timeline").jsonObject.getValue("limit").jsonPrimitive.content)
        assertEquals(listOf("m.room.member"), room.getValue("state").jsonObject.getValue("types").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(room.getValue("ephemeral").jsonObject.getValue("types").jsonArray.isEmpty())
        assertTrue(filter.getValue("presence").jsonObject.getValue("types").jsonArray.isEmpty())
    }

    @Test
    fun aMatrixAnswerOverTheCapIsNotRead() {
        val big = "x".repeat(64).toResponseBody("application/json".toMediaType())
        val small = "{}".toResponseBody("application/json".toMediaType())

        assertEquals("", big.redditChatMatrixTextWithinCap(maxBytes = 32))
        assertEquals("{}", small.redditChatMatrixTextWithinCap(maxBytes = 32))
    }

    @Test
    fun aLimitedSyncCarriesWhereItsGapEnds() {
        val projection = redditChatMatrixLiveSyncProjection(
            body = """{"next_batch":"s2","rooms":{"join":{"!r":{"timeline":
                {"limited":true,"prev_batch":"p1","events":[]}}}}}""",
            roomId = "!r",
            ownUserId = "@me:reddit.com",
            ownUsername = "me",
            participantUsername = "peer",
        )!!

        assertTrue(projection.limited)
        assertEquals("p1", projection.prevBatch)
    }

    @Test
    fun theGapIsReadBackPageByPageWithinTheBound() = runBlocking<Unit> {
        val asked = mutableListOf<String>()
        val pages = mapOf(
            "p1" to """{"chunk":[{"event_id":"${'$'}c"},{"event_id":"${'$'}b"}],"end":"p2"}""",
            "p2" to """{"chunk":[{"event_id":"${'$'}a"}],"end":"p3"}""",
            "p3" to """{"chunk":[],"end":null}""",
        )

        val events = redditChatMatrixBackfillGap(from = "p1", pages = 3) { from -> asked += from; pages[from] }

        assertEquals(listOf("p1", "p2", "p3"), asked)
        assertEquals(listOf("\$c", "\$b", "\$a"), events.map { (it as JsonObject).getValue("event_id").jsonPrimitive.content })
        // Bounded, and a failed page keeps what was read.
        val bounded = redditChatMatrixBackfillGap(from = "p1", pages = 1) { pages[it] }
        assertEquals(2, bounded.size)
        val failed = redditChatMatrixBackfillGap(from = "p1", pages = 3) { if (it == "p1") pages[it] else null }
        assertEquals(2, failed.size)
        assertTrue(redditChatMatrixBackfillGap(from = null, pages = 3) { error("not asked") }.isEmpty())
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
