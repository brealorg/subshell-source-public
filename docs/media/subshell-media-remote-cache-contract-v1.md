# subshell Remote Media Cache Contract v1

Marker: `SUBSHELL_MEDIA04A_SHARED_REMOTE_CACHE_PIPELINE_V1`

## MEDIA-04A scope

MEDIA-04A activates the provider-neutral remote playback path without introducing provider
parsing, authentication, a download manager, metered-network policy or a cache-management UI.

The application declares `INTERNET` and `ACCESS_NETWORK_STATE`. Cleartext traffic remains
disabled, so production remote media is HTTPS by default.

## Shared data-source topology

One `DefaultDataSource.Factory` is shared by the format-aware media-source factory, the
`DefaultPreloadManager` and the player built from that preload manager. Local schemes remain
owned by `DefaultDataSource`:

- `asset://`
- `android.resource://`
- `file://`
- `content://`

Remote reads use a `DefaultHttpDataSource.Factory` upstream behind `CacheDataSource.Factory`.
The upstream has an explicit subshell user agent, a 10 second connect timeout and a 20 second
read timeout. Cache errors may be bypassed for the following request rather than making a
recoverable cache failure terminal for playback.

## Process cache

The playback cache is one process-local `SimpleCache` rooted in the dedicated
`cacheDir/media3-playback` directory. It uses `StandaloneDatabaseProvider` and
`LeastRecentlyUsedCacheEvictor` with a hard 128 MiB ceiling.

The cache is intentionally process-scoped instead of coordinator-scoped. Activity recreation,
full-screen handoff and a new presentation owner therefore reuse the same cache instance and do
not attempt to lock the same cache directory twice.

## Cache identity

Progressive HTTP(S) sources receive a stable custom cache key derived from the provider-neutral
media identity and source role. An explicit provider cache key may replace the generated value.
Rotating query parameters therefore do not fragment the cache for the same progressive object.

DASH and HLS retain Media3's URI-level cache keys. A single custom key must not collapse a
manifest and its distinct segment requests into one cache resource. This also avoids aliasing
unrelated signed adaptive URLs.

Local sources never receive remote custom cache keys.

## Telemetry and privacy

The shared cache records:

- cache-read event count and cached bytes read;
- upstream network request count and bytes read;
- cache-ignored event count.

Performance trace events contain only numeric counters and cache size. Remote URLs, query
parameters, request headers and provider credentials are not logged.

## Deferred work

MEDIA-04B owns metered-network and preload policy. MEDIA-04C now provides user-visible cache
size and confirmed clear-cache controls. Offline downloads remain a separate backlog item.

## Acceptance

MEDIA-04A is accepted when:

- secure network permissions are present and cleartext traffic is disabled;
- Media3 datasource and database APIs are direct version-catalog dependencies;
- one bounded process cache backs the shared player/preloader data-source factory;
- progressive remote media uses stable identity while adaptive and local sources do not receive
  a collapsing custom key;
- cache/upstream telemetry is URL-free;
- existing progressive, DASH, HLS, split A/V, fallback, track-selection, full-screen and rotation
  contracts remain green.

## Debug-only deterministic HTTPS runtime proof

Marker: `SUBSHELL_MEDIA04A1_DETERMINISTIC_HTTPS_CACHE_RUNTIME_PROOF_V1`

MEDIA-04A.1 adds a debug/instrumentation-only localhost HTTPS fixture. It does not add a
production certificate exception, remote provider dependency or cleartext fallback.

The test target starts a TLS server bound only to `127.0.0.1` and serves the packaged
`subshell_video_lab_a` MP4 bytes. A dedicated test certificate is trusted only by the debug
source set; the main manifest continues to disable cleartext traffic and release builds do not
contain this trust anchor or the server private key.

The runtime proof executes one stable progressive cache identity through the production
`VideoMediaDataSource` factory:

1. delete the previous media-cache directory and Media3 cache index before factory creation;
2. read the deterministic MP4 over localhost HTTPS and require upstream bytes plus created cache
   files;
3. close the HTTPS server completely;
4. read the same URI and cache key again;
5. require byte-identical output, cached-byte telemetry, and no additional upstream requests or
   upstream bytes.

The reusable `tools/run-media04a1-https-cache-runtime.sh` gate builds the debug app and
instrumentation APK offline, selects one ADB target using the mDNS-first policy, installs both
APKs, runs the single test class and validates the URL-free numeric proof markers from logcat.

This test deliberately resets only the debug app's bounded playback cache. Account, settings,
backup and other application state are outside its scope.

## MEDIA-04A.1 acceptance

MEDIA-04A is runtime-complete when:

- the first HTTPS read contacts the localhost fixture and reads at least the full fixture byte
  count from upstream;
- cache files exist after that cold read;
- the HTTPS server is closed before the second read;
- the second read returns the identical MP4 bytes entirely from the cache;
- cached-byte telemetry increases by at least the fixture size;
- upstream request and byte counters do not increase during the offline read;
- cache-ignore telemetry does not increase;
- the instrumentation and permanent source contracts remain green.
