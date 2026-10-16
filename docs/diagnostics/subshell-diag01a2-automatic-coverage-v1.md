# DIAG01A2 — automatic diagnostic coverage v1

DIAG01A2 extends the DIAG01A1 structured diagnostic foundation from a small set of explicit producers into the application's established typed error boundaries. It remains an app-owned structured-event system, not a logcat collector.

## Coverage rule

Automatic diagnostics are produced only at known, typed, user-visible or background failure boundaries. Raw Android `Log.*` output is deliberately **not** copied into the persistent diagnostic buffer. The source audit found many historical debug logs, some of which contain account/item identifiers or implementation-level detail; treating them as a diagnostic data source would weaken the DIAG01 privacy boundary and create high-volume noise.

DIAG01A2 v1 covers:

- existing `ReportViewportError` degraded/cached read failures from DIAG01A1;
- explicitly scoped `ErrorNotice` blocking/inline/degraded failures across Home/feed, Search, comments, Inbox, communities, profile, message reply, and Reddit write/manage surfaces;
- user-visible image, GIF, and video failure placeholders;
- Inbox and Modmail background synchronization failures;
- Inbox/Modmail notification-post failures caused by permission/security or local posted-state persistence failure.

The `ErrorNotice` diagnostic route is explicit opt-in through a fixed `diagnosticScopeKey`. It does not infer a subsystem from rendered strings and does not persist titles, messages, Reddit content, usernames, queries, community names, post IDs, comment IDs, conversation IDs, or account stable IDs.

## Typed presentation bridge

`ErrorPresentation.withFailureDetails()` retains the already-structured `FailureDetails` object in memory in addition to the existing sanitized technical-detail text. This lets the DIAG01 reporter build `DiagnosticFailureSnapshot` directly from typed fields.

Persistent diagnostics still exclude `FailureDetails.providerMessage`. Only the existing allowlisted fields may be written:

- HTTP status;
- retry delay and retryability;
- transport/protocol/local kind;
- bounded provider code;
- bounded media code.

The fixed scope key is transformed through the installation-local HMAC-SHA256 scope fingerprint before persistence, exactly as in DIAG01A1.

## Presentation severity

Presentation impact is part of severity classification:

- degraded/cached-content presentation → `WARNING`;
- blocking presentation → `ERROR`;
- dialog presentation → `ERROR`;
- inline presentation → `WARNING` unless the structured failure is explicitly non-retryable, then `ERROR`.

Repeated identical viewport or `ErrorNotice` presentation events are deduplicated inside the existing five-second presentation window.

## Background severity

Background and notification failures use structured `FailureDetails` when available:

- retryable or unknown-retryability operational failure → `WARNING`;
- background authorization requirement → explicit `BACKGROUND_AUTHORIZATION_REQUIRED` `WARNING`;
- explicitly non-retryable configuration/storage/permission failure → `ERROR`.

Background diagnostics never use the account stable ID as a scope. Fixed scopes such as `inbox_background_sync`, `modmail_background_state`, `inbox_notification_post`, and `modmail_notification_post` are HMAC-fingerprinted before persistence.

## Subsystem precision

DIAG01A2 adds `SEARCH` and `POSTS` as explicit diagnostic subsystems. Fixed scope mapping gives Search failures their own subsystem instead of folding them into Feed, while post create/edit/delete/manage failures no longer fall back to generic UI.

Subsystem inference is surface-first and position-aware. DIAG01A2 first finds explicit feature/surface markers in the composite scope and uses the earliest such marker; only when no feature marker exists does it fall back to contextual `auth`/`oauth`/`account` or network markers. This makes `home-feed:account:…` and even `account:home-feed:…` resolve to Feed, `backup:account:settings` resolve to Backup, and `settings:account:notifications` resolve to Settings, while a genuinely auth-owned scope such as `oauth_account_refresh` still resolves to Auth. The classifier therefore reflects the user-visible feature that owns the failure rather than incidental nested context.

Compound surfaces can declare a more specific owner at the same scope position. The post thread reports degraded comment refreshes with `post-comments:<post>:<sort>`; DIAG01A2 therefore declares `post-comments` as Comments. The existing longest-token tie break makes that compound marker win over the generic `post` marker at index zero without changing Profile, Community, Search, Feed, or other container-owned scopes.

## Deliberate exclusions in this slice

DIAG01A2 v1 does not claim complete application coverage. The source audit also found custom one-off notices, client-ID onboarding supporting text, low-value Toast/share failures, and numerous raw development/performance logs. Those are not silently ingested. They require an explicit typed decision before being added so diagnostics do not become a second logcat.

User-initiated incident markers (`Report a problem` for bugs that the app did not detect) remain DIAG01A3 work.

## Support destinations

GitHub Issue prefill, email attachment routing, and generic Android share remain the DIAG01A1 support-destination model. Canonical GitHub/e-mail destinations remain intentionally unconfigured in this preview.

The GitHub issue endpoint is constructed through JVM-safe `java.net.URI` rather than Android `Uri.Builder` or a raw web-URL literal. This preserves the existing source-isolation boundary, keeps local JVM policy tests executable, and does not change the generated GitHub issue URL or its bounded prefill payload.
