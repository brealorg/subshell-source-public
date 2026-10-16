package app.subshell.prototype.ui.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ISSUE88_ERROR_TRAY_KEEPS_EVERY_ACTIVE_SCOPE_V1: the tray had one slot; a
 * newer error replaced an older one that still applied, and clearing or
 * dismissing the newer emptied the tray, so the older lost its Retry.
 */
class Issue88ErrorTrayScopesTest {
    private fun presentation(title: String) = ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = title,
        message = "Something failed",
        primaryAction = ErrorAction(ErrorActionKind.RETRY, "Retry"),
    )

    private val host = ViewportErrorHostState()
    private var feedRetries = 0

    private fun feedFails() = host.present("home", presentation("Couldn't refresh the feed"), null) { feedRetries += 1 }
    private fun voteFails() = host.present("vote", presentation("Couldn't update vote"), null) {}

    @Test
    fun theFeedErrorComesBackWhenTheVoteErrorClears() {
        feedFails()
        voteFails()
        assertEquals("Couldn't update vote", host.currentRequest?.presentation?.title)

        host.clear("vote")

        assertEquals("Couldn't refresh the feed", host.currentRequest?.presentation?.title)
        host.retryCurrent()
        assertEquals(1, feedRetries)
    }

    @Test
    fun dismissingTheNewerErrorShowsTheOlderOne() {
        feedFails()
        voteFails()

        host.dismissCurrent()

        assertEquals("Couldn't refresh the feed", host.currentRequest?.presentation?.title)
    }

    @Test
    fun eachScopeKeepsItsOwnDismissal() {
        feedFails()
        host.dismissCurrent()
        voteFails()
        host.dismissCurrent()
        assertNull(host.currentRequest)

        // Both effects run again with the same errors: both stay dismissed.
        feedFails()
        voteFails()

        assertNull(host.currentRequest)
    }

    @Test
    fun theSameErrorAgainKeepsItsPlace() {
        feedFails()
        voteFails()

        feedFails()

        assertEquals("Couldn't update vote", host.currentRequest?.presentation?.title)
    }
}
