package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountId
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSessionIntegrationPolicyTest {
    @Test
    fun localAndAuthenticatedAccountsUseStableIdsAndSanitizedUiState() {
        val local = localFixtureAccountSummaries(listOf("root", "packetmoss"))
        val authenticated = AccountSessionSummary(
            stableId = "reddit:t2_alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val selected = mutableListOf<String>()
        val persisted = mutableListOf<String>()
        val state = SubshellState(
            initialAccountSessions = local + authenticated,
            initialActiveAccountId = authenticated.stableId,
            selectAccountSession = selected::add,
            persistActiveAccountId = persisted::add,
        )

        assertEquals("alice", state.activeAccountUsername)
        assertEquals(authenticated.stableId, state.activeAccountId)
        assertEquals(AccountSessionState.CONNECTED, state.activeAccountSession.state)

        state.switchAccount(localFixtureAccountId("root"))

        assertEquals("root", state.activeAccountUsername)
        assertEquals(localFixtureAccountId("root"), state.activeAccountId)
        assertEquals(listOf(localFixtureAccountId("root")), selected)
        assertEquals(selected, persisted)
        assertTrue(state.accountSessions.none { it.toString().contains("token", ignoreCase = true) })
    }

    @Test
    fun usernameCollisionAcrossFixtureAndAuthenticatedAccountsIsRejected() {
        val local = localFixtureAccountSummaries(listOf("root"))
        val colliding = AccountSessionSummary(
            stableId = "reddit:t2_root",
            username = "ROOT",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )

        assertThrows(IllegalArgumentException::class.java) {
            SubshellState(initialAccountSessions = local + colliding)
        }
    }

    @Test
    fun legacyUsernameSelectionStillResolvesToStableLocalAccountId() {
        val state = SubshellState(initialActiveAccountUsername = "packetmoss")

        assertEquals("packetmoss", state.activeAccountUsername)
        assertEquals(localFixtureAccountId("packetmoss"), state.activeAccountId)
    }

    @Test
    fun authenticatedAccountAllowsRemoteTextDraftingButRejectsLocalWriteMutations() {
        val authenticated = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_readonly",
            username = "readonly",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + authenticated,
            initialActiveAccountId = authenticated.stableId,
        )
        val postId = state.posts.first { post -> post.id !in state.savedPostIds }.id
        val savedBefore = state.savedPostIds.toSet()
        val votesBefore = state.votes.toMap()
        val communitiesBefore = state.joinedCommunityIds.toSet()
        val followedBefore = state.followedUsernames.toSet()

        assertTrue(state.activeAccountIsAuthenticated)
        assertFalse(state.localMutationsAllowed)
        state.toggleSaved(postId)
        state.vote(postId, 1)
        state.toggleCommunity(state.communities.first().id)
        state.toggleFollowUser("someone")
        state.openPostComposer()

        assertEquals(savedBefore, state.savedPostIds.toSet())
        assertEquals(votesBefore, state.votes.toMap())
        assertEquals(communitiesBefore, state.joinedCommunityIds.toSet())
        assertEquals(followedBefore, state.followedUsernames.toSet())
        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)
        assertEquals("", state.postComposerDraft.community)
        assertEquals(
            "Choose a community",
            state.postComposerValidationMessage,
        )
        assertNull(state.publishPostComposerDraft())
    }

    @Test
    fun invalidatedSameAccountQueryCanLoadAgain() {
        val account = localFixtureAccountSummaries(listOf("root")).single()
        val state = SubshellState(initialAccountSessions = listOf(account))
        val queryKey = mainPostFeedQueryKey(
            account = account,
            source = FeedSource.HOME,
            sort = FeedSort.BEST,
        )

        state.ensurePostPaging(queryKey)
        assertTrue(state.postPagingSnapshot(queryKey).items.isNotEmpty())
        val previousRevision = state.postPagingInvalidationRevision

        state.invalidatePostPaging()

        assertEquals(previousRevision + 1, state.postPagingInvalidationRevision)
        assertTrue(state.postPagingSnapshot(queryKey).items.isEmpty())

        state.ensurePostPaging(queryKey)
        assertTrue(state.postPagingSnapshot(queryKey).items.isNotEmpty())
    }

    @Test
    fun authenticatedSearchFederatesGloballyAndScopesCurrentCommunityPosts() {
        val authenticated = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_search",
            username = "searcher",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + authenticated,
            initialActiveAccountId = authenticated.stableId,
        )

        state.openSearch(refocus = false)
        assertEquals(
            listOf(
                SearchScope.ALL,
                SearchScope.POSTS,
                SearchScope.COMMUNITIES,
                SearchScope.USERS,
            ),
            state.availableSearchScopes,
        )
        assertEquals(SearchScope.ALL, state.searchScope)
        state.updateSearchQuery("Marc Marquez")
        state.closeSearch()
        state.openSubreddit("motogp")
        state.openSearch(refocus = false)
        assertEquals("", state.searchQuery)
        state.updateSearchQuery("Valentino Rossi")

        assertEquals(listOf(SearchScope.POSTS), state.availableSearchScopes)
        assertFalse(SearchArea.MY_COMMUNITIES in state.availableSearchAreas)
        val key = searchPostFeedQueryKey(
            account = state.activeAccountSession,
            destination = state.searchOriginDestination,
            originCommunity = state.searchOriginCommunity,
            query = state.searchQuery,
            scope = state.searchScope,
            area = state.searchArea,
        )
        assertEquals(
            "account|authenticated|reddit:redreader_compatibility:t2_search|" +
                "live05|search|motogp|valentino rossi",
            key.value,
        )
    }
}
