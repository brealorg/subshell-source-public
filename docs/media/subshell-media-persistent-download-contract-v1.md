# subshell Persistent Media Download Contract v1

Marker: `SUBSHELL_MEDIA05A_PERSISTENT_DOWNLOAD_FOUNDATION_V1`

## MEDIA-05A scope

MEDIA-05A establishes a provider-neutral persistent download foundation for one secure progressive
video source at a time. It intentionally does not add download buttons, a download tab, background
work, adaptive-manifest downloads, split audio/video downloads, or storage-management UI.

## Storage ownership

Persistent downloads live below `Context.noBackupFilesDir/media-downloads`. They are not stored in
`Context.cacheDir`, are intentionally excluded from Android Auto Backup, are not owned by the 128 MB
`SimpleCache`, and are not removed by the MEDIA-04C cache-clear action. The foreground downloader
uses a direct `DefaultHttpDataSource`; it does not route bytes through `VideoMediaDataSource` or the playback cache.

Each download is written to a unique temporary file, synchronized to storage and atomically moved
to its final filename. The index is written with the same temporary-file and replace discipline.

## Provider-neutral identity and index

The stable download ID is the first 32 hexadecimal characters of SHA-256 over the application-owned
identity tuple `subshell:download:v1:<mediaId>:<sourceIndex>`. The filesystem name is derived only
from that digest. Provider URLs, hostnames, request headers and credentials are not retained in the
persistent index.

Index schema v1 stores:

- download ID;
- application media ID and source index;
- `COMPLETE` or `FAILED` state;
- byte length and SHA-256 for completed content;
- MIME type and application-generated filename;
- creation timestamp;
- a bounded exception-class name for failed attempts.

`DELETED` is returned as the explicit result of removal. Deleted entries and their media files are
removed from the current index rather than retained as tombstones.

## Eligibility boundary

MEDIA-05A accepts HTTPS progressive single-file sources only. DASH, HLS, companion audio, and
sources with remote subtitle dependencies remain outside this phase. This prevents the first
persistent-download contract from claiming offline completeness for only part of a composite source.

## Playback resolution

`VideoPlaybackCoordinator` resolves an eligible completed download before creating Media3 items.
A valid persistent file replaces the remote URI with a `file://` URI, clears the remote cache key,
and is treated as local for preload and automatic-quality policy. If no valid download exists, the
original ordered remote source and fallback behavior remain unchanged.

A completed download is eligible for fast playback resolution only when its generated filename,
presence, and byte length match the index. Explicit verification additionally recalculates SHA-256.

## Deterministic device proof

`VideoPersistentDownloadInstrumentedTest` performs the complete sequence:

1. clear only the bounded playback cache and the test download directory;
2. serve a valid MP4 from the existing debug-only localhost HTTPS fixture;
3. download it through the direct persistent-download data source;
4. prove playback-cache bytes remain zero;
5. close the HTTPS server;
6. reopen the persistent index through a new store instance;
7. verify byte length and SHA-256;
8. prepare the original remote `VideoMedia` through `VideoPlaybackCoordinator` and reach Media3
   `STATE_READY` with the server offline;
9. delete the completed download and prove it no longer resolves;
10. perform a request against the closed server and prove `FAILED` survives another store reopen.

The runtime test does not mutate device network configuration and contacts no remote provider.

## Deferred work

- MEDIA-05B owns download/remove actions and Library presentation.
- MEDIA-05C owns queueing, background execution, pause/resume and restart recovery.
- MEDIA-05D owns user-visible download storage totals and bulk management.
- Adaptive DASH/HLS and split A/V persistence require later source-specific download expansion.
