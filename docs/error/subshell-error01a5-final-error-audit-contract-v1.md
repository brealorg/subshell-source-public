# ERROR01A5 — final error audit and enforcement contract

ERROR01A5 closes ERROR01 by auditing the active application and strengthening the build-time contract so structured error causes cannot silently regress back to raw or string-only delivery.

## Audit scope

- Canonical `RepositoryResult.Error` / `Offline` and `RepositoryLoadState.Error` / `Offline` carry optional `FailureDetails` alongside feature-owned readable copy.
- Repository paging and persistent-cache wrappers preserve those details instead of flattening them while stale content is retained.
- Reddit identity metadata adapts `NetworkResult` failures through `failureDetailsOrNull()` and classifies local cache write failures as storage failures.
- Generic paging UI may retain feature-owned copy, but any technical cause is rendered only through `FailureDetails.toSafeTechnicalDetail()`.
- Active comment/post edit UI state no longer carries parallel provider-code/message strings; sanitized provider metadata is owned by `FailureDetails`. Transport-facing compatibility fields may remain below the UI boundary.
- Local mutation replay does not copy `Throwable.message`; fixed local diagnostics are used instead.

## Security and redaction

The ERROR01A1 redaction boundary remains authoritative. Provider text, callback-like URLs, authorization headers, cookies, OAuth codes/state and token-shaped key/value data must pass through `SafeFailureText` before they can become technical detail. Raw exception text, response bodies, request URLs and stack traces are not user-facing error data.

## Contract truthfulness

`tools/check-source-contract.sh` treats every invoked child contract as required. A child non-zero exit marks the aggregate source contract failed; the aggregate may not print a PASS result while a nested checker has emitted a failure. Historical predecessor checkers remain successor-aware so later versions are validated against retained semantics rather than frozen version strings. ERROR01 and AUTH08 predecessor gates accept the final ERROR01A5 successor, and child checkers that validate source-contract wiring recognize the required-contract helper rather than a frozen literal runner line.

## Allowed strings

Feature-owned validation copy, empty-state reasons, test-fixture diagnostics and fixed local messages may remain strings. ERROR01A5 does not require every internal sentence to become `FailureDetails`; it prohibits strings from being the only canonical carrier of an available technical failure cause.

## Completion boundary

ERROR01A1 through ERROR01A5 together cover the shared presentation foundation, Reddit reads, persistent viewport delivery, adaptive details, media/backup, Reddit writes/mutations and the final broader audit. New error-producing features must preserve structured causes and use the shared sanitization/presentation boundaries rather than adding raw exception/provider rendering.
