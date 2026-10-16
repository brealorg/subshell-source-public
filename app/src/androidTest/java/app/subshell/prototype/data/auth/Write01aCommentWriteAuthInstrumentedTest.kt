package app.subshell.prototype.data.auth

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.account.localFixtureAccountId
import app.subshell.prototype.data.auth.reddit.RedditAccountIdentityClient
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Write01aCommentWriteAuthInstrumentedTest {
    @Test
    fun explicitCommentGrantUpgradesPinnedAccountWithoutTouchingTargetAppData() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = requireNotNull(instrumentation.targetContext) {
                "WRITE01-A1 target context is unavailable"
            }
            val fixtureContext = targetContext.createPackageContext(
                instrumentation.componentName.packageName,
                0,
            )
            val isolatedContext = Write01aIsolatedStorageContext(
                base = targetContext,
                suffix = "positive",
            )
            val clock = Write01aClock(1_000_000L)
            val store = EncryptedAuthSessionStore(
                context = isolatedContext,
                cipher = AndroidKeystoreAesGcmCipher(WRITE01A_KEY_ALIAS),
            )
            store.clearAll(deleteKey = true)

            val server = LocalJsonHttpsServer(fixtureContext).also(LocalJsonHttpsServer::start)
            val readConfiguration = configuration(server.port, RedditOAuth.coreReadScopes)
            val writeConfiguration = configuration(server.port, RedditOAuth.liveCommentWriteScopes)
            val accountId = authenticatedAccountId(
                provider = OAuthProvider.REDDIT,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                providerAccountId = "t2_write01a",
            )
            val identity = AuthAccountIdentity("t2_write01a", "write01a_user")
            store.saveSession(
                accountId,
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = token("read-access", "read-refresh", "identity read"),
                    identity = identity,
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                    clientIdFingerprint = readConfiguration.clientIdFingerprint,
                ),
            )
            store.saveAccountRegistry(
                StoredAuthAccountRegistry(
                    accounts = listOf(
                        StoredAuthAccountRecord(
                            accountId = accountId,
                            provider = OAuthProvider.REDDIT,
                            providerAccountId = identity.providerAccountId,
                            username = identity.username,
                            createdAtEpochMs = clock.now,
                            lastUsedAtEpochMs = clock.now,
                            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                        ),
                    ),
                    activeAccountId = accountId,
                ),
            )

            val readCoordinator = AuthAccountSessionCoordinator(
                configuration = readConfiguration,
                store = store,
                tokenClient = tokenClient(readConfiguration, clock),
                identityClient = OAuthAccountIdentityClient {
                    error("Read coordinator identity must not run")
                },
                localFixtureUsernames = listOf("root"),
                clock = clock,
            )
            // The account directory is read from the store on hydration; until
            // then the coordinator has no active account.
            readCoordinator.hydratedSnapshot()
            // DEAD06 (OCB-473): comment write authorization runs through the
            // owner-bound capability the runtime wires; the Configured class is
            // now only a token source.
            val capability = OwnerBoundAuthCommentWriteCapability(
                authorizer = ConfiguredAuthCapabilityAuthorizer(
                    configuration = writeConfiguration,
                    store = store,
                    tokenClient = tokenClient(writeConfiguration, clock),
                    identityClient = RedditAccountIdentityClient(
                        callFactory = SubshellOkHttpClientFactory.create(),
                        endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                        userAgent = WRITE01A_USER_AGENT,
                    ),
                    accountCoordinator = readCoordinator,
                    clock = clock,
                    stateGenerator = OAuthStateGenerator {
                        "state-write01a-comment-upgrade-abcdefghijklmnopqrstuvwxyz"
                    },
                ),
                owner = AuthCapabilityAuthorizationOwner.COMMENT_WRITE,
            )

            val authorizationUrl = capability.beginAuthorization(accountId).toHttpUrl()
            assertEquals("identity mysubreddits read submit", authorizationUrl.queryParameter("scope"))
            val pending = requireNotNull(store.pendingAuthorization())
            assertEquals(accountId, pending.accountStableId)
            assertEquals(writeConfiguration.scopeFingerprint, pending.scopeFingerprint)
            assertFalse(pending.scopeFingerprint == readConfiguration.scopeFingerprint)

            server.enqueue(json(TOKEN_JSON))
            server.enqueue(json(IDENTITY_JSON))
            val state = requireNotNull(authorizationUrl.queryParameter("state"))
            val callback = "${WRITE01A_REDIRECT_URI}?code=${encode("upgrade-code")}&state=${encode(state)}"
                .toUri()
            assertTrue(OAuthCallbackProcessor(store, clock).process(callback) is AuthCallbackResult.Accepted)

            val upgraded = capability.completePendingAuthorization()
            assertTrue(upgraded is AuthCommentWriteCapabilityResult.Upgraded)
            upgraded as AuthCommentWriteCapabilityResult.Upgraded
            assertEquals(accountId, upgraded.accountStableId)
            assertEquals(identity.username, upgraded.username)
            assertNull(store.session())
            val bound = requireNotNull(store.session(accountId))
            assertTrue(bound.tokens.grants(RedditOAuth.liveCommentWriteScopes))
            assertEquals(identity, bound.identity)

            readCoordinator.selectAccount(localFixtureAccountId("root"))
            val pinned = capability.ensureAuthorized(accountId)
            assertTrue(pinned is AuthSessionResult.Authenticated)
            assertEquals("write-access", (pinned as AuthSessionResult.Authenticated).session.tokens.accessToken)
            assertNull(readCoordinator.bearerToken())

            assertTrue(server.awaitRequestCount(expected = 2, timeoutMs = 5_000L))
            val requests = server.requestsSnapshot()
            assertTrue(requests[0].requestLine.startsWith("POST /api/v1/access_token"))
            assertTrue(requests[1].requestLine.startsWith("GET /api/v1/me"))
            assertEquals("Bearer write-access", requests[1].headers["authorization"])

            val encrypted = store.encryptedBytesForTest().toString(Charsets.ISO_8859_1)
            listOf("write-access", "write-refresh", "upgrade-code", identity.username).forEach { secret ->
                assertFalse(encrypted.contains(secret))
            }
            store.clearAll(deleteKey = true)
            assertTrue(store.encryptedBytesForTest().isEmpty())
            isolatedContext.clear()
            assertFalse(isolatedContext.root.exists())

            server.close()
            server.assertHealthy()
            Log.i(
                WRITE01A_LOG_TAG,
                "WRITE01A1_SCOPE=identity_read_submit DEFAULT_READ_ONLY_PRESERVED=true " +
                    "SCOPE_BOUND_CALLBACK=true ACCOUNT_PINNED=true UPGRADE_ATOMIC=true " +
                    "TARGET_APP_AUTH_VAULT_TOUCHED=false TEST_CACHE_STORAGE_CLEANED=true " +
                    "LIVE_REDDIT_NETWORK=false",
            )
            Log.i(WRITE01A_LOG_TAG, "RESULT=SUBSHELL_WRITE01A1_COMMENT_WRITE_AUTH_RUNTIME_PASS")
        }
    }

    @Test
    fun writeCallbackCannotBeConsumedByReadOnlyCapabilityOrAnotherAccount() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = requireNotNull(instrumentation.targetContext) {
                "WRITE01-A1 target context is unavailable"
            }
            val isolatedContext = Write01aIsolatedStorageContext(
                base = targetContext,
                suffix = "negative",
            )
            val clock = Write01aClock(2_000_000L)
            val store = EncryptedAuthSessionStore(
                context = isolatedContext,
                cipher = AndroidKeystoreAesGcmCipher(WRITE01A_NEGATIVE_KEY_ALIAS),
            )
            store.clearAll(deleteKey = true)

            val readConfiguration = offlineConfiguration(RedditOAuth.coreReadScopes)
            val writeConfiguration = offlineConfiguration(RedditOAuth.liveCommentWriteScopes)
            val pinnedAccountId = "reddit:redreader_compatibility:t2_scope_bound"
            val writeManager = OAuthSessionManager(
                configuration = writeConfiguration,
                store = store,
                tokenClient = tokenClient(writeConfiguration, clock),
                clock = clock,
                stateGenerator = OAuthStateGenerator {
                    "state-write01a-negative-scope-binding-abcdefghijklmnopqrstuvwxyz"
                },
                authorizationAccountStableId = pinnedAccountId,
            )
            val authorizationUrl = writeManager.beginAuthorization().toHttpUrl()
            val state = requireNotNull(authorizationUrl.queryParameter("state"))
            val callback = "$WRITE01A_REDIRECT_URI?code=${encode("must-not-exchange")}&state=${encode(state)}"
                .toUri()
            assertTrue(OAuthCallbackProcessor(store, clock).process(callback) is AuthCallbackResult.Accepted)

            val wrongCapability = OAuthSessionManager(
                configuration = readConfiguration,
                store = store,
                tokenClient = tokenClient(readConfiguration, clock),
                clock = clock,
                authorizationAccountStableId = pinnedAccountId,
            ).completePendingCallback()
            assertTrue(wrongCapability is AuthSessionResult.ClientConfigurationMismatch)
            assertNull(store.session())

            store.clearAll(deleteKey = true)
            val secondAuthorization = writeManager.beginAuthorization().toHttpUrl()
            val secondState = requireNotNull(secondAuthorization.queryParameter("state"))
            val secondCallback = "$WRITE01A_REDIRECT_URI?code=${encode("wrong-account")}&state=${encode(secondState)}"
                .toUri()
            assertTrue(
                OAuthCallbackProcessor(store, clock).process(secondCallback) is AuthCallbackResult.Accepted,
            )
            val wrongAccount = OAuthSessionManager(
                configuration = writeConfiguration,
                store = store,
                tokenClient = tokenClient(writeConfiguration, clock),
                clock = clock,
                authorizationAccountStableId = "reddit:redreader_compatibility:t2_other",
            ).completePendingCallback()
            assertTrue(wrongAccount is AuthSessionResult.ClientConfigurationMismatch)
            assertNull(store.session())
            store.clearAll(deleteKey = true)
            isolatedContext.clear()
            assertFalse(isolatedContext.root.exists())
        }
    }

    private fun offlineConfiguration(scopes: Set<String>): OAuthProviderConfiguration =
        OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint = "https://example.test/api/v1/authorize.compact".toHttpUrl(),
            tokenEndpoint = "https://example.test/api/v1/access_token".toHttpUrl(),
            revokeEndpoint = "https://example.test/api/v1/revoke_token".toHttpUrl(),
            clientId = "write01a-client",
            redirectUri = WRITE01A_REDIRECT_URI,
            scopes = scopes,
            permanentAccess = true,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )

    private fun configuration(port: Int, scopes: Set<String>): OAuthProviderConfiguration =
        OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint = "https://example.test/api/v1/authorize.compact".toHttpUrl(),
            tokenEndpoint = "https://127.0.0.1:$port/api/v1/access_token".toHttpUrl(),
            revokeEndpoint = "https://127.0.0.1:$port/api/v1/revoke_token".toHttpUrl(),
            clientId = "write01a-client",
            redirectUri = WRITE01A_REDIRECT_URI,
            scopes = scopes,
            permanentAccess = true,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )

    private fun tokenClient(
        configuration: OAuthProviderConfiguration,
        clock: AuthClock,
    ): OAuthTokenEndpointClient = OAuthTokenEndpointClient(
        callFactory = SubshellOkHttpClientFactory.create(),
        configuration = configuration,
        userAgent = WRITE01A_USER_AGENT,
        clock = clock,
    )

    private fun token(access: String, refresh: String, scope: String): OAuthTokenSet = OAuthTokenSet(
        accessToken = access,
        refreshToken = refresh,
        tokenType = "bearer",
        scope = scope,
        expiresAtEpochMs = 4_600_000L,
    )

    private fun json(body: String): FixtureResponse = FixtureResponse(
        status = "200 OK",
        body = body,
        headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

private class Write01aIsolatedStorageContext(
    base: Context,
    suffix: String,
) : ContextWrapper(base) {
    val root: File = File(
        base.cacheDir,
        "$WRITE01A_STORAGE_DIRECTORY_PREFIX-$suffix",
    ).apply {
        deleteRecursively()
        check(mkdirs() || isDirectory) {
            "Could not create WRITE01-A1 isolated test storage"
        }
    }

    override fun getApplicationContext(): Context = this

    override fun getNoBackupFilesDir(): File = root

    fun clear() {
        root.deleteRecursively()
    }
}

private class Write01aClock(@Volatile var now: Long) : AuthClock {
    override fun epochMillis(): Long = now
}

private const val WRITE01A_LOG_TAG = "subshell-write01a1"
private const val WRITE01A_STORAGE_DIRECTORY_PREFIX = "subshell-write01a1-auth-test"
private const val WRITE01A_KEY_ALIAS = "app.subshell.prototype.write01a1.runtime.v1"
private const val WRITE01A_NEGATIVE_KEY_ALIAS =
    "app.subshell.prototype.write01a1.runtime.negative.v1"
private const val WRITE01A_REDIRECT_URI = "subshell-write01a-test://oauth/callback"
private const val WRITE01A_USER_AGENT = "android:subshell-write01a:0.65.0 (localhost fixture)"
private const val TOKEN_JSON = """
{"access_token":"write-access","refresh_token":"write-refresh","token_type":"bearer","expires_in":3600,"scope":"identity mysubreddits read submit"}
"""
private const val IDENTITY_JSON = """{"id":"t2_write01a","name":"write01a_user"}"""
