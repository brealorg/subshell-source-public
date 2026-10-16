# subshell Media Performance Contract v6

## Scope

MEDIA-PERF-02 replaces percentage-retention and terminal-special-case arbitration with a
single deterministic viewport-owner model. Existing Media3 preloading, one-player ownership,
poster-until-first-frame, audio focus and performance timing remain in place.

## Baseline finding

The phase-2.3 capture showed:

- candidate-to-first-frame median near 146 ms;
- every committed transition was a fresh `autoplay_acquire` with `previous=none`;
- no committed transition carried a real scroll direction;
- H never committed;
- directional code therefore did not control production ownership.

The bottleneck was owner arbitration and feed motion state, not decode latency.

## Viewport source of truth

Each Home/Community `LazyListState` reports:

- direct drag state through `interactionSource.collectIsDraggedAsState()`;
- total scrolling through `isScrollInProgress`;
- last direction through `lastScrolledForward` / `lastScrolledBackward`;
- physical start/end through `canScrollBackward` / `canScrollForward`.

This state is application-owned and drives both playback and preload selection.

## Surface source of truth

Actual video surfaces continue to report bounds through `boundsInWindow()`. Signed centre
offset changes must trigger reconciliation even when absolute centre distance is unchanged.

## Performance behavior

- During direct drag, owner handoff may occur at the shifted viewport anchor.
- During fling, no new autoplay owner may start.
- During idle, owner selection is immediate; no fixed settle delay is added.
- At feed end, the last qualified video owns playback deterministically.
- Preload continues while the list moves.

## Telemetry

Every `candidate_committed` event records:

- `previous` media;
- transition reason;
- scroll direction;
- `viewport_motion` (`dragging`, `flinging`, `idle`);
- `viewport_edge` (`start`, `middle`, `end`).

The analyzer exports the same fields alongside first-frame timing.

## Acceptance

1. Unit tests validate anchor selection, drag takeover, fling gating, edge ownership,
   hysteresis, manual pause and preload direction.
2. State tests validate D→E, F→G→H, H→G→F and signed-offset reconciliation.
3. Runtime captures must contain no F→H skip and no G/H ping-pong.
4. H must commit in every bottom-edge run.
5. Candidate-to-first-frame remains separately measured and should not regress materially
   from the phase-2.3 146 ms median.
6. Build and source/media contracts pass without compiler warnings.
