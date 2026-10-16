# subshell Media Performance Contract v1

**Document ID:** `SUBSHELL_MEDIA_PERFORMANCE_CONTRACT_V1`  
**Scope:** MEDIA-PERF-01 instrumentation baseline

## Purpose

This contract measures the current shared-player handoff path before preload, playlist or surface-retention changes are selected.

## Required transition events

Each activation receives a monotonically increasing transition ID. Debug logcat under tag `subshell.media.perf` must emit:

1. `candidate_committed`
2. `surface_mounted`
3. `play_requested`
4. `media_item_set` when the media changes
5. `prepare_called` when preparation is required
6. `player_ready`
7. `first_frame_rendered`
8. `audible_playback_started` for unmuted playback

Release and lifecycle events include `candidate_released`, `surface_unmounted`, `owner_detached`, `background_pause` and `player_released`.

## Measurement windows

- **Decision to first frame:** `first_frame_rendered.t_ms - candidate_committed.t_ms`
- **Play request to first frame:** `first_frame_rendered.t_ms - play_requested.t_ms`
- **Prepare to ready:** `player_ready.t_ms - prepare_called.t_ms`
- **Audio handoff gap:** next `audible_playback_started.t_ms - previous audible stop/release marker`

## Baseline targets

The instrumentation phase does not claim conformance. It records the existing path against these intended MEDIA-PERF-01 targets:

- Local preloaded fixture decision to first frame: **≤150 ms**
- Network source already preloaded decision to first frame: **≤300 ms**
- Audible handoff gap: **≤150 ms**
- Overlapping audible playback: **0 ms**
- Handoff-caused main-thread stall over 100 ms: **0**

## Analysis tool

`tools/analyze-media-perf01-log.py <logcat> --csv <output.csv>` groups markers by transition ID and calculates the supported timing windows.

## Decision gate

Preload/playlist/surface-retention implementation is selected only after at least three A→H video-lab runs provide transition distributions from these markers.
