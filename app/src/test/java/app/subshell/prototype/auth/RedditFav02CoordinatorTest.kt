package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditFavoriteUiFailureKind
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.reddit.RedditFavoriteCommunityWriter
import app.subshell.prototype.data.auth.reddit.RedditFavoriteFailureKind
import app.subshell.prototype.data.auth.reddit.RedditFavoriteRequest
import app.subshell.prototype.data.auth.reddit.RedditFavoriteWriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITFAV02_FAVORITE_WRITE_V1: one write per change, Reddit's list read
 * back for the answer, never a second send.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RedditFav02CoordinatorTest {
    private class FakeWriter(
        private val result: RedditFavoriteWriteResult,
        val gate: CompletableDeferred<Unit>? = null,
        private val results: MutableList<RedditFavoriteWriteResult> = mutableListOf(),
    ) : RedditFavoriteCommunityWriter {
        val requests = mutableListOf<RedditFavoriteRequest>()
        override suspend fun update(request: RedditFavoriteRequest): RedditFavoriteWriteResult {
            requests += request
            gate?.await()
            return if (results.isEmpty()) result else results.removeAt(0)
        }
    }

    private class FakeCapability(
        private val completion: AuthCustomFeedWriteCapabilityResult,
    ) : AuthCustomFeedWriteCapability {
        var begun = 0
        var stored = false
        override suspend fun beginAuthorization(accountStableId: String): String {
            begun += 1
            stored = true
            return "https://www.reddit.com/api/v1/authorize"
        }
        override suspend fun hasScopeGrant(accountStableId: String) = false
        override suspend fun authorizedSession(accountStableId: String): StoredOAuthSession? = null
        override fun hasPendingAuthorization() = stored
        override fun hasStoredCallback() = stored
        override fun cancelPendingAuthorization() { stored = false }
        override suspend fun completePendingAuthorization(): AuthCustomFeedWriteCapabilityResult {
            stored = false
            return completion
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }

    private fun session() = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            expiresAtEpochMs = Long.MAX_VALUE,
            scope = "identity mysubreddits read subscribe",
        ),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private fun TestScope.coordinator(
        writer: FakeWriter,
        reads: MutableList<Set<String>?>,
        readCount: IntArray = IntArray(1),
        capability: AuthCustomFeedWriteCapability? = null,
    ) = RedditFavoriteCoordinator(
        writer = writer,
        capability = capability,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        activeAccountStableId = { "reddit:a" },
        readBack = { request ->
            readCount[0] += 1
            val read = if (reads.isEmpty()) null else reads.removeAt(0)
            read?.any { name -> name.equals(request.customFeedPath ?: request.communityName, ignoreCase = true) }
        },
        scope = this,
        readBackDelaysMillis = listOf(0L, 10L, 20L),
    )

    @Test
    fun aConfirmedChangeClearsPendingWithoutFailure() = runTest {
        val writer = FakeWriter(RedditFavoriteWriteResult.Updated, CompletableDeferred())
        val coordinator = coordinator(writer, mutableListOf(setOf("Android", "norge")))

        coordinator.update("reddit:a", "Android", true)
        advanceUntilIdle()
        assertEquals(mapOf("android" to true), coordinator.uiState.value.pending)

        writer.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(coordinator.uiState.value.pending.isEmpty())
        assertNull(coordinator.uiState.value.failure)
        assertEquals(1, writer.requests.size)
    }

    @Test
    fun aSecondTapWhileSendingIsNotSent() = runTest {
        val writer = FakeWriter(RedditFavoriteWriteResult.Updated, CompletableDeferred())
        val coordinator = coordinator(writer, mutableListOf(setOf("Android")))

        coordinator.update("reddit:a", "Android", true)
        coordinator.update("reddit:a", "android", false)
        writer.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, writer.requests.size)
    }

    @Test
    fun aLostAnswerIsReadBackAndNeverResent() = runTest {
        val writer = FakeWriter(RedditFavoriteWriteResult.Failed(RedditFavoriteFailureKind.OUTCOME_UNKNOWN))
        val reads = IntArray(1)
        val coordinator = coordinator(writer, mutableListOf(setOf("norge"), setOf("norge", "Android")), reads)

        coordinator.update("reddit:a", "Android", true)
        advanceUntilIdle()

        assertEquals(1, writer.requests.size)
        assertEquals(2, reads[0])
        assertNull(coordinator.uiState.value.failure)
    }

    @Test
    fun aChangeRedditsListNeverShowsIsNotConfirmed() = runTest {
        val writer = FakeWriter(RedditFavoriteWriteResult.Updated)
        val coordinator = coordinator(writer, mutableListOf(setOf("norge"), null, setOf("norge")))

        coordinator.update("reddit:a", "Android", true)
        advanceUntilIdle()

        assertEquals(RedditFavoriteUiFailureKind.NOT_CONFIRMED, coordinator.uiState.value.failure?.kind)
        assertTrue(coordinator.uiState.value.pending.isEmpty())
        assertEquals(1, writer.requests.size)
    }

    @Test
    fun refusalsAreReportedWithoutReadBack() = runTest {
        val reads = IntArray(1)
        val refused = coordinator(
            FakeWriter(RedditFavoriteWriteResult.Failed(RedditFavoriteFailureKind.HTTP)),
            mutableListOf(),
            reads,
        )
        refused.update("reddit:a", "Android", true)
        advanceUntilIdle()
        assertEquals(RedditFavoriteUiFailureKind.FAILED, refused.uiState.value.failure?.kind)

        val unauthorized = coordinator(FakeWriter(RedditFavoriteWriteResult.AuthorizationRequired), mutableListOf(), reads)
        unauthorized.update("reddit:a", "norge", false)
        advanceUntilIdle()
        assertEquals(RedditFavoriteUiFailureKind.AUTHORIZATION, unauthorized.uiState.value.failure?.kind)
        assertEquals(0, reads[0])
    }

    @Test
    fun anotherAccountsChangeIsIgnored() = runTest {
        val writer = FakeWriter(RedditFavoriteWriteResult.Updated)
        val coordinator = coordinator(writer, mutableListOf())

        coordinator.update("reddit:b", "Android", true)
        advanceUntilIdle()

        assertTrue(writer.requests.isEmpty())
    }

    // REDDITFAV03_FAVORITE_AUTHORIZATION_V1
    @Test
    fun authorizeSendsTheRefusedChangeOnce() = runTest {
        val writer = FakeWriter(
            RedditFavoriteWriteResult.Updated,
            results = mutableListOf(RedditFavoriteWriteResult.AuthorizationRequired),
        )
        val capability = FakeCapability(
            AuthCustomFeedWriteCapabilityResult.Upgraded("reddit:a", "brealorg", session()),
        )
        val coordinator = coordinator(writer, mutableListOf(setOf("Android")), capability = capability)

        coordinator.update("reddit:a", "Android", true)
        advanceUntilIdle()
        assertEquals(RedditFavoriteUiFailureKind.AUTHORIZATION, coordinator.uiState.value.failure?.kind)

        assertTrue(coordinator.beginAuthorization() is AuthBrowserLaunchResult.Ready)
        assertTrue(coordinator.uiState.value.authorizing)
        assertTrue(coordinator.handleCallbackResultTokenIfOwned("ok"))
        advanceUntilIdle()

        assertEquals(2, writer.requests.size)
        assertEquals(writer.requests[0], writer.requests[1])
        assertTrue(coordinator.uiState.value.pending.isEmpty())
        assertTrue(!coordinator.uiState.value.authorizing)
        // Nothing left to authorize: a second Authorize does not open the browser.
        assertEquals(AuthBrowserLaunchResult.NotStarted, coordinator.beginAuthorization())
    }

    @Test
    fun aCancelledAuthorizationSendsNothing() = runTest {
        val writer = FakeWriter(RedditFavoriteWriteResult.AuthorizationRequired)
        val capability = FakeCapability(
            AuthCustomFeedWriteCapabilityResult.Upgraded("reddit:a", "brealorg", session()),
        )
        val coordinator = coordinator(writer, mutableListOf(), capability = capability)

        coordinator.update("reddit:a", "Android", true)
        advanceUntilIdle()
        coordinator.beginAuthorization()
        coordinator.handleCallbackResultTokenIfOwned(AUTH_CALLBACK_CANCELLED_TOKEN)
        advanceUntilIdle()

        assertEquals(1, writer.requests.size)
        assertTrue(!coordinator.uiState.value.authorizing)
    }

    // REDDITFAV04_CUSTOM_FEED_FAVORITES_V1
    @Test
    fun aCustomFeedChangeIsSentByPathAndReadBackFromTheFeeds() = runTest {
        val writer = FakeWriter(RedditFavoriteWriteResult.Updated)
        val coordinator = coordinator(writer, mutableListOf(setOf("/user/brealorg/m/baesj/")))

        coordinator.update("reddit:a", "baesj", true, customFeedPath = "/user/brealorg/m/baesj/")
        assertEquals(mapOf("/user/brealorg/m/baesj/" to true), coordinator.uiState.value.pendingCustomFeeds)
        assertTrue(coordinator.uiState.value.pending.isEmpty())
        advanceUntilIdle()

        assertEquals("/user/brealorg/m/baesj/", writer.requests.single().customFeedPath)
        assertTrue(coordinator.uiState.value.pendingCustomFeeds.isEmpty())
        assertNull(coordinator.uiState.value.failure)
    }
}
