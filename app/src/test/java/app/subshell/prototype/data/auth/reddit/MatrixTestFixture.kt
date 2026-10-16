package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.chat.RedditChatWebTokenResult
import app.subshell.prototype.chat.RedditChatWebTokenSource
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * STEP3 Matrix test setup: a signed-in account with the live inbox scopes, a
 * fixed Reddit web token (RedditChatWebTokenSource seam), a Matrix session
 * cache that can be warmed, and an OkHttp client whose interceptor records
 * every request and answers from [respond]. Nothing reaches the network.
 */
internal class MatrixTestFixture(
    var respond: (RecordedMatrixRequest) -> MatrixAnswer = { MatrixAnswer.Json(200, "{}") },
) {
    val providerAccountId = "me"
    val account = "reddit:redreader_compatibility:$providerAccountId"
    val ownMatrixUserId = "@t2_me:reddit.com"
    val participant = "peer"
    val peerMatrixUserId = "@t2_peer:reddit.com"
    val roomId = "!room:reddit.com"
    val webToken = "web-token"

    val requests = CopyOnWriteArrayList<RecordedMatrixRequest>()
    val invalidatedTokens = CopyOnWriteArrayList<String?>()
    private val active = AtomicInteger(0)
    val maxConcurrentRequests = AtomicInteger(0)

    val sessionCache = RedditChatMatrixSessionCache()

    val capability: AuthInboxReadCapability = object : AuthInboxReadCapability {
        private val session = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet(
                accessToken = "oauth-token",
                refreshToken = null,
                tokenType = "bearer",
                scope = RedditOAuth.liveInboxReadScopes.joinToString(" "),
                expiresAtEpochMs = Long.MAX_VALUE,
            ),
            identity = AuthAccountIdentity(providerAccountId, "self"),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )

        override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
        override suspend fun hasScopeGrant(accountStableId: String): Boolean = true
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(session)
    }

    val tokenSource: RedditChatWebTokenSource = object : RedditChatWebTokenSource {
        override suspend fun token(): RedditChatWebTokenResult =
            RedditChatWebTokenResult.Success(webToken, expiresAtEpochMs = null)

        override fun invalidate(expectedToken: String?) {
            invalidatedTokens += expectedToken
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val recorded = RecordedMatrixRequest(
                method = request.method,
                path = request.url.encodedPath,
                query = request.url.queryParameterNames.associateWith { request.url.queryParameter(it) },
                body = request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() },
                authorization = request.header("Authorization"),
            )
            requests += recorded
            val now = active.incrementAndGet()
            maxConcurrentRequests.accumulateAndGet(now, ::maxOf)
            try {
                Thread.sleep(20)
                when (val answer = respond(recorded)) {
                    is MatrixAnswer.Json -> response(request, answer.code, answer.body)
                    MatrixAnswer.Transport -> throw IOException("lost")
                }
            } finally {
                active.decrementAndGet()
            }
        }
        .build()

    /** The identity and the two-party room a foreground reader would have cached. */
    fun warmCache() {
        sessionCache.rememberIdentity(webToken, providerAccountId, ownMatrixUserId)
        sessionCache.rememberRoom(webToken, providerAccountId, participant, roomId, tokenExpiresAtEpochMs = null)
    }

    fun cachedRoom(): String? = sessionCache.roomFor(webToken, providerAccountId, participant)

    private fun response(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}

internal data class RecordedMatrixRequest(
    val method: String,
    val path: String,
    val query: Map<String, String?>,
    val body: String?,
    val authorization: String?,
)

internal sealed interface MatrixAnswer {
    data class Json(val code: Int, val body: String) : MatrixAnswer
    data object Transport : MatrixAnswer
}
