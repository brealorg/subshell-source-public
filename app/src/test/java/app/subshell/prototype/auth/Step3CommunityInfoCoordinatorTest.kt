package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditCommunityInfoSection
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoSection
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoSectionFailure
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoTarget
import app.subshell.prototype.model.CommunityInfoSnapshot
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (communityinfo01a2): community info keeps one request per account
 * and community — a second open does not read again unless forced, and a
 * refresh keeps what is on screen while it runs; a request for another
 * community drops the answer to the first; a missing loader, a thrown read
 * or a bad name each end in their own failure; a missing section is named.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Step3CommunityInfoCoordinatorTest {
    private val account = "reddit:me"
    private val calls = mutableListOf<String>()

    private fun snapshot(name: String) = CommunityInfoSnapshot(communityName = name, sidebarMarkdown = "about $name")

    private inner class Loader(
        private val answer: suspend (String) -> AuthenticatedRedditCommunityInfoResult = {
            AuthenticatedRedditCommunityInfoResult.Loaded(snapshot(it))
        },
    ) : AuthenticatedRedditCommunityInfoLoader {
        override suspend fun load(accountStableId: String, target: AuthenticatedRedditCommunityInfoTarget): AuthenticatedRedditCommunityInfoResult {
            calls += target.communityName
            return answer(target.communityName)
        }
    }

    private fun TestScope.coordinator(loader: AuthenticatedRedditCommunityInfoLoader?) =
        CommunityInfoCoordinator(loader, this)

    @Test
    fun aSecondOpenDoesNotReadAgainUnlessForcedAndARefreshKeepsWhatIsShown() = runTest(StandardTestDispatcher()) {
        val release = CompletableDeferred<Unit>()
        var gated = false
        val c = coordinator(
            Loader { name ->
                if (gated) release.await()
                AuthenticatedRedditCommunityInfoResult.Loaded(snapshot(name))
            },
        )

        c.loadRedditCommunityInfo(account, "kotlin")
        c.loadRedditCommunityInfo(account, "kotlin")
        advanceUntilIdle()
        c.loadRedditCommunityInfo(account, "kotlin")
        advanceUntilIdle()
        assertEquals(listOf("kotlin"), calls)
        assertEquals(1L, c.redditCommunityInfoUiState.value.contentRevision)

        gated = true
        c.loadRedditCommunityInfo(account, "kotlin", forceRefresh = true)
        advanceUntilIdle()
        val refreshing = c.redditCommunityInfoUiState.value
        assertTrue(refreshing.loading)
        assertEquals("about kotlin", refreshing.snapshot?.sidebarMarkdown)

        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("kotlin", "kotlin"), calls)
        assertEquals(2L, c.redditCommunityInfoUiState.value.contentRevision)
    }

    @Test
    fun aRequestForAnotherCommunityDropsTheAnswerToTheFirst() = runTest(StandardTestDispatcher()) {
        val first = CompletableDeferred<Unit>()
        val c = coordinator(
            Loader { name ->
                // A read that does not stop when cancelled still answers late.
                if (name == "kotlin") withContext(NonCancellable) { first.await() }
                AuthenticatedRedditCommunityInfoResult.Loaded(snapshot(name))
            },
        )

        c.loadRedditCommunityInfo(account, "kotlin")
        advanceUntilIdle()
        c.loadRedditCommunityInfo(account, "android")
        first.complete(Unit)
        advanceUntilIdle()

        val state = c.redditCommunityInfoUiState.value
        assertEquals("android", state.communityName)
        assertEquals("about android", state.snapshot?.sidebarMarkdown)
    }

    @Test
    fun aMissingLoaderAThrownReadAndABadNameEachHaveTheirOwnFailure() = runTest(StandardTestDispatcher()) {
        val none = coordinator(null)
        none.loadRedditCommunityInfo(account, "kotlin")
        assertEquals(RedditCommunityInfoUiFailureKind.CLIENT_CONFIGURATION, none.redditCommunityInfoUiState.value.failureKind)

        val throwing = coordinator(Loader { error("socket closed") })
        throwing.loadRedditCommunityInfo(account, "kotlin")
        advanceUntilIdle()
        assertEquals(RedditCommunityInfoUiFailureKind.TRANSPORT, throwing.redditCommunityInfoUiState.value.failureKind)
        assertTrue(throwing.redditCommunityInfoUiState.value.failureRetryable)

        val named = coordinator(Loader())
        named.loadRedditCommunityInfo(account, " /r/ ")
        assertEquals(RedditCommunityInfoUiFailureKind.PROTOCOL, named.redditCommunityInfoUiState.value.failureKind)
        assertEquals(listOf("kotlin"), calls)
    }

    @Test
    fun aCancelledReadIsNotTurnedIntoAFailure() = runTest(StandardTestDispatcher()) {
        val c = coordinator(Loader { throw CancellationException("left the screen") })

        c.loadRedditCommunityInfo(account, "kotlin")
        advanceUntilIdle()

        val state = c.redditCommunityInfoUiState.value
        assertEquals(null, state.failureKind)
        assertFalse(state.loaded)
    }

    @Test
    fun aMissingSectionIsNamedWithTheRestLoaded() = runTest(StandardTestDispatcher()) {
        val c = coordinator(
            Loader { name ->
                AuthenticatedRedditCommunityInfoResult.Loaded(
                    snapshot(name),
                    sectionFailures = listOf(
                        AuthenticatedRedditCommunityInfoSectionFailure(
                            section = AuthenticatedRedditCommunityInfoSection.MODERATORS,
                            kind = AuthenticatedRedditCommunityInfoFailureKind.HTTP,
                            retryable = false,
                        ),
                    ),
                )
            },
        )

        c.loadRedditCommunityInfo(account, "kotlin")
        advanceUntilIdle()

        val state = c.redditCommunityInfoUiState.value
        assertTrue(state.loaded)
        assertEquals(setOf(RedditCommunityInfoSection.MODERATORS), state.unavailableSections)
        assertEquals(1L, state.contentRevision)
    }
}
