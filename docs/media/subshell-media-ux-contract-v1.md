# subshell Media UX Contract v1

Implementation marker: `SUBSHELL_MEDIA_UX01A_TRANSPORT_CONTROLS_V1`

## MEDIA-UX-01A scope

The inline feed player uses the existing single shared Media3/ExoPlayer instance and the
already accepted RAMPED viewport-owner policy. This phase adds only the first transport
control layer:

- a tap on the selected video shows or hides the control overlay;
- controls auto-hide after 3 seconds only while playback is ready and actively playing;
- paused, buffering, ended, and terminal-error states keep controls visible;
- manual pause preserves the selected player surface and rendered frame;
- explicit resume preserves the current playback position;
- mute remains shared across the feed session;
- buffering is represented by a progress indicator rather than a false pause state;
- completed playback exposes an explicit replay action;
- exhausted source fallback exposes an explicit retry action;
- inline playback no longer loops automatically (`REPEAT_MODE_OFF`).

## Preserved contracts

- one shared player;
- RAMPED ownership and all MEDIA-PERF-02 thresholds;
- scroll-time preload;
- background pause and conditional foreground resume;
- direct player-volume synchronization and Android audio focus;
- A-Z deterministic video fixtures;
- MEDIA-UX-01A transport behavior remains unchanged outside the seek interaction.

## Acceptance

`MEDIA-UX-01A` is accepted when unit tests, the media contract, the source contract, and a
warning-free debug build pass. Runtime smoke must confirm play/pause, mute/unmute,
buffering visibility, ended replay, retry presentation, and control auto-hide without
owner-policy changes.


## MEDIA-UX-01B scope

Implementation marker: `SUBSHELL_MEDIA_UX01B_SEEK_SCRUBBING_V1`

The selected inline video now exposes a continuous seekbar whenever Media3 reports a known,
positive duration and the current media item supports seeking. Current position and duration
are displayed alongside the slider.

Scrubbing is an explicit interaction transaction:

- the shared player pauses temporarily when a playing video begins scrubbing;
- the viewport owner is locked for the duration of the slider interaction;
- controls remain visible and auto-hide is suspended;
- releasing the slider seeks to the clamped target position;
- playback resumes only when it was playing before scrubbing;
- a manually paused video remains paused after seeking;
- unknown duration hides the seekbar rather than presenting false progress;
- cancellation during surface disposal clears the scrub lock without starting playback.

## MEDIA-UX-01B acceptance

- position and duration formatting is deterministic;
- seek targets are clamped to `[0, duration]`;
- scrubbing blocks RAMPED owner handoff until release;
- RAMPED thresholds, preload, audio focus, global mute, and NAV-LIFECYCLE-01 remain unchanged;
- unit tests, media contract, source contract, and a warning-free debug build pass.

## MEDIA-UX-01C scope

Implementation marker: `SUBSHELL_MEDIA_UX01C_VISUAL_POLISH_V1`

The inline transport layer follows the active Material 3 theme rather than carrying an
independent hard-coded seek color:

- the seek thumb and played track use `MaterialTheme.colorScheme.primary`;
- the buffered range uses the same primary role at reduced opacity;
- the unbuffered range uses a neutral translucent track over the video;
- a vertical control scrim preserves contrast for top and bottom controls without flattening
  the full frame;
- active playback controls auto-hide after 2.5 seconds;
- scrubbing, paused, buffering, ended, and error states retain the existing visibility rules;
- custom per-player seek colors remain deferred to a later settings phase.

## MEDIA-UX-01C acceptance

- buffered position is clamped to the known duration and exposed as a deterministic fraction;
- theme accent changes flow directly into seek thumb, played track, and buffered track;
- RAMPED ownership, scrubbing semantics, preload, audio, global mute, and NAV-LIFECYCLE-01
  remain unchanged;
- unit tests, media contract, source contract, and a warning-free debug build pass.


## MEDIA-UX-01C.1 scope

Implementation marker: `SUBSHELL_MEDIA_UX01C1_HIGH_CONTRAST_NEUTRAL_V1`

Runtime validation in light theme showed that the app-level Material accent can have insufficient
contrast against arbitrary video frames and the player scrim. The inline player therefore uses a
contrast-stable neutral overlay palette independent of the surrounding app surface:

- seek thumb and played track use opaque white;
- buffered range uses white at 48% opacity;
- unbuffered range uses white at 24% opacity;
- the existing black vertical control scrim remains the contrast foundation;
- the 2.5-second auto-hide timing and all MEDIA-UX-01B interaction semantics remain unchanged;
- following the app accent and custom seek colors remain optional future player settings rather
  than the production default.

## MEDIA-UX-01C.1 acceptance

- the seekbar remains legible in both light and dark app themes over arbitrary video content;
- the player palette does not change when the surrounding Material color scheme changes;
- RAMPED ownership, scrubbing, preload, audio, global mute, and NAV-LIFECYCLE-01 remain unchanged;
- unit tests, media contract, source contract, and a warning-free debug build pass.


## MEDIA-UX-01C.2 scope

Implementation marker: `SUBSHELL_MEDIA_UX01C2_TIMELINE_SIZE_PRESETS_V2`

The inline player replaces the oversized default Material slider geometry with three curated
timeline-size presets while retaining Material slider interaction and accessibility semantics:

- `Compact`: 3 dp track, 10 dp resting thumb, and 14 dp scrubbing thumb;
- `Standard`: 4 dp track, 12 dp resting thumb, and 16 dp scrubbing thumb;
- `Large`: 6 dp track, 16 dp resting thumb, and 20 dp scrubbing thumb;
- `Standard` is the default and follows the established Media3 timebar scale;
- every preset retains a minimum 48 dp touch target independently of its visual geometry;
- the custom Material 3 slider thumb and track overloads are explicitly opted in at the narrow `LocalVideoPlayer` call site;
- the high-contrast white/48%/24% palette remains unchanged;
- preset selection is persisted, searchable in Settings, and included in backup and restore;
- scrubbing, control auto-hide, RAMPED ownership, preload, audio, global mute, and
  NAV-LIFECYCLE-01 remain unchanged.

## MEDIA-UX-01C.2 acceptance

- all three profiles use the contracted geometry and preserve at least a 48 dp touch target;
- the thumb is circular and grows only for active scrubbing;
- older backups restore the `Standard` default while current backups round-trip the selected preset;
- unit tests, media contract, source contract, and a warning-free debug build pass;
- runtime smoke confirms all three sizes remain usable without changing seek behavior.


## MEDIA-NAV-02 scope

Implementation marker: `SUBSHELL_MEDIA_NAV02_IMMERSIVE_HANDOFF_V1`

Video presentation is a route-level ownership handoff around the existing application-scoped
Media3 player rather than a second player or a media reload:

- feed, post detail, and full screen attach the same media identity to the same player instance;
- ownership transfer preserves position, buffered state, mute, quality, and current play/pause intent;
- detaching the outgoing surface does not pause playback while a declared presentation handoff is active;
- a manually paused video remains paused when entering and leaving full screen;
- full-screen ownership suspends feed RAMPED arbitration and feed preload selection;
- the full-screen surface does not publish itself as a feed viewport candidate;
- returning from full screen reveals the exact underlying post or feed route already held by state;
- app backgrounding still pauses, while foreground resume can reacquire the visible full-screen video;
- inline and full-screen presentations both use the existing `SurfaceView`-backed `PlayerSurface`;
- full screen is edge-to-edge with black background, overlay controls, safe-drawing control insets,
  transient system bars by swipe, and no forced orientation;
- player-control taps show system bars and the existing 2.5-second control timeout hides them again;
- non-video image, GIF, and gallery viewer behavior remains on the existing viewer shell.

## MEDIA-NAV-02 acceptance

- exactly one ExoPlayer remains application-scoped;
- feed → post → full screen → post → feed retains media identity and playback position;
- playing video continues through each presentation transfer without a reset to `0:00`;
- manual pause, global mute, quality policy, timeline preset, and scrubbing semantics are unchanged;
- full-screen controls avoid status, cutout, and navigation gesture regions while video draws edge-to-edge;
- system back and both exit controls return to the precise underlying route;
- RAMPED feed ownership cannot replace the full-screen media;
- unit tests, media contract, source contract, and a warning-free debug build pass;
- runtime smoke verifies portrait and landscape without forced-orientation dependencies.

## MEDIA-NAV-02.1 scope

Implementation marker: `SUBSHELL_MEDIA_NAV021_FULLSCREEN_FIT_AND_TOUCH_TARGET_V1`

Runtime validation of MEDIA-NAV-02 exposed two presentation defects without changing the
shared-player handoff itself:

- a raw full-window `PlayerSurface` stretched landscape media to portrait window bounds;
- the 48 dp full-screen control met the minimum target but was unreliable at the physical edge.

The full-screen presentation therefore uses Media3 `ContentFrame` with `ContentScale.Fit`. The
underlying default remains a `SurfaceView`, while the video preserves its source aspect ratio and
uses black letterboxing rather than non-uniform scaling or cropping.

The enter/exit full-screen control keeps its 48 dp circular visual treatment but uses a 64 × 56 dp
interactive target. The additional width expands inward from the physical screen edge, and the
child control consumes the tap before the full-surface control-visibility gesture.

All player identity, position, buffered state, manual play/pause intent, mute, quality, timeline,
scrubbing, RAMPED suspension, preload suspension, route return, system-bar, inset, and orientation
contracts from MEDIA-NAV-02 remain unchanged.

## MEDIA-NAV-02.1 acceptance

- 16:9, 9:16, square, and ultrawide fixtures retain source aspect ratio in full screen;
- full-screen scaling is `ContentScale.Fit`, with black letterboxing and no stretching or crop;
- the visible full-screen control remains 48 dp while the interactive target is 64 × 56 dp;
- both enter and exit actions use the expanded target without toggling the parent video surface;
- unit tests, media contract, source contract, and a warning-free debug build pass;
- runtime smoke confirms Video A is undistorted and full-screen entry is reliable under thumb use.

## MEDIA-NAV-02.2 scope

Implementation marker: `SUBSHELL_MEDIA_NAV022_FULLSCREEN_TEXTURE_SYNC_V1`

Runtime validation of MEDIA-NAV-02.1 confirmed that `ContentFrame` established correct 16:9
letterbox bounds, but the default `SurfaceView` buffer still rendered Video A with non-uniform
pixel scaling in portrait. Landscape rendering, the expanded full-screen target, and the compact
full-screen timeline were correct.

Only the full-screen `ContentFrame` therefore switches to Media3
`SURFACE_TYPE_TEXTURE_VIEW`. The inline feed and post player keep the default `SurfaceView`.
`ContentScale.Fit`, the black shutter and letterbox, the shared Media3 player, and the existing
presentation handoff remain unchanged.

This is a presentation-surface correction only. It does not create another player, reload the
media item, reset position, change playback intent, alter mute or quality, modify timeline or
scrubbing behavior, or change RAMPED, preload, route-return, lifecycle, system-bar, inset, or
orientation policies.

## MEDIA-NAV-02.2 acceptance

- portrait full screen renders Video A at its source 16:9 pixel geometry without stretching;
- landscape full screen remains undistorted and fills the available 16:9 area;
- full-screen `ContentFrame` uses `SURFACE_TYPE_TEXTURE_VIEW` with `ContentScale.Fit`;
- inline `PlayerSurface` remains on its default `SurfaceView` path;
- black letterboxing, 64 × 56 dp full-screen target, and timeline presets remain unchanged;
- unit tests, media contract, source contract, and a warning-free debug build pass;
- runtime smoke verifies portrait and landscape, first-tap full-screen entry, seek, mute, and return.

## MEDIA-NAV-02.3 scope

Implementation marker: `SUBSHELL_MEDIA_NAV023_FULLSCREEN_ROTATION_STATE_V1`

Runtime validation of MEDIA-NAV-02.2 confirmed correct 16:9 portrait rendering with the
full-screen `TextureView`, but an Activity recreation caused by portrait/landscape rotation
discarded the active media-viewer route and returned to the underlying feed. Full-screen route
identity must therefore be saved as primitive identifiers rather than relying on transient `Post`
objects in `SubshellState`.

The saveable route contract records the underlying post id, active full-screen media id, media
viewer index, and post visit id. Recreation resolves those identifiers from the canonical local
post data and restores the same route stack, so Back still returns first to the underlying post
when one exists and otherwise to the original feed context. Unknown or retired identifiers are
rejected instead of creating an invalid route.

The full-screen viewer also keeps its latest valid playback position in saveable state. A recreated
Media3 coordinator applies that position only to the first attachment of the restored media item;
normal feed/post/full-screen handoffs still reuse the live player without seeking or reloading.
The immersive system-bar policy is reapplied by the recreated viewer. TextureView rendering,
content fit, mute, quality, timeline presets, scrubbing, RAMPED, preload, and orientation policy
remain unchanged.

## MEDIA-NAV-02.3 acceptance

- portrait → landscape and landscape → portrait keep the video in full screen;
- the active media id, underlying post route, viewer index, and post visit id survive recreation;
- the latest valid playback position is restored on the recreated player without repeated seeks;
- Back exits to the exact underlying post when present, then to its original feed context;
- unknown restored ids are safely rejected;
- immersive system bars are reapplied and no orientation is forced; direct configuration handling is contracted separately by MEDIA-NAV-02.5;
- unit tests, all project contracts, and a warning-free debug build pass;
- runtime smoke verifies both rotation directions, position continuity, mute, seek, and route return.


## MEDIA-NAV-02.3.1 scope

Implementation marker: `SUBSHELL_MEDIA_NAV0231_SAVEABLE_ROUTE_MIRROR_V1`

Runtime validation showed that the initial MEDIA-NAV-02.3 implementation declared saveable
primitive route fields but did not reliably update them when only `selectedMediaPost` changed. The
write-back lived in `SideEffect`, while the fullscreen-only transition could preserve the already
active media id and therefore fail to invalidate the `rememberSubshellState` composition scope. On
rotation, the stale saved value remained `null`, so the recreated state restored the underlying post
or feed instead of the media viewer.

The route mirror is now derived during composition as a primitive `NavigationRouteRestoreSnapshot`
before a `SideEffect` publishes it to the saveable fields. Reads of selected post id, selected media
id, media index, and post visit id are therefore observed directly, and every route-only fullscreen
transition invalidates the composition scope and updates the saveable mirror before configuration
recreation. Playback position restoration,
TextureView rendering, content fit, mute, quality, timeline presets, scrubbing, RAMPED, preload,
and system-bar policy remain unchanged.

## MEDIA-NAV-02.3.1 acceptance

- opening fullscreen from an already-playing post updates the saveable media-route id even when the
  active playback id does not change;
- portrait → landscape and landscape → portrait retain the media viewer;
- the recreated viewer restores the same media id, underlying post, viewer index, and post visit id;
- no route mirror reads mutable navigation state only from inside `SideEffect`;
- unit tests, all project contracts, and a warning-free debug build pass;
- runtime smoke confirms both rotation directions and exact Back return.


## MEDIA-NAV-02.4 scope

Implementation marker: `SUBSHELL_MEDIA_NAV024_CONFIGURATION_STABLE_PLAYER_OWNER_V1`

The single Media3 player is owned by an Activity-retained `VideoPlaybackViewModel`, not by a
Compose `remember` scope. Activity recreation for rotation therefore replaces only the video
presentation surface and UI tree. It must not release the player, replace the media item, call
`prepare()`, seek to a restored position, or clear the existing buffer.

Activity `ON_STOP` is classified before applying the background policy:

- a real app background transition pauses playback, releases audio focus, and records conditional
  foreground-resume state;
- `Activity.isChangingConfigurations == true` preserves playback and the current buffer;
- the disposing presentation owner preserves playback intent while the old surface is detached;
- the recreated presentation owner attaches to the same player and same media without source reload.

The ViewModel releases the coordinator only from `onCleared()`. Compose removes lifecycle observers
and surfaces but never owns the final player release.

## MEDIA-NAV-02.4 acceptance

- portrait-to-landscape and landscape-to-portrait retain the same coordinator and ExoPlayer;
- rotation emits no `player_released`, `background_pause`, `media_item_set`, or `prepare_called`;
- audio has no perceptible restart and video interruption is limited to surface reattachment;
- progressive, DASH, HLS, split A/V, fallback, preload, mute, quality, scrubbing, fullscreen route,
  and normal background pause/resume remain unchanged;
- source contracts, unit tests, and a warning-free debug build pass.


## MEDIA-NAV-02.5 scope

Implementation marker: `SUBSHELL_MEDIA_NAV025_DIRECT_CONFIGURATION_HANDLING_V1`

The Compose-only host handles window-geometry configuration changes directly instead of
destroying and recreating `MainActivity`. The manifest declares exactly `orientation`,
`screenSize`, `smallestScreenSize`, and `screenLayout`. Other configuration changes continue to
use the platform default recreation policy.

On a handled rotation:

- the same Activity, ViewModel, coordinator, ExoPlayer, media source, decoder, and buffer remain;
- Compose recomposes against the updated window configuration;
- the fullscreen TextureView is resized in place rather than reconstructed with a new Activity;
- immersive system-bar behavior and light/dark icon appearance are reapplied for the new geometry;
- no background pause, player release, media-item replacement, prepare, or restore seek occurs.

The saveable fullscreen route and position state remain required for process death and unrelated
configuration changes that still recreate the Activity.

## MEDIA-NAV-02.5 acceptance

- portrait-to-landscape and landscape-to-portrait do not recreate `MainActivity`;
- player identity remains unchanged and `configuration_changed_in_place` records the new geometry;
- audio remains continuous and video interruption is limited to an in-place surface resize;
- fullscreen route, aspect-safe TextureView rendering, controls, mute, seek, and A/V sync remain;
- multi-window resizing recomposes without source reload;
- the exact manifest declaration, source contracts, unit tests, and a warning-free debug build pass.
