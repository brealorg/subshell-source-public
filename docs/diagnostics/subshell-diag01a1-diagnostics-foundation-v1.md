# DIAG01A1 — Diagnostics foundation

DIAG01A1 introduces an always-on, app-owned diagnostic buffer and a user-visible Diagnostics surface under **Settings → Advanced → Diagnostics**.

## Product boundary

- Diagnostics are available in normal application builds; collection is not gated behind `BuildConfig.DEBUG`.
- The event store lives under `noBackupFilesDir/diagnostics` and is not part of settings backup/restore.
- The buffer is bounded by both record count and byte size: 1000 events and 1 MiB by default.
- Oldest records are evicted first. The store preserves a cumulative dropped-event count.
- Shareable reports are staged only in `cacheDir/diagnostics_reports` and exposed through the existing app-scoped `FileProvider`.
- Clearing diagnostics removes the event history but preserves the install-local scope pseudonymization salt.

## Privacy boundary

DIAG01A1 is a structured-event system, not a logcat collector.

The persisted event model has no generic string map and no fields for Reddit post/comment/message bodies, request bodies, response bodies, URLs, usernames, OAuth tokens, cookies, authorization codes, passwords, or raw account identifiers.

Failure events may preserve only bounded structured fields already represented by `FailureDetails`: HTTP status, retry timing/eligibility, transport/protocol/local failure kinds, provider code, and media code. `providerMessage` is deliberately not persisted even though the ERROR01 sanitizer already bounds and redacts it.

Raw viewport scope keys are never persisted. They are classified in memory and correlated using HMAC-SHA256 with a random 32-byte key stored locally under `noBackupFilesDir/diagnostics`. Only a 16-hex-character truncated fingerprint is persisted or exported, preventing cross-install correlation.

## A1 producers

DIAG01A1 records:

1. application surface session start;
2. degraded viewport errors routed through the existing `ReportViewportError` host;
3. opening the Diagnostics screen;
4. creation of a diagnostic report.

Repeated identical viewport failures inside a five-second window are deduplicated.

## Diagnostics UI

DIAG01A1R1 applies the Material 3 runtime qualification to the diagnostics surface. The UI is intentionally a product surface rather than a developer console.

The screen shows:

- a glanceable **Diagnostics active** status surface with compact event, storage and retention metrics;
- one visually dominant **Create diagnostic report** action;
- compact **Private by design** disclosure with expandable privacy detail;
- severity FilterChips with an animated selected shape;
- a single subsystem selector that opens a Material bottom sheet instead of an arbitrarily scrolling chip rail;
- a denser newest-first event history using connected expressive shapes and semantic severity containers;
- report preview in a tall Material bottom sheet with a dedicated **Share report** action;
- transient operation feedback through a snackbar rather than permanent status text;
- clear-history in the top-app-bar overflow with a confirmation dialog so the destructive action does not compete with report creation.

The R1 presentation must remain adaptive to narrow phones and wider layouts. A subsystem list is never represented as a horizontally clipped scrolling row.

## Report contents

The report contains bounded non-secret environment information (app version/build, Android API/release, device model class, and screen dp dimensions), buffer metadata, privacy declarations, and the structured event projection.

No system logcat, Reddit content, username, raw scope, auth credential, cookie, or request/response body is collected for the report.

## Successor scope

Later DIAG01 slices may add more typed producers, correlation from error-details actions, optional time-bounded detailed diagnostics, and process-exit information. Those additions must preserve this persistence-time allowlist model and must not turn the store into an arbitrary text logger.

## R2 polish and support routing

DIAG01A1R2 keeps the R1 Material direction while increasing information density and tightening diagnostic semantics.

- The status dashboard uses a consistent value/label hierarchy: current event count, stored bytes, and `On` / `Auto-prune` retention state.
- The subsystem selector is a compact single-row filter surface. The subsystem list still opens as a Material bottom sheet; there is no horizontally clipped subsystem chip rail.
- Event rows use reduced vertical padding and smaller severity affordances so a phone viewport can expose more history without sacrificing the connected-shape grouping.
- Report preview keeps each technical `key=value` line intact with soft wrapping disabled and explicit horizontal scrolling inside the preview surface.
- Runtime viewport failures use severity semantics rather than treating every failure alike: retryable viewport failures are `WARNING`; explicitly non-retryable viewport failures are `ERROR`; ordinary lifecycle/observation/success events remain `INFO`.

Support delivery is deliberately destination-agnostic. The report layer now models optional email and GitHub destinations while generic Android sharing remains available at all times. Canonical support destinations are optional and remain unconfigured until the project chooses a real support email address and GitHub repository.

When configured later, a prefilled GitHub Issue is opened in the browser with bounded app/device environment metadata and prompts for reproduction steps. The GitHub URL builder does not accept the diagnostic report file or report text, so event history is never embedded automatically into a public issue URL. The user may attach the `.txt` report manually if appropriate. Email support, when configured, sends the staged `.txt` report as an attachment. Generic **Share another way** remains the fallback.
