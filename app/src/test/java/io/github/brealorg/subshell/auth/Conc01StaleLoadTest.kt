package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.RedditRemovalReason
import io.github.brealorg.subshell.data.account.UserFlairSnapshot
import io.github.brealorg.subshell.data.account.UserFlairMutationRequest
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditUserFlairLoadResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditUserFlairManager
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditUserFlairMutationResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditRemovalReasonLoader
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditRemovalReasonResult
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
