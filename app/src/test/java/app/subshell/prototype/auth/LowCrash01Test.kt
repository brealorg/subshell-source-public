package app.subshell.prototype.auth

import android.content.SharedPreferences
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.decodeDirectChatReactionPresentationSnapshot
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerCommunitySearchLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.error.redditWriteHttpFailureDetails
import app.subshell.prototype.data.liveSearchPostFeedQueryKey
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditSubmittedTextPost
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.ui.ComposerCommunitySearchStatus
import app.subshell.prototype.ui.ComposerPolicyStatus
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOWCRASH01: low-severity audit findings where a bad value threw out of a
 * coroutine or composition instead of failing softly (OCB-462, 448, 584, 594,
 * 603, 627).
 */
class LowCrash01Test {
    private val accountId = "reddit:redreader_compatibility:t2_writer"

    @Test
    fun aLongEscapedSearchQueryGivesAValidKeyAndShortKeysAreUnchanged() {
        val long = liveSearchPostFeedQueryKey(accountId, "|".repeat(200))
        assertTrue(long.value, long.value.endsWith("|all|sha256:" + long.value.substringAfterLast("sha256:")))
        assertEquals(
            "account|authenticated|$accountId|live05|search|all|cats %7c dogs",
            liveSearchPostFeedQueryKey(accountId, "Cats | Dogs").value,
        )
    }

    @Test
    fun aReactionCacheEntryWithAnObjectFieldIsUnreadableNotAThrow() {
        assertNull(
            decodeDirectChatReactionPresentationSnapshot(
                raw = """{"version":{"v":2}}""",
                nowEpochMs = 1_000L,
                json = Json,
            ),
        )
    }

    @Test
    fun untrustedStatusAndRateLimitHeadersBuildFailureDetails() = runBlocking {
        val client = SubshellHttpClient(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(999).message("odd")
                    .header("X-Ratelimit-Reset", "-1")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test",
        )
        val result = client.getJson(JsonGetRequest(pathSegments = listOf("api", "v1", "me")), JsonElement.serializer())
        val failure = result as NetworkResult.HttpFailure
        assertEquals(0L, failure.metadata.rateLimit.resetAfterMs)

        val details = redditWriteHttpFailureDetails(
            statusCode = failure.statusCode,
            retryable = false,
            metadata = failure.metadata.copy(
                rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = -1_000L),
            ),
        )
        assertNull(details.httpStatusCode)
        // LOW_ERROR_RATE_WINDOW_ONLY_FOR_429_V1 (OCB-532): the rate window is a
        // retry-after only for a 429; there, a negative header is clamped.
        assertNull(details.retryAfterMs)
        val limited = redditWriteHttpFailureDetails(
            statusCode = 429,
            retryable = true,
            metadata = failure.metadata.copy(
                rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = -1_000L),
            ),
        )
        assertEquals(0L, limited.retryAfterMs)
    }

    @Test
    fun aFailedAttemptStoreCommitSendsNothingAndDoesNotThrow() = runBlocking {
        val prefs = CommitPreferences(failCommits = true)
        val submitter = CountingSubmitter()
        val coordinator = TextPostWriteCoordinator(
            null, submitter, OneAccount(), OAuthClientMode.REDREADER_COMPATIBILITY,
            TextPostSubmitAttemptStore(prefs.preferences),
        )

        coordinator.submit(request())

        assertEquals(0, submitter.writes)
        val state = coordinator.uiState.value
        assertEquals(TextPostWritePhase.FAILED, state.phase)
        assertEquals(LocalFailureKind.STORAGE, state.failureDetails?.localKind)
        assertTrue(state.retryable)
    }

    @Test
    fun aPostThatSucceededStaysSucceededWhenClearingTheAttemptFails() = runBlocking {
        val prefs = CommitPreferences(failCommits = false)
        val submitter = CountingSubmitter { prefs.failCommits = true }
        val coordinator = TextPostWriteCoordinator(
            null, submitter, OneAccount(), OAuthClientMode.REDREADER_COMPATIBILITY,
            TextPostSubmitAttemptStore(prefs.preferences),
        )

        coordinator.submit(request())

        assertEquals(1, submitter.writes)
        assertEquals(TextPostWritePhase.SUCCEEDED, coordinator.uiState.value.phase)
    }

    @Test
    fun throwingComposerLoadersReadAsUnavailable() {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined +
                CoroutineExceptionHandler { _, error -> errors += error },
        )
        val coordinator = ComposerPolicyCoordinator(
            composerPolicyLoader = AuthenticatedComposerPolicyLoader { _, _ -> error("loader fault") },
            composerCommunitySearchLoader = AuthenticatedComposerCommunitySearchLoader { _, _ -> error("search fault") },
            userFlairCoordinator = UserFlairCoordinator(manager = null, activeAccountStableId = { null }),
            accountSessionDirectory = { null },
            scope = scope,
        )

        coordinator.loadComposerPolicy(accountId, "pics")
        coordinator.searchComposerCommunities(accountId, "pic")

        assertEquals(emptyList<Throwable>(), errors)
        assertEquals(ComposerPolicyStatus.UNAVAILABLE, coordinator.composerPolicy.value.status)
        assertEquals(ComposerCommunitySearchStatus.UNAVAILABLE, coordinator.composerCommunitySearch.value.status)
    }

    private fun request() = TextPostWriteRequest(
        accountStableId = accountId,
        community = "subshell",
        title = "Title",
        text = "Body",
    )

    private inner class OneAccount : AccountSessionDirectory {
        private val summary = AccountSessionSummary(
            stableId = accountId,
            username = "writer",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        override fun snapshot() = AccountSessionDirectorySnapshot(listOf(summary), accountId)
        override fun selectAccount(stableId: String) = snapshot()
    }

    private class CountingSubmitter(private val onSubmit: () -> Unit = {}) : AuthenticatedTextPostSubmitter {
        var writes = 0

        override suspend fun submit(request: AuthenticatedTextPostSubmitRequest): AuthenticatedTextPostSubmitResult {
            writes++
            onSubmit()
            return AuthenticatedTextPostSubmitResult.Submitted(
                accountStableId = request.accountStableId,
                post = RedditSubmittedTextPost(
                    fullname = "t3_new", id = "new", community = request.community,
                    title = request.title, text = request.text,
                    permalink = "/r/${request.community}/comments/new/",
                ),
                metadata = NetworkResponseMetadata(
                    statusCode = 200, receivedAtEpochMs = 1L,
                    rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
                    retryAfterMs = null,
                ),
            )
        }
    }

    /** Preferences whose commit() answers false while [failCommits] is set (full disk). */
    private class CommitPreferences(var failCommits: Boolean) {
        private val values = linkedMapOf<String, String?>()

        val preferences: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "CommitPreferences"
                else -> error("Unexpected preference operation: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = linkedMapOf<String, String?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "putString" -> { pending[args!![0] as String] = args[1] as String?; proxy }
                    "remove" -> { pending[args!![0] as String] = null; proxy }
                    "commit" -> if (failCommits) {
                        false
                    } else {
                        pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                        true
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    else -> error("Unexpected editor operation: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
