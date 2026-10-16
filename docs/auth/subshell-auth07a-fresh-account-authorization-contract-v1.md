# subshell AUTH07A fresh account authorization contract v1

AUTH07A separates account connection from capability reauthorization at the browser-session boundary.

## Account connection

The canonical `AuthConnectFlow` creates an `AuthBrowserLaunchResult.Ready` with `freshAccountSession = true`.

For RedReader compatibility this causes the dedicated Subshell login WebView to clear its own WebView cookie and web-storage session before loading Reddit authorization. Reddit OAuth tokens stored by Subshell are not removed or changed.

This behavior applies to first account connection and `Add Reddit account`.

## Existing-account capability authorization

Comment, post, inbox, composer, custom-feed and other capability authorization results retain the default `freshAccountSession = false`.

Those flows therefore preserve the existing WebView login session and the existing account-pinning, owner-routing and callback-validation architecture.

## Security and storage boundaries

AUTH07A does not remove authenticated Reddit sessions, account-directory entries, OAuth client configuration, Room data, or capability grants.

The RedReader Client ID remains in the existing encrypted no-backup configuration store. No Client ID is bundled in source or BuildConfig.

AUTH07A does not yet add first-run onboarding. That follows in AUTH07B after the fresh-account runtime behavior is proven on device.

## AUTH07A1 non-blocking initial feed warm-up

OAuth token exchange and Reddit identity resolution establish the authenticated account.

The initial Home feed synchronization is cache warm-up rather than an account-validity condition. A successfully applied empty Reddit listing is valid, and a warm-up failure or unexpected synchronization exception must not discard an otherwise valid authenticated Reddit account.

Normal feed loading owns subsequent retry, offline and error presentation after connection.

## AUTH07A2 live account persistence

Account registration can add a new authenticated session after the Compose state object has already been created.

Active-account persistence must therefore resolve account metadata from the live `AccountSessionDirectory` snapshot when that directory exists. The account list captured during initial `rememberSubshellState` construction is only a fallback for configurations without an authoritative session directory.

This prevents a newly registered Reddit account from being rejected by stale UI-state persistence after a successful OAuth callback.
