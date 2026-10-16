# ERROR01A1 — user-facing error foundation

ERROR01A1 establishes a shared error-detail and Material presentation boundary without migrating existing feature error flows.

## Scope

- Existing feature/domain `*FailureKind` types remain authoritative.
- `FailureDetails` preserves safe HTTP, retry, transport, protocol, provider, and local failure metadata.
- `NetworkResult.failureDetailsOrNull()` provides a canonical lossless adapter for the shared network boundary.
- Provider text enters `FailureDetails` only through bounded sanitization.
- `ErrorPresentation` owns user-facing title, explanation, safe technical detail, semantic presentation kind, and action descriptors.
- Presentation models never own callbacks.
- `ErrorNotice` covers blocking, degraded/cached-content, and inline Material presentation.
- `ErrorPresentationDialog` covers transactional/dialog failures.

## Security boundary

The shared failure-detail boundary must never expose access tokens, refresh tokens, ID tokens, client secrets, OAuth authorization codes/state, cookies, Authorization header values, or raw callback credentials. Untrusted provider text is flattened, redacted, and bounded before it can be used as safe technical detail.

Raw `Throwable.message`, request URLs, response bodies, callback payloads, and stack traces are not part of the ERROR01A1 presentation contract.

## Deferred work

ERROR01A1 deliberately does not migrate Home, comments, Inbox, subscriptions, Explore, profile comments, media, backup, composer, or mutation surfaces. Those migrations belong to ERROR01A2–A4 so the foundation can be validated independently.
