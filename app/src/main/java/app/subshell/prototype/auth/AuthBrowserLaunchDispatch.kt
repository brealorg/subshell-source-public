package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthBrowserLaunchResult

/**
 * The one shape every authorization launch shares: a Ready result opens the
 * browser, a failure to open it is reported back once, and NotStarted does
 * nothing. MainActivity supplies [open] (the auth tab launch) and the
 * feature's own launch-failed callback.
 */
internal fun dispatchAuthBrowserLaunch(
    launch: AuthBrowserLaunchResult,
    onLaunchFailed: () -> Unit,
    open: (AuthBrowserLaunchResult.Ready) -> Unit,
) {
    when (launch) {
        is AuthBrowserLaunchResult.Ready -> {
            runCatching {
                open(launch)
            }.onFailure {
                onLaunchFailed()
            }
        }

        AuthBrowserLaunchResult.NotStarted -> Unit
    }
}
