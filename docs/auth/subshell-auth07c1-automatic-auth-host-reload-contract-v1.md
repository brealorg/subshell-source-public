# AUTH07C1 automatic auth host reload contract v1

## Goal

Reddit client configuration changes must become usable without asking the
user to manually restart Subshell.

## Existing configuration storage

AUTH07C1 preserves the existing
`RedditCompatibilityConfigurationController` and
`EncryptedAuthSessionStore` boundary.

The user-supplied OAuth client ID remains encrypted in no-backup storage.
AUTH07C1 does not move the client ID into preferences, Room, logs,
BuildConfig, backup data, source, or UI process state.

## Runtime ownership

`AuthConnectViewModel` intentionally retains its immutable
`AuthApplicationRuntime` graph.

The runtime owns the account directory, connect flow, feed updater, Reddit
loaders, capability authorizers, mutation transports, and the coordinators
constructed from those dependencies.

AUTH07C1 does not attempt to hot-swap that graph inside a running
ViewModel.

## Automatic reload boundary

After a successful Reddit compatibility configuration save or remove,
Settings calls the host callback.

`MainActivity` starts a fresh `MainActivity` instance and finishes the old
host. The new Activity receives a new `AuthConnectViewModel`, which creates
a new canonical `AuthApplicationRuntime` from the encrypted configuration.

The implementation does not kill the process and does not require the user
to close, force-stop, or manually restart Subshell.

A failed configuration mutation does not reload the host.

## Product behavior

Settings no longer tells the user to restart Subshell after saving or
removing the Reddit client configuration.

The temporary RedReader compatibility management surface remains available
until AUTH07C2 replaces the normal first-run path with product onboarding.

## AUTH07C2 seam

AUTH07C2 can derive first-run state from actual configuration and account
state.

No durable `onboarding_completed` flag is introduced by AUTH07C1.

The intended product sequence is:

`Paste Client ID -> automatic host reload -> Ready to connect -> Reddit OAuth`

## Boundaries

AUTH07C1 does not change OAuth scopes, stored Reddit sessions, account
identity, Reddit endpoints, Room schema, or the daily-driver grant policy.

Existing authenticated accounts are not reauthorized by a configuration
host reload.
