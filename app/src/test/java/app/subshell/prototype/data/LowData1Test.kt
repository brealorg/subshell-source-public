package app.subshell.prototype.data

import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.DirectChatReaction
import app.subshell.prototype.data.account.DirectChatReactionCachedTarget
import app.subshell.prototype.data.account.DirectChatReactionPresentationSnapshot
import app.subshell.prototype.data.account.mergeDirectChatReactionPresentationSnapshots
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthProfileHistoryCapability
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditSavedHistoryResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.reddit.RedditSavedHistoryLoader
import app.subshell.prototype.data.cache.RedditSavedHistoryCacheStore
import app.subshell.prototype.data.network.NetworkEndpoint
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DATA1 (low data/cache + data/account findings): a Saved "load more" without
 * its cached list reloads from the top (OCB-618), an unnamed membership state
 * matches no community (OCB-601), carried-over reaction targets keep their
 * age (OCB-463).
 */
class LowData1Test {
    @Test
    fun aSavedAppendWithoutItsCachedListReloadsFromTheTop() = runBlocking {
        val requests = mutableListOf<Request>()
        val loader = RedditSavedHistoryLoader(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("ok")
                    .body(
                        """{"kind":"Listing","data":{"after":"t3_next","children":[]}}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            cacheStore = RedditSavedHistoryCacheStore(Files.createTempDirectory("saved").toFile()),
            capability = object : AuthProfileHistoryCapability {
                override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                    AuthSessionResult.Authenticated(
                        StoredOAuthSession(
                            provider = OAuthProvider.REDDIT,
                            tokens = OAuthTokenSet("token", null, "bearer", "history", Long.MAX_VALUE),
                            identity = AuthAccountIdentity("me", "me"),
                            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                        ),
                    )
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = loader.append("reddit:redreader_compatibility:writer", "writer", after = "t3_gone")

        assertTrue("$result", result is AuthenticatedRedditSavedHistoryResult.Loaded)
        assertEquals(2, requests.size)
        assertNull("the reload starts at the top", requests.last().url.queryParameter("after"))
    }

    @Test
    fun anUnnamedMembershipStateMatchesNoCommunity() {
        val unnamed = CommunityMembershipUiState(
            phase = CommunityMembershipPhase.FAILED,
            accountStableId = "acct",
            communityName = null,
        )
        assertFalse(unnamed.matches("acct", "not a valid name!"))
        val named = CommunityMembershipUiState(
            phase = CommunityMembershipPhase.FAILED,
            accountStableId = "acct",
            communityName = "subshell",
        )
        assertTrue(named.matches("acct", "r/SubShell"))
    }

    @Test
    fun carriedOverReactionTargetsKeepTheirAge() {
        fun target(id: String) = DirectChatReactionCachedTarget(id, outgoing = true, createdEpochSeconds = 1L, mediaId = null, bodySha256 = "0".repeat(64))
        fun reaction(id: String, target: String) = DirectChatReaction(id, target, key = "👍", senderIsSelf = true)
        val stored = DirectChatReactionPresentationSnapshot(
            targets = listOf(target("\$old")),
            reactions = listOf(reaction("\$r1", "\$old")),
            updatedAtEpochMs = 1_000L,
        )
        val live = DirectChatReactionPresentationSnapshot(
            targets = listOf(target("\$new")),
            reactions = listOf(reaction("\$r2", "\$new")),
            updatedAtEpochMs = 9_000L,
        )

        val merged = mergeDirectChatReactionPresentationSnapshots(live, stored, liveMessageIds = setOf("\$new"))

        assertEquals(listOf("\$old", "\$new"), merged.targets.map(DirectChatReactionCachedTarget::targetEventId))
        assertEquals(1_000L, merged.updatedAtEpochMs)
        assertEquals(9_000L, mergeDirectChatReactionPresentationSnapshots(live, null, setOf("\$new")).updatedAtEpochMs)
    }
}
