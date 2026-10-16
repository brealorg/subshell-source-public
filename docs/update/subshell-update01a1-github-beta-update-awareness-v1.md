# UPDATE01A1 — GitHub beta update awareness foundation

UPDATE01A1 introduces a release-provider boundary for Subshell's current
GitHub-distributed beta without adding Google Play Core or a self-installer.

## Provider

The beta client reads one bounded HTTPS JSON document:

`https://brealorg.github.io/subshell-update-beta.json`

Schema v1 contains:

- `schemaVersion = 1`;
- `channel = beta`;
- positive `versionCode`;
- bounded `versionName`;
- bounded publication timestamp text;
- one HTTPS GitHub release URL under `brealorg/subshell`;
- a 64-character APK SHA-256 value;
- one bounded headline and at most six bounded highlights.

The client does not consume a direct APK URL and does not install packages.
The later UI opens the GitHub release in the browser.

## Runtime identity

Update network/runtime behavior is enabled only for the non-debug production
application ID `io.github.brealorg.subshell`. DEV1, DEV2 and ordinary debug
builds do not contact the public update manifest and cannot post public beta
update notifications.

Google Play Core is deferred until Play distribution exists.

## Scheduling

The app schedules one independent connected WorkManager job every 12 hours.
Every start of the activity (a launch or a return from the background)
additionally enqueues a connected one-time check when the last completed check
is at least one hour old (UPDATE07, owner 2026-10-15; it was six hours and
cold starts only).

The update scheduler is deliberately independent from authenticated Reddit
accounts and the Inbox notification preference. Updates remain useful when the
user has no Reddit account or has disabled Inbox background sync.

## Notification policy

UPDATE01A1 owns a dedicated Android notification channel named `App updates`
with default importance. A remote release is notified only when its
`versionCode` is greater than the installed `BuildConfig.VERSION_CODE` and
greater than the last version code already notified.

The feature never requests `POST_NOTIFICATIONS` merely because update checks
exist. If Android notification permission is unavailable, the check still
stores the available release for later in-app presentation and posts nothing.

UPDATE08 (owner, 2026-10-15): checks still never ask on their own, but while
notifications are not allowed the update dialog offers "Notify me next time".
It asks Android for `POST_NOTIFICATIONS` while Android still asks (first time,
or with a rationale after one refusal), and opens Subshell's notification
settings otherwise (a final refusal, or notifications turned off for the app).
The "asked once" flag is device-local (`update08.notification_permission_asked`).

UPDATE09 (owner, 2026-10-16): a known-bad-version advisory. The top release in
`app/src/main/assets/changelog/releases.json` may carry

```json
"advisory": {"affectedVersionCodes": [245], "message": "0.81 can lose a draft when …"}
```

The message is at most 240 characters. The list holds at most 32 versions, each
older than the release; RELEASETOOL02B stops the release on anything else. Stage B
copies the advisory into the manifest as an optional `advisory` field, and
`schemaVersion` stays 1. Builds before 0.82 ignore the field and see a plain
update. From 0.82, a reader on a listed version sees "Known problem in your
version" with the message first in the dialog, and the notification names the
problem. The dialog comes back at most once a day until they update, while a
plain update is offered once. The last time the dialog was shown is device-local
(`update09.last_update_presented_at_epoch_ms`). An advisory only travels with a
new release; there is no way yet to publish one without a release.

## Device-local state

All update/check/acknowledgement state uses the existing
`subshell_device_preferences` file. Existing Android backup and device-transfer
rules already exclude this file.

The store owns:

- last observed installed version code;
- pending What's New version code;
- last acknowledged changelog version code;
- last completed update check time;
- last notified remote version code;
- the latest validated available-release projection.

None of these values participate in portable Settings Backup & Restore.

## Installed-version semantics

`ACTION_MY_PACKAGE_REPLACED` is the authoritative signal that this package was
updated. The receiver marks the current version as pending What's New even when
UPDATE01 did not exist in the previous installed version. This makes the first
upgrade that introduces UPDATE01 work correctly.

A fresh install does not receive `MY_PACKAGE_REPLACED`. On first process start,
an absent last-observed value initializes to the current version without
creating a What's New event.

Process-start version comparison remains a fallback when the installed version
increases and the receiver-created pending marker is absent.

## UPDATE01A1B presentation and bundled changelog

`UPDATE01A1B_WHATS_NEW_CHANGELOG_PRESENTATION_V1`

A validated available release is presented in-app at most once per remote
version code on the main shell. Android update-notification taps use the same
presentation on cold and warm `MainActivity` delivery. The notification intent
does not become a trust boundary: the UI reuses the validated release cached in
`subshell_device_preferences`.

After a real package replacement, a bundled changelog entry matching the
installed version is shown once as **What's new in Subshell**. Dismissing it,
choosing **Got it**, or opening the full changelog acknowledges that installed
version in device-local state. Fresh installs continue to produce no What's New
event.

The full changelog is bundled under `app/src/main/assets/changelog/releases.json`
and is readable without network access. Its schema, release count, text lengths,
URLs and byte size are bounded and fail soft. The shared **Help & about** screen
is the permanent changelog surface: release history is rendered inline directly
below the app/version summary in the same scrollable page. Each release uses a
Material 3 card (`surfaceContainerLow`, `shapes.large`) with an internal divider
between the release summary and highlights. This preserves strong visual grouping
without adding a separate navigation step. Both real
product entrypoints therefore show the same content: Settings -> Help & about
and the main side-menu Help & about destination. Settings search terms for
changelog/release notes resolve to this existing About destination; no ninth
root destination is added. The standalone changelog screen is retained only for
the explicit **View changelog** action in the one-shot What's New dialog.

The initial historical entries are copied from the actual published GitHub
release notes for `0.77.0-beta.1` and `0.77.0-beta.2`. No unreleased changelog
entry is guessed. A later release-tooling slice must require the release being
published to already have its matching bundled entry.

DEV builds retain public update-network isolation. Debug-only explicit intent
actions can preview the Update available and What's New surfaces using local
data without enabling public beta polling.

Publication of the public update manifest remains deferred to UPDATE01A1C.

No `REQUEST_INSTALL_PACKAGES`, Play Core dependency, direct APK downloader, or
custom package installer is introduced.

### A1B1 presentation hardening

Automatic update and installed-release presentation is deferred until the
current navigation route is the main **Home** destination. Explicit forced
presentation requests (notification/debug entry) are not Home-gated, but all
update presentation is deferred while the backup-folder onboarding dialog is
visible. The onboarding surface reports visibility to the shared shell owner.

Explicit update handling also removes a remembered automatic candidate for the
same version before clearing the explicit request, preventing duplicate
presentation. Empty debug What's New cleanup is owned by `LaunchedEffect`
rather than mutating coordinator state during Compose composition.

### A1B2 Material 3 changelog cards

The permanent **Help & about** changelog remains inline. Individual releases use
Material 3 cards with `surfaceContainerLow`, `shapes.large`, 18 dp content
padding, an internal divider before highlights, and the existing low-emphasis
**View GitHub release** text action. There is no Help -> Changelog navigation
step.

## UPDATE04 manifest trust and health (2026-09-27)

- The update dialog shows the manifest's `apkSha256` (selectable), so a
  careful user can compare it with the downloaded APK. The manifest itself
  stays unsigned; its release link is pinned to
  `https://github.com/brealorg/subshell/releases/tag/v<versionName>`.
- `releaseUrl` must be exactly `.../releases/tag/v<versionName>` with no
  query or fragment; the release tooling already writes it that way
  (`promote-public-site-manifest.py`: `TAG == v<VERSION_NAME>`).
- Help & about says "Update check isn't working" after three days of checks
  that produced no usable manifest (failed fetch, rejected manifest).

### Manifest compatibility rule (for the release tooling)

Every installed build reads `subshell-update-beta.json` with schema 1 and
silently rejects anything else. Therefore:

1. Never change the meaning of a schema-1 field, and never remove one.
2. A new manifest format goes into a new file next to the schema-1 file,
   never in its place. Keep publishing the schema-1 file until no supported
   build reads only schema 1.
3. Never move the manifest URL or answer it with a redirect: the client does
   not follow redirects, so old builds would stop seeing updates.
