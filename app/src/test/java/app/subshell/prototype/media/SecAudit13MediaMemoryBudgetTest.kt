package app.subshell.prototype.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SECAUDIT13 (security audit #13): large images and GIFs loading side by
 * side could each hold up to 100 MB and exhaust the heap. Their reads and
 * decodes share one byte budget; a load that does not fit waits.
 */
class SecAudit13MediaMemoryBudgetTest {
    private val mib = 1_024L * 1_024L

    @Test
    fun aLoadThatDoesNotFitWaitsForTheEarlierOne() = runBlocking {
        val budget = RemoteMediaMemoryBudget(100 * mib)
        val firstMayFinish = CompletableDeferred<Unit>()
        var secondRan = false

        val first = async { budget.withReservation(60 * mib) { firstMayFinish.await() } }
        repeat(5) { yield() }
        val second = async { budget.withReservation(60 * mib) { secondRan = true } }
        repeat(5) { yield() }
        assertFalse("the second load waits while the first holds the budget", secondRan)

        firstMayFinish.complete(Unit)
        withTimeout(5_000) { first.await(); second.await() }
        assertTrue(secondRan)
        assertEquals(100, budget.availableMib)
    }

    @Test
    fun smallLoadsRunSideBySide() = runBlocking {
        val budget = RemoteMediaMemoryBudget(100 * mib)
        val bothInside = CompletableDeferred<Unit>()
        var inside = 0

        val loads = List(2) {
            async {
                budget.withReservation(10 * mib) {
                    inside += 1
                    if (inside == 2) bothInside.complete(Unit)
                    bothInside.await()
                }
            }
        }
        withTimeout(5_000) { loads.forEach { it.await() } }
        assertEquals(100, budget.availableMib)
    }

    @Test
    fun aLoadLargerThanTheBudgetRunsAloneAndAFailureGivesItsBytesBack() = runBlocking {
        val budget = RemoteMediaMemoryBudget(100 * mib)

        assertEquals("ran", withTimeout(5_000) { budget.withReservation(500 * mib) { "ran" } })
        runCatching { budget.withReservation(30 * mib) { error("decode failed") } }

        assertEquals(100, budget.availableMib)
    }
}
