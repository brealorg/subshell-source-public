package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityWikiTarget
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.model.CommunityWikiIndex
import app.subshell.prototype.model.CommunityWikiPage
import app.subshell.prototype.model.CommunityWikiPageRef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (wiki01a2): a wiki index or page already loaded is not read again
 * unless forced; an answer for a community no longer asked for is dropped;
 * and a missing wiki grant shows as "authorization required".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Step3CommunityWikiCoordinatorTest {
    private val account = "reddit:me"
    private val loads = mutableListOf<String>()

    private fun index(community: String) = CommunityWikiIndex(community, listOf(CommunityWikiPageRef("index")))

    private inner class Loader(
        private val answer: suspend (AuthenticatedRedditCommunityWikiTarget) -> AuthenticatedRedditCommunityWikiResult = { target ->
            if (target.pagePath == null) {
                AuthenticatedRedditCommunityWikiResult.IndexLoaded(index(target.communityName))
            } else {
                AuthenticatedRedditCommunityWikiResult.PageLoaded(CommunityWikiPage(target.communityName, target.pagePath, "# ${target.pagePath}"))
            }
        },
    ) : AuthenticatedRedditCommunityWikiLoader {
        override suspend fun load(accountStableId: String, target: AuthenticatedRedditCommunityWikiTarget): AuthenticatedRedditCommunityWikiResult {
            loads += target.requestKey
            return answer(target)
        }
    }

    private fun TestScope.coordinator(loader: AuthenticatedRedditCommunityWikiLoader) = CommunityWikiCoordinator(
        wikiLoader = loader,
        wikiAuthorizer = null,
        wikiClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        activeAuthenticatedAccountStableId = { account },
        scope = this,
    )

    @Test
    fun aLoadedIndexOrPageIsNotReadAgainUnlessForced() = runTest(StandardTestDispatcher()) {
        val c = coordinator(Loader())

        c.loadRedditCommunityWikiIndex(account, "kotlin")
        advanceUntilIdle()
        c.loadRedditCommunityWikiIndex(account, "kotlin")
        advanceUntilIdle()
        c.loadRedditCommunityWikiPage(account, "kotlin", "faq")
        advanceUntilIdle()
        c.loadRedditCommunityWikiPage(account, "kotlin", "faq")
        advanceUntilIdle()
        assertEquals(2, loads.size)

        c.loadRedditCommunityWikiPage(account, "kotlin", "faq", forceRefresh = true)
        advanceUntilIdle()
        assertEquals(3, loads.size)
        assertEquals("# faq", c.redditCommunityWikiUiState.value.page?.markdown)
    }

    @Test
    fun anAnswerForACommunityNoLongerAskedForIsDropped() = runTest(StandardTestDispatcher()) {
        val older = CompletableDeferred<Unit>()
        val c = coordinator(
            Loader { target ->
                if (target.communityName == "kotlin") withContext(NonCancellable) { older.await() }
                AuthenticatedRedditCommunityWikiResult.IndexLoaded(index(target.communityName))
            },
        )

        c.loadRedditCommunityWikiIndex(account, "kotlin")
        advanceUntilIdle()
        c.loadRedditCommunityWikiIndex(account, "android")
        older.complete(Unit)
        advanceUntilIdle()

        val state = c.redditCommunityWikiUiState.value
        assertEquals("android", state.communityName)
        assertEquals("android", state.index?.communityName)
    }

    @Test
    fun aMissingWikiGrantShowsAsAuthorizationRequired() = runTest(StandardTestDispatcher()) {
        val c = coordinator(Loader { AuthenticatedRedditCommunityWikiResult.AuthorizationRequired })

        c.loadRedditCommunityWikiIndex(account, "kotlin")
        advanceUntilIdle()

        assertTrue(c.redditCommunityWikiUiState.value.authorizationRequired)
    }
}
