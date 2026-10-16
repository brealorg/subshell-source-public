package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.*
import app.subshell.prototype.data.auth.*
import app.subshell.prototype.data.network.reddit.OnlineVisibilityNetwork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class UserStatus01A1ServiceTest {
    private val config = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "test-client", redirectUri = "test://callback",
        scopes = setOf("identity", "account"), permanentAccess = true,
    )
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, config.clientMode, "me")
    private fun stored(scopes: String = "identity account", id: String = "me") = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", scopes, Long.MAX_VALUE),
        identity = AuthAccountIdentity(id, "test-user"),
        clientMode = config.clientMode, clientIdFingerprint = config.clientIdFingerprint,
    )
    private fun confirmation() = OnlineVisibilityConfirmation(account, false, true, 1)
    private class FakeNetwork : OnlineVisibilityNetwork {
        val events = mutableListOf<String>()
        val replies = ArrayDeque<OnlineVisibilityResult>()
        var patchReply: OnlineVisibilityResult = OnlineVisibilityResult.Known(true)
        var afterRead: () -> Unit = {}
        var patchWait: CompletableDeferred<Unit>? = null
        override suspend fun read(): OnlineVisibilityResult {
            events += "GET"
            val result = replies.removeFirst()
            afterRead()
            return result
        }
        override suspend fun patch(value: Boolean): OnlineVisibilityResult {
            events += "PATCH:$value"
            patchWait?.await()
            return patchReply
        }
    }
    private fun service(
        network: FakeNetwork,
        active: () -> String? = { account },
        ensure: suspend (String, Set<String>) -> AuthSessionResult = { _, _ -> AuthSessionResult.Authenticated(stored()) },
    ) = ConfiguredRedditOnlineVisibilityService(config, active, ensure) { network }

    @Test fun narrowReadAndWriteScopesDoNotExpandDailyDriver() {
        assertEquals(setOf("identity"), ONLINE_VISIBILITY_READ_SCOPES)
        assertEquals(setOf("identity", "account"), ONLINE_VISIBILITY_WRITE_SCOPES)
        assertTrue(RedditOAuth.dailyDriverScopes.containsAll(ONLINE_VISIBILITY_WRITE_SCOPES))
    }

    @Test fun readAcceptsIdentityGrantAlone() = runBlocking {
        val n = FakeNetwork().apply { replies += OnlineVisibilityResult.Known(false) }
        val s = service(n, ensure = { id, scopes ->
            assertEquals(account, id); assertEquals(setOf("identity"), scopes)
            AuthSessionResult.Authenticated(stored("identity"))
        })
        assertEquals(OnlineVisibilityResult.Known(false), s.read(account) { true })
        assertEquals(listOf("GET"), n.events)
    }

    @Test fun wrongTokenIdentityNeverReachesNetwork() = runBlocking {
        val n = FakeNetwork()
        val s = service(n, ensure = { _, _ -> AuthSessionResult.Authenticated(stored(id = "other")) })
        assertTrue(s.read(account) { true } is OnlineVisibilityResult.Failed)
        assertTrue(n.events.isEmpty())
    }

    @Test fun missingGrantCannotWrite() = runBlocking {
        val n = FakeNetwork()
        val s = service(n, ensure = { _, _ -> AuthSessionResult.Authenticated(stored("identity")) })
        val result = s.change(confirmation()) { true } as OnlineVisibilityWriteResult.Failed
        assertEquals(OnlineVisibilityIssue.AUTHORIZATION, result.issue)
        assertTrue(n.events.isEmpty())
    }

    @Test fun accountChangeDuringTokenAcquisitionStopsBeforeNetwork() = runBlocking {
        var active = account
        val n = FakeNetwork()
        val s = service(n, active = { active }, ensure = { _, _ ->
            active = "other"; AuthSessionResult.Authenticated(stored())
        })
        assertEquals(OnlineVisibilityIssue.ACCOUNT_CHANGED,
            (s.change(confirmation()) { true } as OnlineVisibilityWriteResult.Failed).issue)
        assertTrue(n.events.isEmpty())
    }

    @Test fun exactGetPatchGetTransaction() = runBlocking {
        val n = FakeNetwork().apply {
            replies += OnlineVisibilityResult.Known(false)
            replies += OnlineVisibilityResult.Known(true)
        }
        assertEquals(OnlineVisibilityWriteResult.Verified(true, true), service(n).change(confirmation()) { true })
        assertEquals(listOf("GET", "PATCH:true", "GET"), n.events)
    }

    @Test fun falseIsSerializedAndVerifiedAsAbsoluteDesiredValue() = runBlocking {
        val n = FakeNetwork().apply {
            replies += OnlineVisibilityResult.Known(true)
            replies += OnlineVisibilityResult.Known(false)
            patchReply = OnlineVisibilityResult.Known(false)
        }
        assertEquals(OnlineVisibilityWriteResult.Verified(false, true),
            service(n).change(confirmation().copy(expectedValue = true, desiredValue = false)) { true })
        assertEquals(listOf("GET", "PATCH:false", "GET"), n.events)
    }

    @Test fun externalChangeSkipsPatch() = runBlocking {
        val n = FakeNetwork().apply { replies += OnlineVisibilityResult.Known(true) }
        assertEquals(OnlineVisibilityWriteResult.Changed(true), service(n).change(confirmation()) { true })
        assertEquals(listOf("GET"), n.events)
    }

    @Test fun unknownPreflightSkipsPatch() = runBlocking {
        val n = FakeNetwork().apply { replies += OnlineVisibilityResult.Unavailable }
        assertEquals(OnlineVisibilityWriteResult.Unavailable, service(n).change(confirmation()) { true })
        assertEquals(listOf("GET"), n.events)
    }

    @Test fun leavingScreenDuringPreflightSkipsPatch() = runBlocking {
        var current = true
        val n = FakeNetwork().apply {
            replies += OnlineVisibilityResult.Known(false)
            afterRead = { current = false }
        }
        assertEquals(OnlineVisibilityIssue.ACCOUNT_CHANGED,
            (service(n).change(confirmation()) { current } as OnlineVisibilityWriteResult.Failed).issue)
        assertEquals(listOf("GET"), n.events)
    }

    @Test fun server500WithChangedReadbackProvesCurrentStateWithoutRetry() = runBlocking {
        val n = FakeNetwork().apply {
            replies += OnlineVisibilityResult.Known(false)
            replies += OnlineVisibilityResult.Known(true)
            patchReply = OnlineVisibilityResult.Failed(OnlineVisibilityIssue.HTTP, 500)
        }
        assertEquals(OnlineVisibilityWriteResult.Verified(true, false), service(n).change(confirmation()) { true })
        assertEquals(listOf("GET", "PATCH:true", "GET"), n.events)
    }

    @Test fun httpSuccessWithoutReadbackDoesNotProveSaving() = runBlocking {
        val n = FakeNetwork().apply {
            replies += OnlineVisibilityResult.Known(false)
            replies += OnlineVisibilityResult.Failed(OnlineVisibilityIssue.TRANSPORT)
        }
        assertEquals(OnlineVisibilityIssue.OUTCOME_UNKNOWN,
            (service(n).change(confirmation()) { true } as OnlineVisibilityWriteResult.Failed).issue)
        assertEquals(1, n.events.count { it.startsWith("PATCH") })
    }

    @Test fun ignoredPatchDoesNotPretendSuccessOrRetry() = runBlocking {
        val n = FakeNetwork().apply {
            replies += OnlineVisibilityResult.Known(false)
            replies += OnlineVisibilityResult.Known(false)
            patchReply = OnlineVisibilityResult.Known(false)
        }
        assertEquals(OnlineVisibilityIssue.OUTCOME_UNKNOWN,
            (service(n).change(confirmation()) { true } as OnlineVisibilityWriteResult.Failed).issue)
        assertEquals(1, n.events.count { it.startsWith("PATCH") })
    }

    @Test fun duplicateConcurrentWritesAreNotQueued() = runBlocking {
        val n = FakeNetwork().apply {
            replies += OnlineVisibilityResult.Known(false)
            replies += OnlineVisibilityResult.Known(true)
            patchWait = CompletableDeferred()
        }
        val s = service(n)
        val first = async { s.change(confirmation()) { true } }
        yield()
        assertTrue(s.change(confirmation()) { true } is OnlineVisibilityWriteResult.Failed)
        n.patchWait!!.complete(Unit)
        assertTrue(first.await() is OnlineVisibilityWriteResult.Verified)
        assertEquals(1, n.events.count { it.startsWith("PATCH") })
    }
}
