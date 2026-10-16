# subshell Media Track Intelligence Contract v1

## MEDIA-03C.1 scope

Implementation markers:

- `SUBSHELL_MEDIA03C1_TRACK_INTELLIGENCE_DIAGNOSTICS_V1`
- `SUBSHELL_MEDIA03C11_MODAL_DIAGNOSTICS_CONTROL_ISOLATION_V1`

MEDIA-03C.1 adds a provider-neutral, read-only runtime diagnostics layer to the existing shared
Media3 player. It does not add a second player, provider-specific parsing, track overrides, or
new playback policy.

`Player.Listener.onTracksChanged` records the available, supported, and selected video, audio,
and text tracks after preparation and whenever the selection changes. Adaptive selections may
contain multiple selected video tracks, so this inventory is not presented as the one currently
rendered representation.

A narrowly scoped `AnalyticsListener` records the formats actually consumed by the video and
audio renderers, decoder names and initialization latency, cumulative dropped-video-frame
reports for the active source candidate, and recoverable codec events. It is diagnostics-only and
does not restore the retired MEDIA-PERF-02 analytics/tuning pipeline.

## Presentation

All full-screen videos expose the same Playback details control. The read-only overlay reports:

- active source kind, ordered fallback position, and split A/V status;
- selected, supported, and available video/audio/text track counts;
- the renderer-consumed video resolution, frame rate, bitrate, MIME/codec metadata;
- the renderer-consumed audio channel count, sample rate, bitrate, language, and codec metadata;
- video and audio decoder names and initialization duration;
- dropped frames and the latest recoverable codec event, when present.

The overlay remains visible until explicitly dismissed. MEDIA-03C.1.1 treats it as a modal
diagnostics surface: the ordinary transport scrim, center play/pause control, timeline, mute,
and full-screen controls are hidden while it is open. Playback continues, system bars remain
available, taps outside the panel are consumed, and Android Back or the panel close button
dismisses diagnostics before leaving full screen. Normal controls and auto-hide restart after
dismissal.

It is global to progressive, DASH, HLS, split A/V, and fallback playback; no fixture ID is
special-cased.

## Lifecycle

Diagnostics reset when a new ordered source candidate is loaded, including fallback. They remain
attached to the same Activity-retained ExoPlayer across feed/post/full-screen handoff and direct
configuration changes. Final listener removal follows the existing ViewModel-owned player
release.

## Preserved contracts

MEDIA-03C.1 does not change:

- source ordering, source construction, fallback, preload, decoder choice, or buffering;
- automatic quality ceilings or future manual track-selection policy;
- one-active/one-audible ownership, mute, audio focus, seek, scrubbing, or timeline controls;
- full-screen TextureView fit, direct configuration handling, route restoration, or system bars;
- the offline manifest, Media3 version catalog, fixtures, downloads, cache, or networking.

## Acceptance

MEDIA-03C.1 is accepted when:

- track inventory updates from `onTracksChanged`;
- active renderer formats come from video/audio input-format callbacks rather than guessing from
  all selected adaptive tracks;
- decoder and dropped-frame diagnostics remain read-only and cannot drive retry or quality policy;
- Playback details is available for every full-screen video and remains open until explicitly dismissed;
- the modal diagnostics layer suppresses transport controls without pausing or mutating playback;
- Android Back closes diagnostics before the full-screen media route;
- taps outside the panel are consumed and normal controls resume after dismissal;
- source changes and fallback reset stale diagnostics;
- diagnostics listeners are removed only with final coordinator release;
- unit tests, all project contracts, and a warning-free debug APK build pass;
- runtime smoke covers progressive, DASH or HLS, split A/V, fallback, and rotation.


## MEDIA-03C.2 scope

Implementation marker:

- `SUBSHELL_MEDIA03C2_PLAYBACK_TRACK_SELECTION_V1`

MEDIA-03C.2 promotes the full-screen information control into a global Playback options menu.
The menu exposes the supported video, audio, and text tracks reported by Media3 for the active
source candidate. Technical details remains available as a secondary, read-only page inside the
same modal flow rather than as a permanent top-level diagnostics button.

Quality offers Auto plus exact supported video representations. Auto clears the exact video
override and reapplies the existing subshell quality ceiling. Exact quality selection installs one
`TrackSelectionOverride` for the selected video track and does not change the stored global quality
preference.

Audio offers Auto plus exact supported audio tracks. Subtitles offers Off, Auto, and exact
supported text tracks. Off disables the text renderer; Auto clears exact text overrides and restores
source/default selection. Exact audio and text choices each install one type-specific override.

Exact selections are session-scoped. They survive feed/post/full-screen presentation handoff,
direct configuration changes, and ordinary recomposition because they remain attached to the same
Activity-retained player. Loading a different media item, ordered fallback candidate, or retry source clears all exact
overrides before preparation so stale `TrackGroup` identities
cannot leak into another source.

## Subtitle presentation

Video Lab A carries deterministic English and Norwegian WebVTT sidecar tracks. Selected cues are
received through the shared player listener and rendered above the video with Media3 `SubtitleView`.
The view uses the device caption style and text-size defaults, remains non-interactive, and reserves
additional bottom space while transport controls are visible. Subtitle selection and rendering are
provider-neutral and apply to any source exposing supported text tracks.

## Playback-options presentation

While Playback options or Technical details is open:

- playback continues without player, source, buffer, position, mute, or audio-focus mutation;
- center transport, timeline, top controls, retry, replay, and buffering actions remain hidden;
- taps outside the panel are consumed and system bars remain available;
- Android Back moves from Technical details to Playback options, then dismisses the modal;
- the close action dismisses the entire modal and restores normal control auto-hide.

## MEDIA-03C.2 acceptance

MEDIA-03C.2 is accepted when:

- Auto and exact video selection update only the video track-selection policy;
- Auto and exact audio selection update only the audio track-selection policy;
- Off, Auto, and exact subtitle selection update text tracks and visible cues correctly;
- exact choices are visibly selected in the menu and reset on source/fallback change;
- Video Lab A exposes both English and Norwegian WebVTT tracks and renders the selected cues;
- Technical details is reachable from Playback options and Back returns to options first;
- transport isolation from MEDIA-03C.1.1 remains intact for both modal pages;
- progressive, DASH, HLS, split A/V, fallback, full-screen handoff, and rotation remain unchanged;
- unit tests, all project contracts, and a warning-free debug APK build pass.

## MEDIA-03C.2.1 scope

Implementation marker:

- `SUBSHELL_MEDIA03C21_HIERARCHICAL_PLAYBACK_OPTIONS_V1`

MEDIA-03C.2.1 replaces the flat, scroll-dependent Playback options list with a compact root page.
The root page exposes Quality, Audio, Subtitles, and Technical details as four immediately visible
navigation rows. Each row reports the active session selection before the user opens its child page.
The root page itself does not require scrolling on the phone-sized full-screen presentation.

Quality, Audio, Subtitles, and Technical details each use a dedicated child page. The child-page
header and Back/Close actions remain fixed while only the choice list or diagnostics body scrolls.
Selecting Auto, Off, or an exact supported track applies the existing MEDIA-03C.2 session policy
without leaving the active child page. The selected indicator and child-page summary update in place,
so users can compare several qualities or tracks without repeatedly reopening the category. Android
Back from any child page or Technical details returns to the root page, where the current selection
summary is visible; Android Back from the root page dismisses Playback options.

The hierarchy is presentation-only. It does not change track discovery, exact override identity,
subtitle rendering, source-reset behavior, quality ceilings, player ownership, playback continuity,
modal transport isolation, or direct configuration handling.
