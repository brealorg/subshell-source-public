# SAFETY01A3.1 — content-safety viewport error crash fix

## Runtime failure

SAFETY01A3 runtime acceptance exposed a deterministic process crash after returning from the first-use Reddit authorization flow.

The crash stack was:

`ViewportErrorHostState.present(ViewportErrorHost.kt:42)`

`ViewportErrorHostState` intentionally accepts only `ErrorPresentationKind.DEGRADED`. A3 routed `contentSafetyErrorPresentation(...)` through `ReportViewportError`, but the new presentation was built with the shared write helper, whose kind is `INLINE`.

That violated the viewport-host contract exactly when Content Safety entered `FAILED`, so the error presentation itself crashed the process before the underlying authorization/write failure could be shown.

## Correction

`contentSafetyErrorPresentation` now constructs a `DEGRADED` presentation directly, matching the established post-save and post-vote viewport error pattern.

Retry semantics remain unchanged:

- retry is exposed only for retryable failures;
- `OUTCOME_UNKNOWN` report failures never offer blind retry;
- dismiss remains available;
- structured `FailureDetails` remain attached.

No Content Safety transport, OAuth, callback, Hidden reconciliation, optimistic state, or product action semantics change in A3.1.

## Build policy

A3 already passed AndroidTest compilation before this runtime failure. A3.1 changes only the presentation kind plus a JVM policy test, so the corrective runner does not repeat AndroidTest compilation. It runs the successor contracts, the focused JVM regression test with the redundant global Gradle contract dependency excluded, then the canonical DEV2 build/install, which retains the repository's normal mandatory build gates.

## Runtime acceptance after A3.1

Repeat the original A3 runtime flow. If the underlying first-use authorization fails, the app must remain alive and surface the Content Safety error instead of crashing. If authorization succeeds, the pending Hide/Unhide action should resume normally.
