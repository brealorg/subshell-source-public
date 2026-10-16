# subshell PROFILE01A3 account session UI contract v1

PROFILE01A3 removes development account artifacts from the product-facing account switcher while preserving the existing authenticated account directory, OAuth storage, account selection, and local fixture test architecture.

## Product account list

The account switcher renders only `AccountSessionSource.AUTHENTICATED` sessions.

Local fixture accounts remain available to internal tests and local fallback architecture, but are not presented as switchable Reddit accounts in normal product UI.

The active authenticated account is labelled `Current account`.

An authenticated session requiring reauthentication remains visible as `Sign in again`. The current session uses `Current account · Sign in again`.

## Connect versus add

`ConnectAccountPhase.IDLE` means that the authorization flow is idle. It does not mean that no authenticated Reddit account exists.

When the account directory already contains an authenticated session, the idle action is therefore `Add Reddit account`.

When no authenticated session exists, the action is `Connect Reddit account`.

The old local-fixture and security diagnostic copy is not rendered in the product account sheet.

## Boundary

PROFILE01A3 does not add logout, account removal, token revocation, OAuth scope changes, Reddit endpoints, Room schema changes, or cache changes.

Existing stable-ID account selection and encrypted session ownership remain unchanged.
