# subshell Media Performance Contract v2

Status: implementation target for MEDIA-PERF-01 phase 2.

## Pipeline

Visible media, preload candidate, settled playback candidate and active media are separate states.
Preloading may run while scrolling; audible playback takeover remains scroll-settled.

## Candidate policy

- Standard acquisition: 60 percent of the media surface.
- Short/wide media: center activation band plus at least 5 percent of viewport height.
- Terminal item: relaxed 15 percent surface or 4 percent viewport-height requirement.
- Active playback retains the 60/35/1 competition model.

## Preload policy

- The best non-active visible candidate becomes preload focus while scrolling.
- Focus loads 3000 ms; adjacent ranks select tracks; rank distance two prepares source.
- The ExoPlayer and DefaultPreloadManager are built from the same builder.
- Playback consumes the managed MediaSource when available.

## Visual handoff

The poster remains above PlayerSurface until onRenderedFirstFrame confirms the new media.

## Acceptance

- Video H is eligible at terminal feed state.
- 21:9 and other short videos can acquire in the center band.
- Preload focus changes while the viewport is unsettled.
- No preload candidate may request audio focus.
- Build and tests are warning-free.
