# subshell Media Cache Management Contract v1

Marker: `SUBSHELL_MEDIA04C_CACHE_OBSERVABILITY_AND_CLEAR_V1`

## MEDIA-04C scope

MEDIA-04C exposes the bounded playback cache in Settings and provides an explicit, confirmed
clear-cache action. It does not add a user preference, download library, provider-specific data,
account state or a second cache instance.

## Settings surface

`Settings → Media & links → Media cache` shows the current process-cache usage and the fixed
128 MB limit. Reading cache status and removing resources execute on `Dispatchers.IO`; the
Compose main thread is never used for cache filesystem work.

The row is searchable by media, cache, storage, clear and disk terms. The Clear action is disabled
when the cache is empty or while an operation is running.

## Confirmation and outcome

Clear requires a Material confirmation dialog. The dialog states that cached media is removed
while settings and saved setups remain untouched.

The operation removes all committed cache resources through the active `SimpleCache` instance.
It does not release or recreate the cache, player or preloader. The UI publishes the authoritative
post-operation size. If currently active remote work writes new bytes immediately after clearing,
the UI reports the remaining/refilled size rather than falsely claiming zero.

Cache size and clear state are runtime UI state only. They are not included in
`SubshellSettings`, settings history or external backup snapshots.

## Privacy and telemetry

The clear event records only resource count, removed byte count and remaining byte count. URLs,
query parameters, request headers, cache keys and credentials are absent.

## Runtime proof

The debug-only instrumentation gate uses the existing localhost HTTPS fixture and production
`VideoMediaDataSource` pipeline:

1. reset only the debug app's media cache and cache index;
2. read the deterministic MP4 over HTTPS and require non-zero cache usage;
3. read status through the production cache API;
4. clear all resources through the production clear API;
5. require zero bytes and zero resources;
6. reuse the same process-local cache instance and HTTPS data-source pipeline;
7. require byte-identical media, a new upstream request and non-zero cache usage after refill.

This proves observability, non-blocking clear semantics and continued cache usability without
releasing the player/preloader cache owner.

## Acceptance

MEDIA-04C is accepted when:

- Settings displays current cache usage and the 128 MB limit;
- clear requires confirmation and runs off the main thread;
- settings, saved setups and backup allowlists remain unchanged;
- the production cache API reports non-zero usage after seeding;
- clear reports removed resources and zero post-clear usage in the quiescent runtime test;
- the same process-local cache accepts a complete, byte-identical refill after clearing;
- no player, preloader or cache release occurs in the clear path;
- URL-free telemetry, MEDIA-04A, MEDIA-04A.1 and MEDIA-04B contracts remain green.
