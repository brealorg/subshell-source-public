# subshell Navigation Lifecycle Contract v1

Implementation marker: `SUBSHELL_NAV_LIFECYCLE01_STATE_RESTORE_V1`

## NAV-LIFECYCLE-01 scope

A normal background/foreground cycle or Activity recreation must not reconstruct the app at
Home when the user was browsing a community. The root `SubshellState` therefore restores the
minimum task-navigation session before the first screen is selected:

- main navigation destination;
- selected community;
- community sort;
- global feed mute state;
- the video media ID eligible for conditional foreground reacquisition;
- whether that media ownership was manual.

The existing `rememberSaveableStateHolder` and the community screen's saveable
`LazyListState` remain responsible for the exact list position. Restoring the community route
with the same state-provider key reattaches that saved scroll state. Once the restored viewport
becomes idle, the existing visibility policy may reacquire the saved media only when that media
is still visible.

## Behaviour boundaries

- RAMPED viewport ownership and its thresholds are unchanged.
- Media3, preload, audio focus, transport controls, and global mute semantics are unchanged.
- Backgrounding still pauses and releases audio focus.
- Foreground restore does not bypass visibility or idle-viewport gates.
- Unknown community names are rejected rather than restored.
- Closing a restored community returns to its saved main destination.
- Playback timestamp persistence across a destroyed player is outside this phase; this phase
  restores route, scroll context, mute state, and eligible media ownership.
- An explicit fresh launch after task removal remains allowed to start at Home.

## Acceptance

`NAV-LIFECYCLE-01` is accepted when unit tests, the navigation lifecycle contract, the media
contract, the source contract, and a warning-free debug build pass. Runtime must confirm:

1. open `r/subshell_video_lab` and park on Media F;
2. background subshell by switching to another app;
3. return to the existing subshell task;
4. the same community and list position remain visible;
5. Media F is conditionally reacquired without duplicate audio;
6. Back returns to the main destination that opened the community.


## Configuration-stable media owner

`MEDIA-NAV-02.4` retains the Media3 coordinator in the Activity `ViewModelStore`. Route state is
still saveable, but rotation no longer reconstructs playback from that state. Saveable position is
a process-recreation fallback only; ordinary configuration changes keep the live player, decoder,
media source, buffer, play intent, and mute state.


## Direct window-geometry configuration handling

`MEDIA-NAV-02.5` supersedes Activity recreation for orientation and window-size changes.
`MainActivity` handles `orientation|screenSize|smallestScreenSize|screenLayout` directly, while
Compose recomposes from the updated configuration. Saveable navigation and media state remain the
fallback for process death and any configuration change outside that exact set.
