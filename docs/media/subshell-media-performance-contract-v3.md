# subshell Media Performance Contract v3

## Scope

MEDIA-PERF-01 phase 2.1 corrects the terminal-video arbitration and the timing
analyzer after the first scroll-time preload capture.

## Evidence from phase 2 capture

Three A→H→A runs produced 33 committed playback transitions.

- every committed transition rendered a first frame;
- three additional transition identifiers were capture-start carry-over state,
  not failed playback transitions;
- 33/33 committed transitions used a preloaded media source;
- release → candidate median improved from 685 ms to 489 ms;
- release → first frame median improved from 841 ms to 678 ms;
- candidate → first frame remained effectively unchanged at 173 ms median;
- video H became the preload candidate in all three runs but was never committed
  for playback.

The last point identifies an arbitration defect, not a preload or decoder defect.

## Terminal feed semantics

A terminal video is the final video in a feed whose backing pagination is known
to be exhausted. It is not required to be the final post of any kind.

Local fixture feeds are finite and therefore pass `feedExhausted = true`.
A future Reddit feed must pass `feedExhausted = false` while another page may
still exist, and may only mark the final video after pagination reaches a
terminal state.

A qualified terminal video may replace the still-visible penultimate video
after scrolling settles when:

- the candidate satisfies the terminal visibility floor; and
- its center is at least 0.05 viewport heights closer to the viewport center.

This terminal exception does not change the ordinary 60/35/1 competition model.

## Analyzer semantics

Timing CSV rows represent committed playback transitions only.

Events that share a pre-existing transition identifier when logcat capture
starts are classified as carry-over and excluded. Preload events for another
media item must never overwrite the committed transition's media identity.

The analyzer reports both:

- candidate → first frame; and
- previous release → candidate / first frame.

## Acceptance criteria

- Video H commits and renders when the finite video-lab feed is parked at the
  bottom.
- A non-exhausted feed exposes no terminal video.
- The penultimate video is retained when it remains more central.
- All committed transitions produce a first frame.
- Capture-start carry-over transitions are excluded from CSV totals.
- Existing manual playback, 60/35/1, mute, lifecycle and preload contracts pass.
- The build is warning-free.
