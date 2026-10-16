# DIAG01A3 — user-initiated incident reporting v1

DIAG01A3 adds a deliberate user signal for problems that the application cannot reliably detect itself, such as a stuck loading state, incorrect-looking data, a no-op interaction, or a visual/layout glitch.

## Product flow

The existing **Settings → Advanced → Diagnostics** surface now includes **Report a problem**.

Selecting it performs two ordered actions:

1. persist a typed `USER_INCIDENT_MARKED` event for the current moment;
2. create and stage the normal diagnostic report so the existing preview/share flow opens immediately.

The marker is a `WARNING` in the `UI` subsystem with `OBSERVED` outcome. It intentionally carries no failure payload and no scope fingerprint. Its purpose is temporal correlation: automatic A1/A2 events immediately before the marker can be inspected alongside the moment the user noticed a problem.

## Privacy boundary

The user-incident action does **not** collect:

- free-form problem descriptions;
- screenshots or screen recordings;
- raw logcat;
- Reddit post/comment/message bodies;
- usernames, community names, queries, post/comment/conversation IDs;
- OAuth tokens, cookies, authorization codes, passwords, or raw account identifiers.

The user can add a human description later in the chosen external support destination. Subshell itself persists only the typed incident marker.

## Report preview readability

The in-app report preview wraps long monospaced report lines to the available phone width and scrolls vertically only. Event records must not disappear beyond the right edge or require a second-axis pan gesture to read. This changes presentation only: the staged/shared report bytes remain the canonical full report and are not rewritten or truncated by the preview.

The report preview sheet itself is intentionally non-draggable. The inner report body owns vertical scroll gestures all the way to its boundaries, so an overscroll at the top or bottom cannot move or dismiss the sheet. The explicit close button, system back action, and normal dismiss request remain the ways to leave the preview.

## Existing diagnostics reused

DIAG01A3 does not introduce another logging or reporting system. It reuses the DIAG01A1 bounded app-owned event store, report generator, cache-only report staging, preview sheet, and existing GitHub/e-mail/generic Android share destinations. DIAG01A2 automatic failure coverage remains unchanged.

The existing limits remain authoritative: 1000 events and 1 MiB by default, oldest-first eviction, no backup of the diagnostic store, and no raw logcat ingestion.
