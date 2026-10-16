package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.auth.ModerationLogCoordinator
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (moderation02a4): the mod log is read for the account it is opened
 * for, with that account's token, by GET only; Reddit's 401 or a missing
 * grant asks to authorize (nothing is sent without a grant); a server error
 * may be retried; and the authorization it starts is a fresh sign-in for the
 * same account, never for an account that is no longer active.
 */
class Step3ModerationLogTest {
    private val asked = mutableListOf<String>()
    private val requests = mutableListOf<Request>()
    private var answer: Pair<Int, String> = 200 to """{"kind":"Listing","data":{"after":null,"children":[]}}"""

    private inner class Capability(private val result: () -> AuthSessionResult) : AuthCommentWriteCapability {
        val begun = mutableListOf<String>()
        override suspend fun beginAuthorization(accountStableId: String): String {
            begun += accountStableId
            return "https://example.test/authorize"
        }
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            asked += accountStableId
            return result()
        }
    }

    private fun session(token: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, "refresh", "bearer", "identity read modlog", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2a", username = "anna"),
    )

    private val client = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            requests += chain.request()
            val (code, body) = answer
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        },
    ).build()

    private fun loader(capability: AuthCommentWriteCapability) = RedditModerationLogLoader(
        capability = capability,
        callFactory = client,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun theLogIsReadForItsAccountWithItsTokenByGetOnly() = runBlocking {
        val result = loader(Capability { AuthSessionResult.Authenticated(session("token-a")) }).load("reddit:a", "subshell", null)

        assertTrue("$result", result is AuthenticatedRedditModerationLogResult.Loaded)
        assertEquals(listOf("reddit:a"), asked)
        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/r/subshell/about/log", request.url.encodedPath)
        assertEquals("Bearer token-a", request.header("Authorization"))
    }

    @Test
    fun a401OrAMissingGrantAsksToAuthorize() = runBlocking {
        answer = 401 to "{}"
        assertEquals(
            AuthenticatedRedditModerationLogResult.AuthorizationRequired,
            loader(Capability { AuthSessionResult.Authenticated(session("token-a")) }).load("reddit:a", "subshell", null),
        )
        requests.clear()

        val denied = loader(Capability { AuthSessionResult.AuthorizationDenied("missing_incremental_scope") }).load("reddit:a", "subshell", null)
        assertEquals(AuthenticatedRedditModerationLogResult.AuthorizationRequired, denied)
        assertEquals(emptyList<Request>(), requests)
    }

    @Test
    fun aServerErrorMayBeRetried() = runBlocking {
        answer = 503 to "{}"

        val failed = loader(Capability { AuthSessionResult.Authenticated(session("token-a")) }).load("reddit:a", "subshell", null)
            as AuthenticatedRedditModerationLogResult.Failed

        assertEquals(AuthenticatedRedditModerationFailureKind.HTTP, failed.kind)
        assertTrue(failed.retryable)
    }

    @Test
    fun theAuthorizationIsAFreshSignInForTheSameActiveAccountOnly() = runBlocking {
        var active = "reddit:a"
        val capability = Capability { AuthSessionResult.Authenticated(session("token-a")) }
        val coordinator = ModerationLogCoordinator(
            capability = capability,
            loader = loader(capability),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { active },
        )
        coordinator.open("reddit:a", "subshell")

        val ready = coordinator.beginAuthorization() as AuthBrowserLaunchResult.Ready
        assertTrue(ready.freshAccountSession)
        assertEquals(listOf("reddit:a"), capability.begun)

        active = "reddit:b"
        assertEquals(AuthBrowserLaunchResult.NotStarted, coordinator.beginAuthorization())
        assertEquals(listOf("reddit:a"), capability.begun)
    }
}
