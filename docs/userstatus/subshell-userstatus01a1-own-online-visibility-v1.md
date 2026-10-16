# USERSTATUS01A1 — Own Reddit online-visibility preference

## Evidence and scope

Source discovery: local canonical main `1a5d8a3b5f9db9fc09b0ff5900de311673fb4ccb`.
Reddit documents `show_presence` as a boolean account preference. Reading uses
GET `/api/v1/me/prefs?fields=show_presence` with `identity`; updating uses
PATCH `/api/v1/me/prefs` with `account` and a JSON body containing only this field.
Both scopes are already in Subshell's daily-driver account grant. This slice does
not expand that grant or modify the existing authorization-owner dispatch.

Sources checked 2026-09-16:
- https://www.reddit.com/dev/api/#GET_api_v1_me_prefs
- https://www.reddit.com/dev/api/#PATCH_api_v1_me_prefs
- https://praw.readthedocs.io/en/stable/code_overview/other/preferences.html

PRAW explicitly warns that invalid preference input can fail silently. Therefore
HTTP success alone is not accepted as proof that a value has been saved.
The current logged-in Reddit web placement and other users' live-status API have
not been verified. This is an own-account preference implementation candidate,
not proof of present-day web placement or proof that the account is online.

## Presentation

The Home avatar opens a compact account switcher only: authenticated account rows
and a Manage accounts entry. It does not host preference controls, Add/Logout
action cards, or the online-visibility control. Manage accounts opens the dedicated
Settings → Accounts destination.

Settings → Accounts owns account switching, Add Reddit account and Log out. Its
Account privacy section contains one compact Show online status row for the active
authenticated account. The normal known boolean state shows only the switch and
short account-scoped supporting text; there is no permanent Refresh action. Retry
is presented only when the value is unknown/unavailable or a read/change needs
reconciliation. Settings → Accounts → Connection also owns RedReader compatibility;
Advanced is limited to developer tools and diagnostics.

Only a successfully read boolean produces a Show online status switch. Loading,
missing/null/non-boolean data, configuration errors and failed reads never turn
into an unchecked switch. All such values are unknown, not false. A confirmation
dialog names the account and desired visibility before every write. Cancel and
Back send no preference update. The switch is not projected optimistically.

## Transport and ownership

The new typed JSON-PATCH helper uses the existing shared HTTP client's encoding,
response-size limit, media-type validation, cancellation and error classification.
The feature uses the existing non-retrying, non-redirecting write client for both
reads and writes and a bearer token pinned to the validated account session.

Reading requires only identity. Writing requires identity and account. The session
provider reuses ensureAccessTokenForAccount; the service checks provider, client
mode, client fingerprint, granted scopes and identity-derived stable account ID.
Active-account and operation-generation fences are rechecked after suspension.
The source's existing Sign in again flow is used when reauthentication is needed.
Opening Accounts settings after reconnect reads again; no authorization callback ever
replays a preference write. There is no new preference-specific OAuth owner.

## Mutation transaction

A confirmation contains account ID, expected old value, absolute desired value and
UI revision. It is consumed before async work; duplicate confirmations do not
queue another request. A non-queuing service mutex independently guards writes.

Each change performs GET preflight, at most one PATCH, and GET readback. A changed
preflight value is displayed for review without sending PATCH. Missing/invalid
preflight data also prevents PATCH. True and false are explicit JSON booleans.

A matching PATCH response plus matching readback is Saved and checked. If the
PATCH response was ambiguous (including HTTP 500) but GET now reports the desired
value, the UI states that Reddit currently reports that value without claiming a
clean save acknowledgment. If readback does not prove the desired state, the
outcome remains unknown. No success, rollback or opposite state is invented.
Refresh performs only GET and is required before another change after an unknown
outcome. There is no automatic write retry, queued write, offline replay or
stored desired value. A server change after the preflight cannot be prevented
atomically by this API; the final readback reports what was observed.

## Explicit exclusions

No online indicator for other users, no heartbeat, no last seen, no Matrix presence,
no Seen/read receipts, and no Direct Chat video. Existing chat features are
untouched. No Room schema change, SharedPreferences entry, backup field or cloud
sync is introduced. Neither preference bodies nor tokens are logged.

## Qualification boundary

The candidate includes JUnit 4 coordinator, transaction, network and presentation
checks. Host-only state/transaction checks use mocked dependencies; they are not
an Android build or live Reddit proof. The preview runner performs the real
focused JVM tests, canonical contract-delta gate, full JVM suite and DEV2 APK build
on the developer machine. It checks APK package identity before an in-place DEV2
install. It neither checkpoints, lands, pushes nor sends a preference write.

Runtime acceptance remains pending: read this account's real value, confirm that
Cancel is inert, explicitly choose a change when desired, verify readback, and
check the setting from Reddit or reopened Accounts settings. Enabling visibility
must never be performed automatically merely to test the feature.


## Account identity presentation

The compact `You → Accounts` switcher and `Settings → Accounts` use the
existing visible-user identity enrichment path. When Reddit supplies an avatar
or snoovatar image it is displayed. If no usable image is available, Subshell
renders the seeded stock Reddit profile avatar.

This presentation does not add a separate profile transport or a new Reddit
endpoint. `Settings → Accounts → Log out` retains the Material destructive
`errorContainer` / `onErrorContainer` role so account removal remains visually
distinct from switching or navigation.
