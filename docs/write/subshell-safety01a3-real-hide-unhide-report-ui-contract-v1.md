# SAFETY01A3 — real Hide/Unhide and Report UI

## Product result

SAFETY01A3 replaces the remaining local/preview safety actions with the owner-bound
SAFETY01A2 Reddit writer.

Post overflow now exposes a stateful `Hide post` / `Unhide post` action. Authenticated
posts use `POST /api/hide` and `POST /api/unhide`; local fixture accounts retain only
their local preview behavior.

Post and comment overflow now expose a real `Report` action. The shared Material 3
report dialog collects a bounded free-text reason and submits it through
`RedditContentReportRequest`. The previous `Report flow preview` toast is retired.

## Hidden source of truth

For authenticated accounts, the old persisted `hiddenPostIds` preference is no longer
loaded or persisted as authoritative state. It is retained only as an in-memory
presentation projection for immediate hide/unhide behavior.

`You -> Hidden` and the legacy Library Hidden destination remain backed by the existing
Reddit `/user/{username}/hidden` history engine. A post rendered from that authoritative
history is explicitly marked as hidden for the PostCard action state.

After a successful SAFETY01A2 mutation whose Hidden reconciliation is `REFRESHED`,
Subshell reloads the existing Hidden pager from the cache refreshed by the writer. This
avoids a second safety truth and avoids requiring another network mutation.

## Optimistic behavior and rollback

Hide/Unhide uses the same product rule as vote/save: the visible action state changes
immediately while authorization or transport is in progress. A failed mutation restores
the previous in-memory hidden projection.

A successful post report records the Reddit-documented implicit hide in the presentation
projection. A comment report has no post-visibility side effect.

## Authorization and errors

`CONTENT_SAFETY` authorization is launched automatically when A2 returns
`AUTHORIZATION_REQUIRED`. The same callback owner resumes the pending operation.

Failures are surfaced through the existing persistent viewport error host.
`OUTCOME_UNKNOWN` report failures are explicitly non-retrying because blind report
replay may duplicate a report.

## Report reason UI

A3 deliberately uses a bounded free-text reason rather than introducing a second
community-rules transport. COMMUNITYINFO01 rules can be used by a later report-reason
picker without changing the A1/A2 wire boundary.

## Exclusions

- no Room schema migration;
- no direct comment Hide/Unhide;
- no app-wide overflow-menu icon audit;
- no block/mute implementation;
- no remote operations, checkpoint, or landing in this phase.
