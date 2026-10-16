# subshell Media Playback Behaviour Contract v1

**Document ID:** `SUBSHELL_MEDIA_PLAYBACK_BEHAVIOUR_CONTRACT_V1`  
**Status:** Proposed normative contract  
**Product name:** `subshell`  
**Primary UI pattern:** Mixed-content Reddit-style feed  
**Secondary UI pattern:** Immersive full-screen video feed  
**Applies to:** Inline video, post video, full-screen video, audio focus, lifecycle, quality selection, preloading and playback ownership

---

## 1. Purpose

This contract defines how video playback in subshell must behave from the user's perspective.

It separates:

1. **Mixed-feed playback** for Home, communities, search and other feeds containing text, images, links, galleries, GIFs and videos.
2. **Immersive playback** for full-screen video and any future vertically paged video experience.

The implementation may evolve, but observable behaviour must remain consistent with this contract unless the contract version changes.

---

## 2. Normative language

- **MUST / MUST NOT:** Required for conformance.
- **SHOULD / SHOULD NOT:** Expected unless a documented technical constraint justifies deviation.
- **MAY:** Optional.
- Thresholds marked **tunable** may be adjusted without changing the behavioural model, provided all invariants and acceptance tests still pass.

---

## 3. Core terms

### Active media

The single video currently owning playback state.

### Audible media

The single active video currently producing audible output.

### Candidate

A visible video surface eligible to become active through autoplay.

### Manual playback

Playback initiated through an explicit user action on that video, such as tapping Play.

### Autoplay playback

Playback initiated automatically by feed visibility policy.

### Video-surface visibility

The visible intersection of the actual rendered video rectangle with the usable content viewport.

The visibility calculation MUST NOT use the bounds of the entire post card.

### Settled viewport

A viewport in which feed scrolling has stopped and the candidate has remained stable long enough to avoid starting videos during a fling or transient layout change.

### Feed session

The lifetime of the current feed/navigation stack until it is discarded, refreshed beyond restoration, or the app process is terminated.

---

## 4. Global invariants

1. subshell MUST have at most **one active video**.
2. subshell MUST have at most **one audible video**.
3. Only the audible active video MAY own Android audio focus.
4. A new active video MUST stop or pause the previous active video before audible output begins.
5. A recycled post, detached surface or off-screen item MUST NOT continue producing audio.
6. Explicit user intent MUST take priority over autoplay.
7. Leaving subshell for the background MUST pause playback.
8. Returning from the background MUST NOT unexpectedly resume playback.
9. Player state MUST have one authoritative owner; individual feed cards MUST NOT independently arbitrate competing players.
10. GIF rendering and video rendering MAY use different surfaces, but they MUST share the same single-playback arbitration policy where applicable.

---

# Part A — Mixed-feed playback

## 5. Mixed-feed operating model

Mixed-feed playback uses a **sticky active player**.

The currently active video keeps ownership while its video surface remains visible. A more visible autoplay candidate MUST NOT preempt it.

A new autoplay video becomes active only after:

1. the previous active video is no longer visible;
2. the viewport is settled; and
3. a candidate satisfies the autoplay start requirement.

Manual selection of another video MAY preempt immediately.

---

## 6. Visibility policy

### 6.1 Measurement

The implementation MUST measure the actual video surface using window or viewport coordinates.

The visibility fraction is:

`visible video-surface area / total video-surface area`

System bars, persistent navigation chrome and obscuring overlays SHOULD be excluded from the usable viewport where technically practical.

### 6.2 Default thresholds

| Policy | Default | Meaning |
|---|---:|---|
| `AUTOPLAY_START_VISIBLE_FRACTION` | `0.60` | Minimum visible surface required to start an inactive autoplay candidate |
| `ACTIVE_KEEP_VISIBLE_FRACTION` | `0.01` | Active video remains owner while any meaningful surface remains visible |
| `MANUAL_KEEP_VISIBLE_FRACTION` | `0.01` | Manually started video remains owner while any meaningful surface remains visible |

These are tunable constants. The behavioural distinction between a high start threshold and near-zero keep threshold is normative.

### 6.3 Candidate ranking

When no video is active and multiple candidates satisfy the start threshold:

1. select the candidate with the greatest visible fraction;
2. if effectively tied, select the candidate closest to the viewport centre;
3. if still tied, retain the previously stable candidate;
4. do not alternate candidates on successive layout frames.

### 6.4 Scroll settling

Autoplay MUST NOT start while the list is in an active fling.

A candidate MUST remain the selected candidate across a stable post-scroll layout before autoplay begins.

Manual tap-to-play MUST NOT wait for scroll settling.

---

## 7. Autoplay behaviour

### 7.1 Modes

subshell MUST support:

- `Always`
- `Wi-Fi only`
- `Never`

### 7.2 Start

When autoplay is permitted, a video MAY start automatically only when:

- no other video has valid active ownership;
- the viewport is settled;
- the video surface satisfies `AUTOPLAY_START_VISIBLE_FRACTION`;
- network policy permits loading; and
- the app is in the foreground.

### 7.3 Keep and stop

Once autoplay begins:

- playback MUST continue while the active video surface remains meaningfully visible;
- another autoplay candidate MUST NOT steal ownership;
- playback MUST pause when the active video surface leaves the viewport;
- audio focus MUST be abandoned when audible playback ends.

### 7.4 Re-entry

When a previously active video re-enters the viewport:

- it MAY become an autoplay candidate again;
- it MUST follow the same start and settled-viewport rules;
- its position MUST be restored within the same feed session unless it completed naturally, was explicitly replayed, or state was evicted under documented resource pressure.

Position restoration is a target behaviour implemented with MEDIA-02.

### 7.5 Autoplay disabled

`Autoplay = Never` MUST disable automatic starts only.

It MUST NOT disable:

- tap-to-play;
- mute/unmute;
- full-screen playback;
- seeking;
- replay.

---

## 8. Manual playback behaviour

1. Tapping Play MUST activate that video immediately.
2. Manual playback MUST preempt any existing active video.
3. The previous video MUST be paused before the new video produces audio.
4. Manual playback MUST remain active while its video surface remains meaningfully visible.
5. Autoplay MUST NOT preempt a manually active video.
6. Tapping another video MAY preempt the current manually active video immediately.
7. Manual pause MUST remain paused until the user explicitly resumes it or navigation policy creates a new playback request.
8. Scrolling a manually active video completely out of view MUST pause it.
9. Scrolling it back into view MUST NOT override an explicit manual pause.

---

## 9. End-of-video behaviour

1. Natural completion MUST be distinguishable from manual pause and viewport pause.
2. Replay MUST restart from the beginning.
3. Inline looping MUST be governed by source metadata or a user-visible `Loop short videos` policy.
4. The mixed feed MUST NOT unconditionally loop every video.
5. A completed video returning into view MUST show a replay state unless loop policy explicitly applies.

---

## 10. Audio and mute state

### 10.1 Initial state

A persistent setting MUST define whether a new app session starts videos muted.

### 10.2 Session state

Mute state SHOULD be session-global for feed video:

- unmuting one feed video changes the feed session's sound preference;
- the next active feed video inherits that preference;
- muting again updates the same session preference;
- per-post mute divergence SHOULD NOT be the default model.

The persistent `Start videos muted` setting defines the initial session value, not an immutable per-video state.

### 10.3 Audio focus

When unmuted, the player MUST use media audio attributes and correct Android audio-focus handling.

- **Focus granted:** playback may proceed.
- **Transient focus loss:** pause or duck according to platform request and current foreground state.
- **Permanent focus loss:** pause and abandon focus.
- **Focus regained:** automatic resume is allowed only if subshell remains foreground, the same video is still active and the user did not pause it.
- **App backgrounded:** pause and do not auto-resume on return.
- **Headset/Bluetooth output disconnect:** pause immediately and do not auto-resume.

Muted video SHOULD NOT hold audio focus unnecessarily.

### 10.4 Volume transition

Switching between muted and audible playback SHOULD avoid re-preparing or visually restarting the video.

---

## 11. Navigation and player handoff

### 11.1 Feed to post

Opening the post containing the active video SHOULD transfer the same logical playback session to the post view.

The handoff MUST:

- preserve media identity;
- preserve position;
- preserve mute state;
- preserve play/pause intent where appropriate;
- prevent overlapping feed and post audio.

### 11.2 Post to feed

Returning to the feed SHOULD restore the same media state if the post remains in the feed session.

If the video is no longer sufficiently visible, it MUST remain paused.

### 11.3 Full-screen entry

Entering full screen SHOULD preserve:

- position;
- mute state;
- selected quality policy;
- playback speed;
- caption selection.

Full-screen entry MUST NOT create simultaneous inline playback.

---

## 12. App and screen lifecycle

1. `ON_STOP`, app backgrounding or loss of visible foreground ownership MUST pause playback.
2. App return MUST restore a paused state, not surprise-resume.
3. Screen lock MUST pause playback unless a future explicit background-audio mode permits otherwise.
4. Configuration changes MUST NOT create a second player or lose authoritative ownership.
5. Player resources MUST be released when the owning application scope ends.
6. Surface detachment MUST not leave audible playback running unintentionally.

---

## 13. Feed refresh, sorting and data replacement

1. Changing sort order MUST pause playback before replacing the feed.
2. Refreshing the feed MUST not bind the old active player to a different post with a recycled index.
3. Media identity MUST use a stable media/post identifier, never only the list index.
4. If the same media remains after refresh, state MAY be restored by stable identity.
5. Deleted, removed or filtered media MUST lose ownership immediately.

---

# Part B — Immersive playback

## 14. Separate immersive profile

A full-screen vertically paged video experience MUST use a separate policy from the mixed feed.

### 14.1 Selected-page ownership

- The selected snapped page is the active video.
- Swiping to another page MUST transfer ownership immediately after page selection settles.
- The previous page MUST stop or pause before the new page becomes audible.
- Visibility percentages are secondary; selected-page identity is authoritative.

### 14.2 Playback

- Selected video SHOULD begin with minimal latency.
- Looping MAY be enabled by default for short-form immersive video.
- Session mute state SHOULD be preserved.
- Manual pause MUST survive minor gestures and overlays.

### 14.3 Preloading

Immersive playback SHOULD preload the next page and MAY preload the previous page, subject to device and network budgets.

The mixed feed MUST NOT automatically inherit this aggressive adjacent-preload strategy.

---

# Part C — Quality, codecs and networking

## 15. User-visible quality policy

subshell SHOULD provide separate policy for Wi-Fi and mobile data:

- `Auto`
- `Low`
- `Medium`
- `High`

These are adaptive policy ceilings, not promises that a specific representation exists.

### 15.1 Recommended profile intent

| Profile | Intent |
|---|---|
| `Low` | Minimise data and power; prefer approximately 480p and avoid unnecessary high frame rate |
| `Medium` | Balanced quality; prefer approximately 720p |
| `High` | Prefer viewport-appropriate high quality and high frame rate without downloading wasteful representations |
| `Auto` | Let adaptive selection balance viewport, bandwidth, buffer health, decoder support and user data policy |

Exact bitrate and resolution caps are tunable implementation policy.

### 15.2 Viewport-aware selection

Inline feed playback MUST NOT download a representation materially larger than useful for the rendered viewport unless required for seamless full-screen handoff or unavoidable source constraints.

### 15.3 Codec selection

Codec choice MUST be capability-based.

The player SHOULD prefer an efficient source representation when:

- the source offers it;
- the device supports the required profile and level;
- hardware decoding is available or demonstrably efficient;
- HDR and frame-rate requirements remain correct.

AV1, HEVC/H.265, VP9 and H.264/AVC are source options, not unconditional quality rankings.

Software-decoded AV1 MUST NOT be preferred merely because AV1 is newer when it would degrade battery, heat or frame stability.

### 15.4 Frame rate and display

The player SHOULD:

- preserve source frame rate;
- use correct frame pacing;
- report content frame rate to Android;
- permit only seamless display refresh-rate changes in mixed-feed playback;
- reserve non-seamless refresh-rate switching for explicit full-screen policy.

---

## 16. Preloading and cache

### 16.1 Mixed feed

The mixed feed SHOULD use conservative, current-focused preloading:

1. prioritise the current or most likely next eligible video;
2. avoid preloading many distant videos;
3. stop or reduce preloading during rapid scroll, memory pressure, thermal pressure or constrained network conditions;
4. prioritise UI smoothness over preload completion.

### 16.2 Immersive feed

Immersive playback SHOULD use adjacent preloading around the selected page.

### 16.3 Cache

Cache policy MUST:

- use stable media identity;
- deduplicate repeated requests;
- respect Wi-Fi/mobile settings;
- have a bounded disk size;
- expose clear-cache control;
- avoid retaining protected or expired URLs beyond their validity model.

---

# Part D — Failure handling and accessibility

## 17. Source and playback failure

1. Adaptive source failure SHOULD attempt a compatible fallback source where available.
2. Audio-track failure MUST NOT silently produce a false `Sound on` state.
3. Unsupported codec/profile MUST produce a controlled fallback or actionable error.
4. Retry MUST be bounded and MUST NOT loop indefinitely.
5. Failure of one feed video MUST NOT destabilise the feed or active-player state.
6. Error UI SHOULD preserve poster, post controls and an explicit retry action.

---

## 18. Accessibility

1. Play, pause, mute, unmute, replay and full-screen controls MUST have accurate accessibility labels.
2. Touch targets MUST meet Android accessibility sizing expectations.
3. State changes MUST be exposed to accessibility services.
4. Captions MUST be supported when the source provides them.
5. Controls MUST remain operable without relying only on colour.
6. Autoplay and animation settings SHOULD respect relevant system accessibility preferences where applicable.

---

# Part E — Diagnostics and verification

## 19. Diagnostic state

Debug builds SHOULD expose:

- active media ID;
- activation reason: `manual`, `autoplay`, `post`, `fullscreen`;
- visible fraction;
- scroll-settled state;
- source type: local, DASH, HLS, progressive;
- selected codec;
- resolution;
- frame rate;
- bitrate;
- selected audio track;
- mute state;
- audio-focus state;
- buffer duration;
- rebuffer count;
- dropped frames;
- fallback/retry reason.

Diagnostic UI MUST NOT be presented as normal consumer UI.

---

## 20. Required acceptance tests

### 20.1 Single ownership

- Start Video A with sound.
- Start Video B while A remains visible.
- A pauses before B becomes audible.
- No overlapping tones or audio sessions occur.

### 20.2 Sticky autoplay

- Autoplay Video A after it satisfies the start threshold.
- Scroll until A is barely visible and B is mostly visible.
- A remains active.
- Scroll until A leaves the viewport.
- After the viewport settles, B may become active.

### 20.3 Manual ownership

- Start A manually.
- Scroll while A remains partially visible.
- A remains active.
- Autoplay candidates do not preempt A.
- Tapping B transfers ownership immediately.

### 20.4 Autoplay Never

- Set autoplay to Never.
- No video starts through scrolling.
- Tapping Play works normally.

### 20.5 Rapid fling

- Fling through several video posts.
- Videos do not rapidly start/stop during the fling.
- At most one settled candidate starts after scrolling stops.

### 20.6 Background

- Start audible video.
- Press Android Home.
- Audio and video pause.
- Return to subshell.
- Playback remains paused.

### 20.7 Audio interruption

- Begin audible playback.
- Trigger transient audio-focus loss.
- Playback follows focus policy.
- Disconnect the active audio route.
- Playback pauses and does not surprise-resume.

### 20.8 Feed/post/full-screen handoff

- Start video in feed.
- Open its post.
- Enter and exit full screen.
- Return to feed.
- No overlap occurs and state is preserved according to the navigation contract.

### 20.9 Quality

- Test a multi-representation adaptive source.
- Low, Medium and High produce different permitted track sets.
- Selection remains within viewport and capability policy.
- Fallback works when the preferred codec is unavailable.

### 20.10 Build quality

- Unit and instrumentation tests pass.
- Runtime contract checks pass.
- Compiler warnings introduced by media changes are treated as failures.
- Debug APK is produced with a recorded SHA256.

---

# Part F — R31 implementation delta

## 21. Already present

The current R31 development baseline already establishes:

- one shared Media3/ExoPlayer;
- real H.264/AAC test media;
- mute/unmute control;
- Android audio-focus integration;
- app-background pause;
- single-player ownership foundation;
- Reddit-neutral source/fallback model;
- Low/Medium/High quality policy;
- actual video-surface visibility measurement;
- explicit manual versus autoplay ownership;
- dual-video runtime fixtures;
- warning-free build gate.

## 22. Required changes before this contract is conformant

1. Change autoplay keep behaviour from `0.20` to the sticky near-zero model.
2. Ensure active autoplay cannot be preempted while still visible.
3. Add settled-scroll qualification before autoplay start.
4. Replace per-media unmute bookkeeping with explicit feed-session mute state.
5. Persist position within a feed session.
6. Implement seamless feed/post/full-screen player handoff.
7. Add adaptive Reddit DASH/HLS fixtures with separate audio.
8. Add capability-based codec selection and diagnostics.
9. Add conservative mixed-feed preloading.
10. Add the separate immersive profile.
11. Complete all acceptance tests in section 20.

---

# Part G — Locked product decisions

## 23. Decisions

The following are locked for Contract v1:

- subshell is primarily a **mixed-content Reddit client**, not a TikTok-style pager.
- Mixed-feed video uses **sticky ownership**.
- Visibility is measured from the **video surface**, not the whole post.
- High start threshold and near-zero keep threshold are intentional hysteresis.
- Autoplay cannot preempt a still-visible active video.
- Manual selection can preempt immediately.
- One player and one audible source are absolute invariants.
- Background playback is not part of Contract v1.
- Full-screen/immersive playback uses a separate profile.
- Quality labels are adaptive policies, not fixed-file labels.
- Codec efficiency never overrides hardware capability and stable playback.
- Warning-free media builds are required.

---

## 24. Research basis

This contract is informed by:

- Android Media3 ExoPlayer and audio-focus behaviour.
- Android Media3 preload-manager architecture.
- Android's documented distinction between current-only preloading in a mixed Facebook Newsfeed and adjacent preloading in Instagram Reels.
- Observed Boost mixed-feed behaviour supplied during subshell development.
- Reddit Android settings and feed-video mute/autoplay behaviour.
- subshell's R27–R31 runtime tests.

External products are references, not normative dependencies. subshell behaviour is defined by this contract.

---

## 25. Versioning

Changes that alter user-visible ownership, autoplay, audio, lifecycle or resume semantics require a new contract version.

Tuning constants may change within v1 only when:

- the sticky ownership model remains intact;
- manual priority remains intact;
- acceptance tests remain valid; and
- the change is recorded in the implementation decision log.
