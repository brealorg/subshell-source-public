# subshell NET01 secure HTTP boundary contract V1

## Status

NET01 introduces the first real network data-source boundary. It does **not** make network DTOs or
remote responses a UI source of truth. DATA-03 Room state remains the exclusive higher-layer read source until a later sync integration writes validated network data transactionally into Room.

Implementation marker: `SUBSHELL_NET01_SECURE_HTTP_BOUNDARY_V1`.

## Architecture

The boundary follows Android's recommended data-layer split:

- one shared `OkHttpClient` owns connection pooling, TLS, redirects, and timeouts;
- `SubshellHttpClient` owns secure JSON transport, cancellation, body bounds, metadata, and sanitized
  failure classification;
- provider DTOs remain in `data.network.reddit` and map into domain `Post` objects;
- `RedditListingNetworkDataSource` owns Reddit Listing paths, `after` cursors, `limit <= 100`, and
  `raw_json=1`;
- repositories and UI do not import OkHttp, JSON DTOs, or live network state in NET01.

## Security and privacy

- HTTPS is mandatory in both the endpoint type and Android Network Security Configuration.
- Cleartext is disabled in production and debug manifests.
- The higher-priority debug manifest explicitly replaces only `android:networkSecurityConfig`, allowing the localhost test CA without weakening the production manifest.
- Production trusts the platform CA store. The existing debug-only localhost CA remains scoped to
  instrumentation builds.
- Redirects are disabled, preventing credentials from following an unexpected redirect target.
- Authentication is injected through `NetworkBearerTokenProvider`; NET01 stores no token and performs
  no OAuth or refresh flow.
- A transparent, non-empty User-Agent is mandatory.
- No HTTP logging interceptor is installed, and error response bodies are never surfaced in failures.
- Successful JSON bodies are bounded to 2 MiB by default and are validated for JSON content type.

## Failure and retry contract

`NetworkResult` distinguishes:

- HTTP failures, including status, retryability, rate metadata, and `Retry-After`;
- DNS, connect, timeout, TLS, and generic I/O transport failures;
- empty, non-JSON, oversized, and malformed JSON protocol failures.

TLS failures are not blindly retryable. NET01 performs no application-level retry loop. A later sync
owner may use the explicit retry classification and server metadata to schedule backoff.

## Reddit Listing contract

- Cursor pagination uses `after`; no page-number abstraction is introduced.
- Listing limit is restricted to Reddit's maximum of 100.
- `raw_json=1` is always sent.
- Community names and every request URL component are added through structured `HttpUrl` builders.
- Provider media URLs are parsed with OkHttp, must resolve to HTTPS, and reject embedded username/password credentials; string-prefix URL validation is not used.
- Unknown JSON fields are ignored, provider DTOs are not exposed to UI, and only valid `t3` children
  map into domain posts.
- The production OAuth endpoint is intentionally not instantiated by application state in NET01.

## Cancellation and execution

The callback API is bridged with `suspendCancellableCoroutine`. Coroutine cancellation calls
`Call.cancel()`, and a response arriving after cancellation is closed. Transport execution is
asynchronous and does not block the caller thread.

## Runtime proof

`Net01HttpBoundaryInstrumentedTest` uses the existing debug-only localhost PKCS12 certificate and a
real TLS socket. It proves:

1. HTTPS transport and JSON Listing mapping;
2. `User-Agent`, `Accept`, cursor, limit, and `raw_json=1` request policy;
3. optional bearer authentication without token storage;
4. rate-limit and `Retry-After` parsing;
5. retryable HTTP 429 classification;
6. sanitized malformed JSON handling;
7. bounded response-body rejection;
8. coroutine cancellation reaches the underlying OkHttp call;
9. no live Reddit request, OAuth mutation, or device network configuration change.

## Deferred work

- AUTH01 owns OAuth authorization, secure token storage, refresh, revocation, and account identity.
- NET02 owns network-to-Room synchronization and conflict policy while preserving Room as source of
  truth.
- Persistent queued network writes and WorkManager backoff are deferred until authentication and the
  real remote write API exist.

## Caller-dispatcher isolation

`SubshellHttpClient` owns an injected I/O dispatcher. The complete OkHttp response lifecycle,
including bounded body reads, JSON decoding, protocol classification, and `Response.close()`, runs
inside that dispatcher even when the suspending caller originates from Compose or another main-thread
scope. Coroutine cancellation still calls `Call.cancel()`, and the caller resumes on its original
dispatcher only after the network boundary has produced a sanitized `NetworkResult`.
