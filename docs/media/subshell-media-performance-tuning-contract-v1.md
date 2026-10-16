# subshell Media Performance Tuning Contract v1

**Document ID:** `SUBSHELL_MEDIA_PERF02_TUNE01_INSTRUMENTATION_V1`  
**Status:** Instrumentation-only calibration contract  
**Parent architecture:** `SUBSHELL_MEDIA_PERF02_VIEWPORT_OWNER_V1`  
**Applies to:** mixed-feed video owner selection, surface handoff, preload telemetry and frame/audio diagnostics

## 1. Purpose

MEDIA-PERF-02-TUNE-01 measures the deterministic viewport-owner implementation without
changing its production behavior.

The current active profile remains:

```text
anchor offset while dragging: +/-0.10 viewport
owner switch hysteresis:       0.035 viewport
idle anchor:                    viewport centre
fling acquire/replacement:      blocked
```

This phase must not alter the active anchor, hysteresis, visibility floors, edge ownership,
manual pause, audio policy, preload policy, player ownership or lifecycle behavior.

## 2. Baseline finding

The first viewport-owner runtime capture established that:

- the architecture prevents F→H skips and G/H ping-pong;
- H becomes playable in every measured run;
- candidate-to-first-frame is stable near the existing player baseline;
- owner changes during direct drag can happen almost immediately after a direction change;
- a re-directionalized anchor can therefore feel earlier than the user's visual intent.

The next decision must be based on measured geometry and score margins, not another guessed
threshold.

## 3. Instrumentation invariants

1. The active production owner must still be selected only by the existing
   `VideoVisibilityPolicy.selectViewportOwner` path.
2. Shadow profiles must be pure calculations and must never mutate playback state.
3. High-frequency geometry and score samples must be buffered in memory.
4. High-frequency samples must not write to logcat while the feed is being dragged.
5. The ring buffer must be bounded and report any dropped records.
6. The capture must flush the buffer only after the app is backgrounded or the coordinator is
   released.
7. Existing sparse lifecycle, transition and first-frame events may remain in logcat.
8. Unit tests, source/media contracts and the warning-free build gate remain mandatory.

## 4. Motion telemetry

Each sampled viewport state records:

```text
motion=idle|dragging|flinging
reported_direction=up|down|none
sampled_direction=up|down|none
at_start=true|false
at_end=true|false
viewport_height_px
first_index
first_offset_px
delta_px
velocity_px_s
smoothed_velocity_px_s
drag_distance_px
reversal_distance_px
reversal_distance_fraction
reversal_time_ms
owner_age_ms
```

`sampled_direction` is derived from actual item displacement when available. The
`LazyListState` direction remains a fallback and a separate diagnostic field.

The tracker must support variable-height feed items and must not assume a fixed card height.

## 5. Surface geometry telemetry

The actual video surface, not the complete post card, records:

```text
visible surface fraction
visible viewport-height fraction
signed centre offset
surface top fraction
surface bottom fraction
surface height fraction
```

The decision sample includes these fields for both the current active owner and the best
candidate under the active profile.

## 6. Score telemetry

For each profile, every decision sample records:

```text
selected owner
best candidate
anchor offset
active-owner score
candidate score
score delta
whether switching is permitted
```

Score delta is defined as:

```text
active score - candidate score
```

A positive value means the candidate is geometrically better than the active owner before
hysteresis is applied.

## 7. Shadow profiles

All profiles are evaluated from the same immutable geometry sample. Each shadow profile
advances from its own previous shadow owner, so its reported sequence represents the policy
that profile would have produced over time rather than a one-frame counterfactual against the
production owner. CURRENT always starts from the real production owner and must agree with the
active decision.

### CURRENT

```text
max directional anchor offset: 0.10
switch hysteresis:              0.035
ramp:                           none
```

This profile must match the current production decision for representative viewport states.

### NEUTRAL

```text
max directional anchor offset: 0.00
switch hysteresis:              0.05
ramp:                           none
```

### SMALL

```text
max directional anchor offset: 0.04
switch hysteresis:              0.05
ramp:                           none
```

### RAMPED

```text
max directional anchor offset: 0.05
switch hysteresis:              0.05
ramp distance:                  0.12 viewport
```

The ramped profile starts at a neutral centre anchor after a direction change and moves
linearly toward the directional offset as actual drag distance accumulates.

No shadow result may start, pause, preload, mute, seek or otherwise affect a player.

## 8. Surface and handoff timeline

The capture must correlate:

```text
owner selected
surface mounted
poster visible/hidden
player ready
first frame rendered
surface unmounted
```

The asynchronous handoff trace begins when an owner transition is committed and ends on the
first rendered frame or a terminal cancellation/release outcome.

Trace counters include at least:

```text
score delta in thousandths of viewport
smoothed scroll velocity in px/s
number of shadow-profile disagreements
```

## 9. Preload and loading telemetry

The capture records:

```text
preload requested
preload completed
preload error
managed-source count/rank
load completed bytes
load duration
player buffered duration at play/first frame
```

Preload telemetry is observational. The existing `DefaultPreloadManager` strategy and preload
ranges remain unchanged in this phase.

## 10. Decoder, format and playback-health telemetry

The capture records:

```text
video decoder initialized/released
video format MIME type
video width/height/frame rate/bitrate
decoder reuse result/discard reasons
dropped video frames
audio underruns
```

These signals distinguish owner-policy timing from decoder, rendering or audio-output
problems.

## 11. Ring-buffer policy

- Capacity: 4096 records.
- Decision sampling interval: at least 32 ms unless an owner change forces a sample.
- Standalone viewport records are limited to motion/edge state transitions.
- Detailed geometry is carried by sampled decision records.
- Oldest records may be evicted only when capacity is exhausted.
- Every flush reports `dropped_records`.
- Calibration data is invalid when dropped records are non-zero.

## 12. Capture protocol

Each run must:

1. open `r/subshell_video_lab`;
2. position MEDIA D as active;
3. begin PID-filtered logcat capture;
4. drag steadily D→E→F→G→H;
5. park at the bottom;
6. reverse H→G→F;
7. background the app to flush the in-memory ring;
8. retain logcat until `tune_flush_end` is observed;
9. collect display-mode and device identity diagnostics;
10. analyze actual and all shadow profiles from the same samples.

Three runs are required.

## 13. Instrumentation acceptance

A run passes instrumentation completeness when:

- one `tune_flush_begin` and one matching `tune_flush_end` exist;
- `TUNE_DROPPED_RECORDS=0`;
- decision samples are present;
- CURRENT, NEUTRAL, SMALL and RAMPED fields are populated;
- CURRENT shadow decisions agree with the active production decision;
- surface, poster and first-frame signals are present;
- preload completion/error counts are reported;
- dropped-frame and audio-underrun counts are reported;
- decoder/format events are reported when emitted by the device.

This phase does not select a winning profile automatically. It produces evidence for a
separate tuning decision.

## 14. Profile-selection criteria for the next phase

The later tuning phase should prefer the profile that:

1. reduces owner changes within 25, 100 and 200 ms of direction reversal;
2. preserves D→E, F→G, G→H, H→G and G→F in controlled runs;
3. produces no F→H skip;
4. produces no G/H ping-pong;
5. does not acquire H materially earlier than intended;
6. does not delay idle or physical-edge ownership;
7. does not regress first-frame timing, dropped frames or audio underruns.

The chosen profile must be implemented in a later, isolated patch. MEDIA-PERF-02-TUNE-01
itself remains instrumentation-only.
