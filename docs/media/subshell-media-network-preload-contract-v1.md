# subshell Network-aware Preload Contract v1

## MEDIA-04B scope

MEDIA-04B makes speculative feed preloading react to the Android default network and viewport
motion without inserting a network gate into explicit playback. The policy consumes only coarse
connectivity state: validated internet availability, meteredness and Data Saver status.

The process-local observer uses `ConnectivityManager.registerDefaultNetworkCallback` and
re-evaluates Data Saver when `ACTION_RESTRICT_BACKGROUND_CHANGED` is delivered. It never records
SSID, carrier, IP addresses, request URLs, headers or credentials.

## Network classes

- `UNMETERED`: the default network has internet capability, is validated and is not metered.
- `METERED`: the default network has validated internet but should avoid large transfers.
- `OFFLINE`: no default network with both internet capability and validation is available.

`NET_CAPABILITY_TEMPORARILY_NOT_METERED` is treated as unmetered while Android grants it.

## Preload depth

For remote sources while the viewport is idle:

| Network class | Focus candidate | Distance 1 | Distance 2 |
|---|---|---|---|
| `UNMETERED` | 3 seconds loaded | tracks selected | source prepared |
| `METERED` | source prepared | none | none |
| `OFFLINE` | none | none | none |

Local assets and packaged resources retain the established idle preload depth because they do not
consume network data.

Viewport motion takes precedence over network depth:

- `FLINGING`: no speculative preload;
- `DRAGGING`: only the immediate candidate may reach `source prepared`;
- `IDLE`: apply the network table above.

A network or viewport-policy transition invalidates the shared `DefaultPreloadManager` so its
current targets are recalculated immediately.

## Explicit playback boundary

MEDIA-04B does not add connectivity checks to `VideoPlaybackCoordinator.attach`, `play`, source
fallback or cache reads. A user-initiated request may therefore use already cached content or try
upstream even when speculative preload is blocked. Existing one-player ownership, mute, audio
focus, session track selection, full-screen handoff and position preservation remain unchanged.

## Runtime proof

The debug instrumentation test starts the real default-network observer on-device and requires an
initial sanitized snapshot. It validates that the observed class is internally consistent, then
executes the deterministic unmetered, metered, offline and fling policy matrix. The runtime log may
contain only the coarse class, validation, metering and Data Saver booleans.

## Deferred

MEDIA-04B does not add separate Wi-Fi/mobile quality settings, download/offline library
management, provider authentication or frame-by-frame playback. Cache observability and clear
controls are delivered separately by MEDIA-04C.
