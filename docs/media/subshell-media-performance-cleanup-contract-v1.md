# subshell Media Performance Cleanup Contract v1

`SUBSHELL_MEDIA_PERF02_CLEANUP_V1`

## Locked production behavior

- Active viewport-owner policy: `RAMPED`.
- Directional anchor maximum: `0.05` viewport.
- Direction-change ramp distance: `0.12` viewport.
- Owner switch hysteresis: `0.05` viewport.
- Fixed post-scroll delay: `0 ms`.
- Media3 single-player, preload, audio focus, lifecycle pause and manual pause behavior remain unchanged.
- The deterministic A-Z fixture matrix remains available in `r/subshell_video_lab`.

## Runtime cleanup

Production ownership contains no shadow profiles and no comparison path for CURRENT, NEUTRAL or SMALL.

The 16,384-record tuning ring buffer, capture flush, decoder/load analytics listener and
high-frequency profile-decision samples are removed. The feed retains only the minimal
variable-height motion tracker required to calculate distance since the latest direction
change for the RAMPED anchor.

Structured handoff and first-frame markers remain available in debug builds. Release builds
do not install the Android log/Perfetto sink.

## Preserved validation evidence

Final forward-only A-Z runtime capture reported:

```text
ACTUAL_OWNER_COUNT=26
RAMPED_DISAGREEMENTS_WITH_ACTUAL=0
NON_ADJACENT_TRANSITIONS=0
DUPLICATE_OR_REVERSE_TRANSITIONS=0
VIDEO_Z_TRANSITIONS=1
MISSING_MEDIA=NONE
FIRST_FRAME_SIGNALS=26
DROPPED_VIDEO_FRAMES=0
AUDIO_UNDERRUNS=0
PRELOAD_ERRORS=0
TUNE_DROPPED_RECORDS=0
FINAL_ACCEPTANCE=PASS
```

The historical tuning contracts and analyzers remain in the repository as evidence and
regression tooling; they are not invoked by production playback.
