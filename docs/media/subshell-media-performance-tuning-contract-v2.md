# subshell Media Performance Tuning Contract v2

**Document ID:** `SUBSHELL_MEDIA_PERF02_TUNE02_RAMPED_AZ_V1`  
**Status:** Final viewport-owner profile and alphabet video-lab validation  
**Parent architecture:** `SUBSHELL_MEDIA_PERF02_VIEWPORT_OWNER_V1`  
**Previous calibration:** `SUBSHELL_MEDIA_PERF02_TUNE01_INSTRUMENTATION_V1`

## 1. Decision

The production viewport-owner profile is RAMPED:

```text
ACTIVE_PROFILE=RAMPED
max directional anchor offset: 0.05 viewport
switch hysteresis:              0.05 viewport
anchor ramp distance:           0.12 viewport
fixed time delay:               none
final-capture ring buffer:       16384 records
```

The anchor begins at viewport centre after a direction change and moves linearly toward the
signed directional offset as real drag distance accumulates.

## 2. Evidence

Across three shared-geometry shadow runs, RAMPED:

- preserved D→E→F→G→H→G→F→E in every run;
- produced zero owner changes within 25 ms and 100 ms of direction reversal;
- produced no F→H skip;
- produced no G/H ping-pong;
- retained H ownership without reintroducing terminal starvation;
- produced zero dropped video frames and zero audio underruns.

NEUTRAL and SMALL were rejected because neither selected H in any run. CURRENT remained
functional but retained an early transition that RAMPED removed.

## 3. Playback invariants

This phase does not change:

- single-player ownership;
- Media3 preload behavior;
- player/surface lifecycle;
- mute, audio focus or manual pause;
- background pause and conditional foreground resume;
- account, subscription or navigation persistence.

## 4. Alphabet video lab

```text
VIDEO_LAB=A_TO_Z
community=r/subshell_video_lab
fixture_count=26
```

A–H remain unchanged. I–Z extend the deterministic matrix with additional surface heights,
aspect ratios, frame rates, durations and unique audio tones.

| ID | Geometry | FPS | Duration | Audio |
|---|---:|---:|---:|---:|
| I | 960×320 | 30 | 20 s | 1000 Hz |
| J | 480×960 | 30 | 21 s | 1040 Hz |
| K | 800×600 | 24 | 22 s | 1080 Hz |
| L | 600×800 | 24 | 23 s | 1120 Hz |
| M | 960×402 | 60 | 24 s | 1160 Hz |
| N | 640×800 | 60 | 25 s | 1200 Hz |
| O | 720×720 | 25 | 26 s | 1240 Hz |
| P | 960×480 | 25 | 27 s | 1280 Hz |
| Q | 600×900 | 50 | 28 s | 1320 Hz |
| R | 900×600 | 50 | 29 s | 1360 Hz |
| S | 600×960 | 30 | 30 s | 1400 Hz |
| T | 960×600 | 30 | 31 s | 1440 Hz |
| U | 800×640 | 48 | 32 s | 1480 Hz |
| V | 540×960 | 48 | 33 s | 1520 Hz |
| W | 960×412 | 60 | 34 s | 1560 Hz |
| X | 960×540 | 60 | 35 s | 1600 Hz |
| Y | 720×720 | 24 | 36 s | 1640 Hz |
| Z | 960×540 | 30 | 37 s | 1680 Hz |

All new fixtures use H.264 video and AAC stereo audio at 48 kHz. Each contains a visible
letter, timecode and continuous motion. Z is the final physical-end fixture.

## 5. Final A–Z capture

The final measured run begins with A playing and scrolls steadily to Z without tapping any
playback control. The ring buffer is flushed by backgrounding the app after Z has rendered.

Acceptance requires:

```text
ACTUAL_SEQUENCE=A_THROUGH_Z_EXACT
RAMPED_DISAGREEMENTS_WITH_ACTUAL=0
NON_ADJACENT_TRANSITIONS=0
DUPLICATE_OR_REVERSE_TRANSITIONS=0
VIDEO_Z_TRANSITIONS>=1
FIRST_FRAME_SIGNALS>=25
DROPPED_VIDEO_FRAMES=0
AUDIO_UNDERRUNS=0
PRELOAD_ERRORS=0
TUNE_DROPPED_RECORDS=0
FINAL_ACCEPTANCE=PASS
```

A failed exact sequence is evidence that the measured gesture or owner policy skipped a
fixture. It is not silently accepted as a partial pass.

## 6. Completion

After one accepted A–Z capture, RAMPED is the locked feed viewport-owner profile. Tuning
shadow telemetry may then be removed or restricted to debug-only diagnostics in a separate
cleanup change.
