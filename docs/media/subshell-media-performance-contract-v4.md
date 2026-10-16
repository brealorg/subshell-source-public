# subshell Media Performance Contract v4

MEDIA-PERF-01 phase 2.2 closes the remaining terminal-video, manual-pause and
post-scroll timing gaps found by the three-run phase 2.1 capture.

## Manual pause ownership

Manual pause is a feed-session playback intent, not a direct player command.
While the paused video retains a meaningful visible surface, autoplay MUST NOT
immediately reacquire it or replace the paused state. Explicit play clears the
pause intent. A different qualified video may take ownership after the paused
video leaves the viewport and scrolling settles.

## Terminal exhausted-feed takeover

When pagination is exhausted, the final video is the terminal video. Once that
terminal video satisfies the terminal visibility floor after scrolling ends, it
MUST be allowed to replace the penultimate video. Terminal takeover is not
conditioned on being closer to viewport center than the penultimate item; that
condition starved Video H in two of three measured runs.

This exception applies only to a feed whose terminal identity is known. Normal
feed competition remains 60/35/1.

## Scroll completion timing

`LazyListState.isScrollInProgress == false` already means the drag or fling has
ended. Playback arbitration therefore runs immediately on that transition.
There is no additional fixed 120 ms delay for ordinary scroll completion.
Preloading remains active while scrolling, and audible playback remains gated
until scrolling has ended.

## Runtime acceptance

- Video H commits and renders in every A-to-H-to-A run.
- Pause leaves the current video visibly paused until explicit play or a later
  qualified different-video takeover.
- No same-video `candidate_committed` event follows `manual_pause` while that
  surface remains meaningfully visible.
- Candidate-to-first-frame remains fully instrumented.
- Ordinary 60/35/1 behavior, one-player ownership and zero overlapping audio
  remain unchanged.
