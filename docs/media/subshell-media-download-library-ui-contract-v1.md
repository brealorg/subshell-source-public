# subshell Download and Library UI Contract v1

Marker: `SUBSHELL_MEDIA05B_DOWNLOAD_ACTION_AND_LIBRARY_UI_V1`

## MEDIA-05B scope

MEDIA-05B exposes the foreground, persistent MEDIA-05A download store through the existing post
surface and Library destination. It does not introduce a second download index, a second playback
path, background execution, WorkManager, provider-specific URLs, or storage-management settings.

## Post action

The post action row no longer renders a persistent-download action. The button lost its call
site in `86b8637` (2026-08-14); the composable and the four assertions that described it were
retired in dead-code-1 (`e38b97b`, `0afc9fb`) and the never-passed `PostActionRow` parameters in
dead-code-2. The only download entry in the product is the media sheet's export to
`Downloads/subshell/` (`MediaComponents.kt`, `downloadPostMedia`), which does not use the MEDIA-05A
store. The store remains reachable through Settings → Media & links → Storage → Downloaded videos.

## Library

Library adds a `Downloads` tab backed only by complete records from `VideoDownloadStore`. Records
are joined to current post models by `mediaId`, sorted newest first, and unknown/stale model IDs are
not rendered. Removing a download updates the tab immediately and persists deletion in the
MEDIA-05A index.

Opening a downloaded post uses the normal post and media routes. `VideoPlaybackCoordinator`
continues to resolve the local persistent file through `VideoDownloadStore.resolvePlaybackSource`;
Library does not create a competing player or copy bytes into the bounded playback cache.

## Execution boundary

Download and delete work run on `Dispatchers.IO`, while Compose snapshot state is updated on the
caller context. MEDIA-05B remains foreground caller-owned. Background queueing, restart recovery,
notifications, aggregate storage controls, DASH/HLS packages, and split A/V downloads remain out
of scope.

## Runtime proof

The Compose surface remains covered by source and policy contracts that bind the post action labels,
Library `Downloads` tab, completed-record projection, and empty-state text to the canonical
`VideoDownloadUiController`.

`VideoDownloadUiInstrumentedTest` uses the existing debug-only localhost HTTPS fixture at the
canonical `/media04a1.mp4` endpoint without Espresso input injection. On device it invokes the same controller operation used by the post action,
verifies that the completed record enters the Library projection, stops the HTTPS server, proves that
the normal `VideoPlaybackCoordinator` reaches `READY` from the persistent local file, removes the
download through the controller, verifies an empty Library projection, and confirms that reopening
`VideoDownloadStore` no longer contains the record. This avoids Android-version-specific hidden
`InputManager` reflection while retaining a real device integration proof of the UI state pipeline.
