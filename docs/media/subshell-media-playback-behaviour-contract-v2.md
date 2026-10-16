# subshell Media Playback Behaviour Contract v2

**Document ID:** `SUBSHELL_MEDIA_PLAYBACK_BEHAVIOUR_CONTRACT_V2`  
**Status:** Normative implementation target  
**Primary surface:** mixed-content Reddit-style feeds  
**Evidence basis:** Android Media3 guidance plus black-box captures of Boost, Boost for Lemmy and subshell

## 1. Global invariants

1. At most one video is active.
2. At most one video is audible.
3. Only an audible, foreground player may own Android audio focus.
4. Manual intent outranks autoplay.
5. A detached or recycled surface must not continue playback.
6. Media state has one authoritative application-scoped owner.

## 2. Mixed-feed arbitration

Mixed-feed autoplay uses a three-threshold competition model:

| Constant | Value | Meaning |
|---|---:|---|
| `AUTOPLAY_ACQUIRE_VISIBLE_FRACTION` | `0.60` | A new candidate qualifies for autoplay or takeover |
| `AUTOPLAY_COMPETITION_KEEP_VISIBLE_FRACTION` | `0.35` | Current autoplay remains protected while at or above this visibility |
| `AUTOPLAY_ABSOLUTE_RELEASE_VISIBLE_FRACTION` | `0.01` | Current autoplay may remain active without a qualified replacement until this floor |
| `MANUAL_KEEP_VISIBLE_FRACTION` | `0.01` | Manually started video remains active while meaningfully visible |

### 2.1 No active video

A new video starts only when:

- autoplay is permitted;
- Compose reports that drag and fling scrolling have ended; no additional fixed settle delay is added;
- the candidate video surface is at least 60% visible; and
- the app is foregrounded.

### 2.2 Active autoplay video

While scrolling is active:

- the current video remains active while at least 1% visible;
- no competing candidate may take over.

After the viewport settles:

- current visibility at or above 35% retains ownership;
- below 35%, another candidate at or above 60% takes over;
- without a qualified replacement, current playback may remain until below 1%;
- below 1%, playback is released.

This produces stable playback without preventing reverse-scroll re-entry.

### 2.3 Manual playback

- Tap-to-play takes over immediately.
- Autoplay never preempts a manually active video.
- Another explicit tap may preempt immediately.
- The manual video releases below 1% visibility.
- `Autoplay = Never` disables automatic starts only.

## 3. Audio and mute

1. `Start videos muted` initializes one feed-session mute state.
2. Toggling mute changes that session state for subsequent feed videos.
3. `Muted` must mean `ExoPlayer.volume == 0f`.
4. `Sound on` must mean `ExoPlayer.volume == 1f`.
5. Mute changes are synchronized directly to the shared coordinator, independent of card recomposition.
6. A muted player must not request audio focus.
7. Backgrounding pauses playback and disables audio-focus handling.
8. Media-item replacement or preparation must not reset volume.

## 4. Lifecycle and conditional resume

On app background:

- pause playback;
- abandon audio focus;
- record whether a video was actively playing;
- preserve player position in the shared coordinator;
- clear visible active playback state.

On foreground return, playback resumes only when:

- the same feed state remains;
- the same media surface is still at least 1% visible;
- the viewport settles;
- playback was active before background;
- the user had not manually paused it; and
- autoplay remains permitted, unless the prior playback was manually initiated.

Otherwise playback remains paused and normal candidate selection proceeds.

## 5. Feed/post/full-screen handoff

The logical media identity, position, mute state and play intent should transfer between feed, post and full-screen surfaces without overlapping players. Full handoff and seek-state persistence remain MEDIA-02 scope.

## 6. Quality and codecs

`Auto`, `Low`, `Medium` and `High` are adaptive selection policies, not labels for fixed local files. Codec selection is capability-based across AV1, HEVC, VP9 and AVC, preferring stable hardware decoding over novelty.

## 7. Preloading

Mixed feeds use conservative current/next-candidate preloading. Immersive paged video may preload adjacent pages. UI smoothness and memory pressure outrank preload completion.

## 8. Required tests

- 60/35/1 takeover in both scroll directions.
- No takeover during fling.
- Manual playback remains protected.
- Muted UI produces zero physical output.
- Session mute state transfers to the next video.
- Background releases audio focus.
- Foreground conditionally resumes the same visible media.
- Invisible media does not resume.
- Build and media contracts pass without compiler warnings.

## 9. Research interpretation

Boost black-box captures showed replacement while the outgoing video remained partially visible, reverse-scroll takeover, delayed activation after fling settlement, background pause and foreground recreation/resume. Boost for Lemmy showed the same broad lifecycle family. Exact internal thresholds are not public; 60/35/1 is subshell's measured and testable approximation, not a claim about proprietary source code.
