# subshell Background Download Queue and Recovery Contract v1

Marker: `SUBSHELL_MEDIA05C_BACKGROUND_DOWNLOAD_QUEUE_AND_RECOVERY_V1`

## MEDIA-05C scope

MEDIA-05C moves eligible progressive single-file video downloads from a caller-owned coroutine to a
persistent WorkManager queue. The MEDIA-05A persistent store remains the only completed-download
index, and the MEDIA-05B post action and Library projection remain the user-facing surfaces.

The implementation uses stable WorkManager `2.11.2`. Every request requires a connected network and
non-low storage. The unique work name is derived only from the provider-neutral download ID and uses
`ExistingWorkPolicy.KEEP`, so repeated taps cannot create parallel work for the same media source.

## Persistent queue contract

`VideoDownloadWorker` is a stable worker class name because WorkManager persists worker class names
in its database. Its input contains the media ID, source index, MIME type, provider-neutral download
ID, and the HTTPS source URI required to recover the transfer after process death. The completed
MEDIA-05A index remains URL-free; queue input is owned by WorkManager and is not copied into that
index.

`VideoDownloadStore` serializes process-local writers and reloads the atomic index before reads and
mutations. This prevents separate UI, player, and worker store instances from overwriting or retaining
stale records while multiple WorkManager objects exist in one process.

## Progress and retry

The worker persists byte progress through `setProgressAsync`. Known content lengths produce an
integer percentage from 0 through 100. ENQUEUED, RUNNING, and BLOCKED work maps to the existing
`DOWNLOADING` UI state, and the post overflow/content description includes a percentage when one is
available.

A failed transfer is recorded by `VideoDownloadStore` and retried with exponential backoff for at
most three attempts. A terminal failed work item exposes the existing `Retry download` action.
WorkManager cancellation is checked during streaming so `Remove download` does not leave an active
writer behind.
Interrupted transfers do not resume partial bytes: before the next attempt restarts from byte zero,
stale download-specific `.part` files are removed.

## Process recovery

`SubshellApp` reconciles the persistent store and WorkManager queue through
`VideoDownloadUiController.observe`. Reconciliation is fast while work is active and backs off to a
low-frequency idle poll when the queue is empty. Recreating the app or controller therefore restores active work
as `DOWNLOADING`, completed work as `DOWNLOADED`, and terminal store failures as `FAILED` without a
second application-owned queue database.

Removing a completed download cancels any matching unique work before deleting the MEDIA-05A record
and file.

## Execution boundary

MEDIA-05C uses ordinary one-time WorkManager work. This covers persistent background execution within
the platform worker execution window. Long-running foreground workers, user-initiated data-transfer
jobs, notification permission UX, partial byte-range resume, aggregate storage management, DASH/HLS
packages, and split A/V downloads remain outside this phase.

## Runtime proof

The device runner uses the explicit Pixel ADB serial by default and executes three phases:

1. enqueue a delayed unique work item and prove a single active provider-neutral work identity;
2. force-stop the app process, start a new instrumentation process, recover the same queued identity
   through a new controller, then cancel it;
3. run the real worker against the canonical debug-only localhost HTTPS fixture, prove duplicate
   enqueue uses one unique work record, observe terminal progress 100, reopen the controller from the
   persistent store, and remove the completed download.

No remote media provider is contacted, and the runtime cleans the queue and test download residue.
