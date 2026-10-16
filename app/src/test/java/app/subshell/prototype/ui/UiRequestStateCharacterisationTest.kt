package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the one-shot UI request counters and the pending
 * post-action tokens inside SubshellState as they behave today, written
 * before they move to UiRequestState. Current behaviour, not a
 * specification.
 */
class UiRequestStateCharacterisationTest {
    @Test
    fun drawerAndAccountSwitcherRequestsAreMonotonicCounters() {
        val state = state()
        assertEquals(0, state.drawerOpenRequest)
        assertEquals(0, state.accountSwitcherOpenRequest)
        state.requestDrawer()
        state.requestDrawer()
        state.requestAccountSwitcher()
        assertEquals(2, state.drawerOpenRequest)
        assertEquals(1, state.accountSwitcherOpenRequest)
    }

    @Test
    fun homeScrollToTopIsRaisedByReselectingHomeAndConsumedOnce() {
        val state = state()
        assertEquals(0, state.homeScrollToTopRequest)
        assertFalse(state.consumeHomeScrollToTopRequest())

        state.navigate(MainDestination.HOME)
        assertEquals(1, state.homeScrollToTopRequest)
        assertTrue(state.consumeHomeScrollToTopRequest())
        assertFalse(state.consumeHomeScrollToTopRequest())

        // Moving away and back is not a reselect.
        state.navigate(MainDestination.INBOX)
        state.navigate(MainDestination.HOME)
        assertEquals(1, state.homeScrollToTopRequest)
        assertFalse(state.consumeHomeScrollToTopRequest())

        state.navigate(MainDestination.HOME)
        state.navigate(MainDestination.HOME)
        assertEquals(3, state.homeScrollToTopRequest)
        assertTrue(state.consumeHomeScrollToTopRequest())
        assertFalse(state.consumeHomeScrollToTopRequest())
    }

    @Test
    fun searchFocusRequestsAreRaisedByOpenersAndTheExploreOneResetsOnlyWhenMatching() {
        val state = state()
        assertEquals(0, state.searchFocusRequest)
        state.openSearch(refocus = false)
        assertEquals(0, state.searchFocusRequest)
        state.openSearch(refocus = true)
        assertEquals(1, state.searchFocusRequest)

        assertEquals(0, state.communityExploreSearchFocusRequest)
        state.openCommunityExplore(focusSearch = true)
        assertEquals(1, state.communityExploreSearchFocusRequest)
        state.consumeCommunityExploreSearchFocusRequest(7)
        assertEquals(1, state.communityExploreSearchFocusRequest)
        state.consumeCommunityExploreSearchFocusRequest(1)
        assertEquals(0, state.communityExploreSearchFocusRequest)
    }

    @Test
    fun postActionTokensNormaliseIdsConsumeOnceAndAreClearedByADeletedPost() {
        val state = state()
        assertFalse(state.consumePostManageRequest("abc"))
        assertFalse(state.consumePostDeleteRequest("abc"))
        assertFalse(state.consumePostEditRequest("abc"))

        // Manage and delete normalise the t3_ prefix, whitespace and case.
        state.requestPostManage(" t3_ABC ")
        assertFalse(state.consumePostManageRequest("xyz"))
        assertTrue(state.consumePostManageRequest("abc"))
        assertFalse(state.consumePostManageRequest("abc"))

        state.requestPostDelete("t3_Abc")
        assertTrue(state.consumePostDeleteRequest(" ABC"))
        assertFalse(state.consumePostDeleteRequest("abc"))

        // The prefix strip is case-sensitive: "T3_" is kept, then lowercased,
        // so only another upper-case-prefixed id matches.
        state.requestPostDelete("T3_Abc")
        assertFalse(state.consumePostDeleteRequest("abc"))
        assertFalse(state.consumePostDeleteRequest("t3_abc"))
        assertTrue(state.consumePostDeleteRequest("T3_ABC"))

        // Edit strips the prefix and whitespace but keeps case.
        state.requestPostEdit(" t3_Abc ")
        assertFalse(state.consumePostEditRequest("abc"))
        assertTrue(state.consumePostEditRequest("t3_Abc"))
        assertFalse(state.consumePostEditRequest("Abc"))

        // Blank ids never register; a pending token survives an unrelated request.
        state.requestPostManage("   ")
        assertFalse(state.consumePostManageRequest(""))
        state.requestPostManage("first")
        state.requestPostManage("second")
        assertFalse(state.consumePostManageRequest("first"))
        assertTrue(state.consumePostManageRequest("second"))

        // A deleted post clears its own edit token and any delete token.
        state.requestPostEdit("keep")
        state.requestPostEdit("gone")
        state.requestPostDelete("other")
        state.applyDeletedPost("t3_GONE")
        assertFalse(state.consumePostDeleteRequest("other"))
        assertFalse(state.consumePostEditRequest("gone"))
        state.requestPostEdit("keep")
        state.applyDeletedPost("unrelated")
        assertTrue(state.consumePostEditRequest("keep"))
    }

    private fun state(): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
    )
}
