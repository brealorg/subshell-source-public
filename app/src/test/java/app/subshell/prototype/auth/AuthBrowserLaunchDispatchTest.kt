package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.OAuthClientMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthBrowserLaunchDispatchTest {
    private val ready = AuthBrowserLaunchResult.Ready(
        authorizationUrl = "https://www.reddit.com/api/v1/authorize?client_id=harness",
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        freshAccountSession = true,
    )

    @Test
    fun aReadyResultOpensTheBrowserOnceAndReportsNothing() {
        val opened = mutableListOf<AuthBrowserLaunchResult.Ready>()
        var failures = 0

        dispatchAuthBrowserLaunch(ready, onLaunchFailed = { failures += 1 }) { opened += it }

        assertEquals(listOf(ready), opened)
        assertEquals(0, failures)
    }

    @Test
    fun aBrowserThatCannotOpenIsReportedOnce() {
        var failures = 0

        dispatchAuthBrowserLaunch(ready, onLaunchFailed = { failures += 1 }) {
            error("no browser")
        }

        assertEquals(1, failures)
    }

    @Test
    fun notStartedOpensNothingAndReportsNothing() {
        var opened = 0
        var failures = 0

        dispatchAuthBrowserLaunch(
            AuthBrowserLaunchResult.NotStarted,
            onLaunchFailed = { failures += 1 },
        ) { opened += 1 }

        assertEquals(0, opened)
        assertEquals(0, failures)
    }
}
