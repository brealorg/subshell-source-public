package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditRemovalReason
import app.subshell.prototype.data.account.UserFlairSnapshot
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairLoadResult
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairManager
import app.subshell.prototype.data.auth.AuthenticatedRedditUserFlairMutationResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditRemovalReasonLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditRemovalReasonResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CONC01: a slower, older load that finishes last no longer overwrites the
 * newer request's state (OCB-447 removal reasons, OCB-449 user flair).
 */
class Conc01StaleLoadTest {
    @Test
    fun anOlderRemovalReasonLoadFinishingLastDoesNotOverwriteTheNewerOne() = runBlocking {
        val slow = CompletableDeferred<Unit>()
        val coordinator = RemovalReasonCoordinator(
            capability = null,
            loader = object : AuthenticatedRedditRemovalReasonLoader {
                override suspend fun load(accountStableId: String, communityName: String): AuthenticatedRedditRemovalReasonResult {
                    if (communityName == "older") slow.await()
                    return AuthenticatedRedditRemovalReasonResult.Loaded(
                        listOf(RedditRemovalReason(id = communityName, title = communityName, message = "m")),
                    )
                }
            },
            clientMode = null,
            activeAccountStableId = { "acc" },
        )

        val older = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { coordinator.load("acc", "older") }
        coordinator.load("acc", "newer")
        slow.complete(Unit)
        older.join()

        assertEquals("newer", coordinator.uiState.value.communityName)
        assertEquals(listOf("newer"), coordinator.uiState.value.reasons.map { it.id })
    }

    @Test
    fun aSlowFlairLoadForAnotherCommunityDoesNotLandUnderTheCurrentOne() = runBlocking {
        val slow = CompletableDeferred<Unit>()
        val coordinator = UserFlairCoordinator(
            manager = object : AuthenticatedRedditUserFlairManager {
                override suspend fun load(accountStableId: String, rawCommunityName: String): AuthenticatedRedditUserFlairLoadResult {
                    if (rawCommunityName == "older") slow.await()
                    return AuthenticatedRedditUserFlairLoadResult.Loaded(UserFlairSnapshot(communityName = rawCommunityName))
                }
                override suspend fun mutate(request: UserFlairMutationRequest): AuthenticatedRedditUserFlairMutationResult =
                    error("unused")
            },
            activeAccountStableId = { "acc" },
        )

        val older = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { coordinator.load("acc", "older") }
        coordinator.load("acc", "newer")
        slow.complete(Unit)
        older.join()

        assertEquals("newer", coordinator.uiState.value.communityName)
        assertEquals("newer", coordinator.uiState.value.snapshot?.communityName)
    }
}
