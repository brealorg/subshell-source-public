# ISSUE18 — stale media navigation and returned-feed audio contract

## Scope

This contract locks two related but distinct regressions found around the
Activity-retained single-player media handoff.

### ISSUE18A — stale media navigation

Historical beta2 behavior allowed an old video presentation/playback request to
survive Back strongly enough that tapping a community target on another post
could resume the previous video instead of producing the expected community
navigation.

Current Google-feed ownership rejects stale playback scope by route and
visibility epoch and clears stale returned-feed handoff state during feed
reconciliation.

### ISSUE18B — returned-feed ghost audio

After opening a video post from a feed and returning with Back, the
Activity-retained player may still contain the same media and preserved playback
intent while the returned feed has not yet requalified a visible player surface.

The retained player must not advance audibly while no feed candidate owns
playback presentation. The returning feed pauses such an orphaned player until
the same media has regained normal Google visibility authority.

The player is not released and playback intent is not globally discarded. This
preserves the existing fullscreen/configuration handoff contract.

## Runtime evidence

On the Pixel 6 DEV1 runtime, the pre-fix sequence exposed:

- `owner_detached ... preserve_playback_intent=true`
- `selected=none`
- `play_when_ready=true`
- `is_playing=true`
- `muted=false`

while the returned feed surface was not yet qualified. This produced audible
playback behind a visually stationary video frame.

After the ISSUE18B repair, manual acceptance confirmed:

1. Open a video post from Home/feed.
2. Start playback.
3. Back to the feed.
4. Leave the returned feed stationary.
5. No orphaned audio advances while the feed waits for visibility qualification.
6. When normal feed visibility grants playback again, rendered video and audio
   resume together.

## Failure classification

The contract reports current candidate assertions independently from historical
or upstream failures.

- Primary failure: direct ISSUE18 contract assertion fails on the candidate.
- Predecessor failure: the historical/base behavior is known red while current
  ISSUE18 assertions pass.
- Release-blocking failure: a current candidate failure that prevents landing or
  release.

## Non-goals

ISSUE18 does not:

- release the Activity-retained player on every navigation;
- remove fullscreen/configuration playback continuity;
- replace Google's feed visibility eligibility policy;
- change the 80% / 500 ms visibility thresholds;
- add a second player;
- alter media URLs, caching, or audio-focus policy.
