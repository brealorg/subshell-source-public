package app.subshell.prototype.ui

import app.subshell.prototype.data.RepositoryQueryKey
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedEntryRefreshGateTest {
    private val home = RepositoryQueryKey("issue17|account-a|home|best")
    private val community = RepositoryQueryKey("issue17|account-a|r/norge|hot")

    @Test
    fun firstEntryExecutesAndEveryReentrySkips() {
        val gate = FeedEntryRefreshGate()

        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, gate.claim(home))
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, gate.claim(home))
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, gate.claim(home, deepInList = true))
    }

    @Test
    fun reentryNeverRefreshesAutomaticallyAgainLikePagingInitialize() {
        // Issue #40: a later automatic refresh rewrote the query to page one
        // and threw a reader past it back up mid-scroll. Paging 3 decides once,
        // when the Pager is created; within a session freshness is
        // pull-to-refresh.
        val gate = FeedEntryRefreshGate()

        gate.claim(home)
        repeat(100) {
            assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, gate.claim(home))
        }
    }

    @Test
    fun aFirstEntryDeepInTheListIsDeferredUntilAnEntryNearTheTop() {
        val gate = FeedEntryRefreshGate()

        assertEquals(FeedEntryRefreshDecision.DEFER_DEEP, gate.claim(home, deepInList = true))
        assertEquals(FeedEntryRefreshDecision.DEFER_DEEP, gate.claim(home, deepInList = true))
        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, gate.claim(home, deepInList = false))
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, gate.claim(home))
    }

    @Test
    fun queryKeysAreClaimedIndependently() {
        val gate = FeedEntryRefreshGate()

        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, gate.claim(home))
        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, gate.claim(community))
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, gate.claim(community))
    }

    @Test
    fun resetForgetsEveryClaim() {
        val gate = FeedEntryRefreshGate()
        gate.claim(home)
        gate.claim(community)

        gate.reset()

        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, gate.claim(home))
        assertEquals(FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY, gate.claim(community))
    }

    @Test
    fun onlyTheFirstEntryExecutes() {
        assertEquals(true, FeedEntryRefreshDecision.EXECUTE_FIRST_ENTRY.execute)
        assertEquals(false, FeedEntryRefreshDecision.SKIP_REENTRY.execute)
        assertEquals(false, FeedEntryRefreshDecision.DEFER_DEEP.execute)
    }

    @Test
    fun deepMeansPastTheFirstRefreshedPage() {
        assertEquals(false, FeedScrollAnchor(itemKey = "t3_a", fallbackIndex = 24, pixelOffset = 0).isDeepForAutomaticRefresh())
        assertEquals(true, FeedScrollAnchor(itemKey = "t3_b", fallbackIndex = 25, pixelOffset = 0).isDeepForAutomaticRefresh())
        assertEquals(false, FeedScrollAnchor.Start.isDeepForAutomaticRefresh())
    }
}
