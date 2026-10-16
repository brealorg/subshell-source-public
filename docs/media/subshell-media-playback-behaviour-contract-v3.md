# subshell Media Playback Behaviour Contract v3

**Document ID:** `SUBSHELL_MEDIA_PLAYBACK_BEHAVIOUR_CONTRACT_V3`  
**Status:** Normative implementation target  
**Primary surface:** mixed-content Reddit-style feeds  
**Supersedes:** v2 mixed-feed `60/35/1` arbitration

## 1. Global invariants

1. At most one video is active.
2. At most one video is audible.
3. Only an audible foreground player may own Android audio focus.
4. Explicit play, pause and mute intent outrank autoplay.
5. A detached or recycled surface must not continue playback.
6. Media state has one authoritative application-scoped owner.

## 2. Deterministic viewport owner

Mixed-feed autoplay uses one deterministic viewport owner instead of competing visibility
thresholds. The owner is selected from actual video-surface geometry, not the complete post
card.

Each visible video reports:

- visible surface fraction;
- visible viewport-height fraction;
- signed centre offset from the viewport centre; and
- stable feed order.

A qualified video is selected by distance to a viewport anchor. Surface dimensions do not
protect a large outgoing video from a smaller video whose centre is closer to the anchor.

## 3. Motion states

The feed distinguishes three states from `LazyListState`:

- **Dragging:** the user's finger is directly moving the list. The owner may move to the
  next anchor candidate so short media is not skipped.
- **Flinging:** inertial or programmatic scrolling is active without a direct drag. Existing
  visible playback may continue, but no new autoplay owner starts.
- **Idle:** scrolling has stopped. The nearest qualified anchor candidate owns playback.

`LazyListState.interactionSource` supplies drag state. `isScrollInProgress` distinguishes
fling/programmatic motion. `lastScrolledForward` and `lastScrolledBackward` supply direction.

## 4. Directional anchor

The idle anchor is the viewport centre. During a direct drag it shifts by `0.10` viewport
heights in the travel direction:

- downward drag: `+0.10`;
- upward drag: `-0.10`.

This makes large-to-small transitions such as D→E and F→G occur when the incoming media is
visually central, not after the outgoing large surface falls below an arbitrary percentage.

The current owner remains stable when candidate scores differ by less than `0.035` viewport
heights.

## 5. Feed edges

Physical feed edges come from `LazyListState.canScrollBackward` and `canScrollForward`.

- At the physical start, the first qualified video owns playback.
- At the physical end of an exhausted feed, the last qualified video owns playback.
- Leaving the end through an upward drag removes the end override and normal anchor selection
  returns ownership to the preceding video.

The final video therefore does not depend on extra content below it or a synthetic terminal
centre threshold.

## 6. Visibility floors

A new autoplay owner qualifies when either:

- at least `0.15` of its own surface is visible; or
- at least `0.04` of viewport height is occupied by the visible surface.

An existing owner remains meaningful while either:

- at least `0.01` of its own surface is visible; or
- at least `0.005` of viewport height remains visible.

These are eligibility floors, not the primary owner-selection algorithm.

## 7. Manual playback and pause

- Tap-to-play takes ownership immediately.
- A manually active video remains owner while meaningfully visible.
- Pause creates a feed-session pause intent for that media ID.
- Autoplay cannot reacquire the paused video while its surface remains visible.
- Explicit play clears pause intent.
- A different qualified video may own playback after the paused surface leaves.
- `Autoplay = Never` disables automatic owners only.

## 8. Preloading

Preload selection runs during dragging, flinging and idle states. With a known direction it
prioritizes the first meaningfully visible media ahead of the active owner. Preloading never
starts playback, changes mute state or requests audio focus.

## 9. Audio and lifecycle

The v2 audio/lifecycle requirements remain normative:

- session-global mute state is applied directly to ExoPlayer;
- muted playback has zero player volume and no audio focus;
- backgrounding pauses playback and releases focus;
- foreground resume is conditional on the same visible media and an idle viewport.

## 10. Required runtime behavior

The deterministic video lab must demonstrate:

- D→E while directly dragging from the tall D surface to the smaller E surface;
- F→G before H on downward travel;
- G→H at the physical bottom;
- H→G→F after upward reversal;
- no new autoplay owner during a fling;
- no G/H ping-pong while parked;
- pause persistence and zero overlapping audio.

Transition telemetry records `viewport_motion` and `viewport_edge` for every committed owner.
