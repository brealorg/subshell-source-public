# subshell Adaptive Media Source Contract v1

## MEDIA-03A scope

Implementation marker: `SUBSHELL_MEDIA03A_FORMAT_AWARE_ADAPTIVE_SOURCES_V1`

MEDIA-03A establishes the first provider-neutral adaptive source layer. Reddit is an
important future adapter, but the player core does not contain Reddit-specific parsing or
routing.

The shared Media3 player accepts ordered source candidates classified as:

- progressive media;
- MPEG-DASH manifests;
- HLS master or media playlists.

Each source kind resolves to an explicit Media3 MIME type when the provider did not supply
one. Explicit provider MIME metadata remains authoritative.

## Source construction

The player and `DefaultPreloadManager` share one `DefaultDataSource.Factory`. Media3's
default media-source factory selects progressive, DASH or HLS handling from the resolved
MIME type. The DASH and HLS modules use the same Media3 version as the existing ExoPlayer
and Compose UI artifacts.

MEDIA-03A originally kept the application manifest offline and validated only deterministic APK
assets. MEDIA-04A supersedes that deployment boundary by enabling the provider-neutral shared
data-source pipeline for secure remote media and a bounded playback cache. Provider
authentication, download storage and metered-network policy remain outside MEDIA-03A.

## Fallback

Source candidates remain ordered. When a source fails and playback was requested, the next
candidate is selected. The current non-negative playback position is forwarded to the next
candidate rather than unconditionally restarting at zero. Exhausting the candidate list keeps
the existing terminal error and manual Retry behavior.

## Deterministic fixtures

The separate `subshell_adaptive_lab` community contains:

1. a local two-representation DASH VOD with AAC audio;
2. a local two-variant HLS VOD with AAC audio;
3. an intentionally absent DASH primary followed by a progressive MP4 fallback.

Adaptive manifests and segments are packaged uncompressed under
`app/src/main/assets/media03/`. The original A-Z playback and viewport fixture matrix is not
modified.

## Preserved contracts

MEDIA-03A does not change:

- the single shared ExoPlayer instance;
- one-active and one-audible ownership;
- quality ceilings, audio focus or global mute;
- preload ranking and viewport arbitration;
- seek, scrubbing and timeline presets;
- feed/post/fullscreen handoff;
- TextureView fullscreen rendering or rotation restoration.

## Acceptance

MEDIA-03A is accepted when:

- catalog aliases and module dependencies for DASH and HLS use the canonical Media3 version;
- source-kind and fallback unit tests pass;
- the local DASH and HLS manifests and segments are packaged;
- the broken-primary fixture reaches its progressive fallback at runtime;
- the application manifest remains offline without `android.permission.INTERNET`;
- existing media and project contracts pass;
- unit tests and a warning-free debug APK build pass.

## MEDIA-03B scope

Implementation marker: `SUBSHELL_MEDIA03B_SPLIT_AV_MERGING_SOURCE_V1`

MEDIA-03B extends each ordered source candidate with an optional provider-neutral companion
audio track. A split candidate contains one primary video source and one companion audio
source; both retain independent URI, source-kind and MIME metadata. Provider adapters may
therefore describe Reddit-style separate video/audio resources without owning ExoPlayer or
player UI behavior.

The coordinator creates each child through the same `DefaultMediaSourceFactory` and
`DefaultDataSource.Factory` used by ordinary progressive, DASH and HLS playback. Child
timestamps are aligned and the merged duration is clipped to the shorter child through
`MergingMediaSource`. The first child remains the canonical video media item and media ID.

Composite first candidates are registered with `DefaultPreloadManager` as explicit media
sources. Existing non-composite candidates continue to use the established MediaItem preload
path. If a companion audio source fails, the entire candidate fails and the existing ordered
fallback mechanism selects the next candidate while preserving the current non-negative
position.

The `subshell_adaptive_lab` adds two deterministic local fixtures:

1. a video-only H.264 MP4 merged with an audio-only AAC MP4;
2. the same video with an intentionally absent companion audio resource followed by a
   progressive H.264/AAC MP4 fallback.

MEDIA-03B does not add remote networking, cache, downloads, track-selection UI, codec
diagnostics, additional player instances or provider-specific parsing. Feed ownership, audio
focus, global mute, quality ceilings, scrubbing, fullscreen, rotation and route restoration
remain unchanged.

## MEDIA-03B acceptance

MEDIA-03B is accepted when:

- split candidates are modeled independently of any provider;
- Media3 constructs one `MergingMediaSource` from video and companion audio children;
- composite candidates remain preload-managed;
- a missing companion track reaches the ordered progressive fallback with position preserved;
- both local split fixtures are packaged and hash-verified;
- all existing contracts, unit tests and a warning-free debug APK build pass.

## MEDIA-03B.1 scope

Implementation marker: `SUBSHELL_MEDIA03B1_DETERMINISTIC_AV_SYNC_SLATE_V1`

Runtime review of the initial split-track fixture could prove that Media3 loaded a merged
video/audio candidate, but its continuous tone and generic motion provided no shared temporal
reference. MEDIA-03B.1 replaces that fixture with a deterministic synchronization slate while
leaving the player engine unchanged.

The video-only child is an eight-second H.264 1280x720 30 fps stream. At presentation times
0 through 7 seconds it displays a full-frame white flash and an incrementing `SYNC` counter.
The audio-only child is an eight-second AAC mono 48 kHz stream with a short 1500 Hz click at
the same presentation times. Both children start at zero and have equal duration. The
progressive fallback carries the identical combined flash/click program.

A companion `sync-events.txt` file records the canonical event timeline in milliseconds. The
fixture validator decodes the packaged tracks and checks video flash frames, audio click
windows, quiet intervals, start timestamps, durations and stream separation. This proves that
the input assets are aligned before runtime review evaluates Media3 rendering on-device.

MEDIA-03B.1 changes no source model, player instance, merging policy, fallback policy, preload,
quality ceiling, mute, scrubbing, fullscreen, rotation, network permission or cache behavior.

## MEDIA-03B.1 acceptance

MEDIA-03B.1 is accepted when:

- the split fixture exposes visible flashes and audible clicks at 0, 1, 2, 3, 4, 5, 6 and 7 seconds;
- video-only and audio-only children both start at zero and last eight seconds;
- the packaged progressive fallback contains the identical synchronized program;
- the deterministic fixture validator passes before Android contracts and build;
- runtime review confirms click/flash alignment after start, seek, pause/resume and fullscreen;
- all existing contracts, unit tests and a warning-free debug APK build pass.
