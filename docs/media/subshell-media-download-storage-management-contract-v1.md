# subshell Download Storage Management Contract v1

## MEDIA-05D scope

MEDIA-05D exposes aggregate persistent-download storage usage without merging download ownership
with the bounded playback cache. The MEDIA-05A index remains the single source of truth for completed
and failed download records. MEDIA-05C WorkManager state remains the source of truth for active work.

The Settings `Downloaded videos` row shows completed item count and total completed bytes. It also
surfaces active and failed counts when present. `Delete all` requires confirmation, cancels all tagged
MEDIA-05C work, waits until no tagged work remains active, removes completed files, failed records,
the download index and stale `.part` files, then refreshes the shared controller state immediately.

The Downloads tab in Library shows the persisted byte size for every completed item. Size formatting
is deterministic and uses binary KB, MB and GB units.

Bulk deletion operates only inside `Context.noBackupFilesDir/media-downloads`. It does not call
`VideoMediaDataSource.clearCache`, does not remove `Context.cacheDir/media3-playback`, and does not
change settings backups. Download-location selection and export/share of downloaded files remain
deferred.

## Runtime proof

The Android instrumentation proof creates two completed downloads, one delayed active WorkManager
job and one stale partial file. It verifies aggregate count and bytes, per-item byte size, confirmed
bulk-delete semantics, active-job cancellation, complete index and directory removal, immediate empty
controller state and preservation of a playback-cache-directory marker.
