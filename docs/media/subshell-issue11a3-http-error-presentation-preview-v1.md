# ISSUE11A3 — actual video error first, explanation underneath

## Accepted presentation

HTTP 403 · Forbidden

Access denied by server.

Retry

The status and reason are visible through Subshell's canonical shared `ErrorNotice`
`DEGRADED` Material 3 surface, not hidden in diagnostics. No settings toggle or second preview is added.
Unknown response codes retain their number. Non-HTTP failures have a concrete
network/decoder/Media3 heading; no HTTP status is invented from error 2004 or a
message string. Different preceding source failures remain available and are
summarized in the same explanation only when they differ from the final error.

## Scope

- Preserve structured source-failure metadata through the existing fallback path.
- Expose terminal metadata only for the current media and playback attempt.
- Keep same-media terminal errors stable on presentation reattachment; Retry
  starts a fresh attempt. Ignore callbacks when paused, unowned, for another
  media item, or no longer the player's current error.
- Reuse the existing ErrorPresentation and Retry action.
- Keep the old Int-only presentation API for existing code and tests. The real
  video overlay uses the typed-state overload.
- Remove only the A2 browser-UA experiment and its dedicated test. Restore the
  original VideoMediaCache source exactly; do not change HTTP stack or headers.
- Preserve A1 ordered source mapping/cache/crossposts without further edits.

No request URL, authorization data, response body or Throwable is put in UI state.
No deleted flag or permanent availability status is inferred or persisted.

## Evidence boundaries

This is a presentation repair, not a claim that the issue #11 video can be made
to play. The previous test returned 403 on all three sources. The Reddit web
message is not proof of deletion. A1's successful-video/audio qualification is
separate and is not supplied by this preview.

## Preview qualification

The runner checks the pinned DEV1 worktree, preserves exact preimages outside
Git, validates all edits before applying any of them, then runs focused tests,
a scoped Contract02 contract, relevant predecessor checks, builds and verifies
the DEV1 APK package. No checkpoint, main mutation, DEV2 mutation or remote write.
Runtime status remains PENDING until the user verifies the installed preview.

Required runtime checks: error status and explanation in normal/fullscreen
views; Retry does not retain the previous failure while loading; another
playable video is not contaminated by the previous failure; Back/scroll causes
no ghost audio or stale error (issue #18).

## API basis

Media3 InvalidResponseCodeException.responseCode supplies the actual status:
https://developer.android.com/reference/androidx/media3/datasource/HttpDataSource.InvalidResponseCodeException
Media3 error categories:
https://developer.android.com/reference/androidx/media3/common/PlaybackException
ADB shell -n disables stdin consumption:
https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md
## A3R3 accepted runtime presentation

Runtime review on Pixel 6 / Android 17 accepted the canonical shared
`ErrorNotice` `DEGRADED` presentation.

For the reproduced HTTP 403 case the normal user-facing player shows:

- `HTTP 403 · Forbidden`
- `Access denied by server.`
- `Retry`

The concrete HTTP status remains visible to the user. The explanation augments
the status instead of replacing it, and debug/diagnostics are not required to
understand the failure.

The rejected custom A3R1 video error surface is not part of the accepted
implementation. The shared `ErrorNotice` component itself is unchanged.

The current reproduction post is also reported by Reddit's web player as
`This video is no longer available`. That web text maps a terminal media
network error and is not treated as proof that the video was deleted.

The original `0.77.0-beta.1` playability root cause remains unverified.
Current qualification therefore proves robust ordered source failover and
accurate terminal HTTP-error presentation; it does not claim that the historic
video could have been made playable.
