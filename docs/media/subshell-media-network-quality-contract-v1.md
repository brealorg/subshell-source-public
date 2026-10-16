# subshell Network Quality Profiles Contract v1

Marker: `SUBSHELL_MEDIA04D_NETWORK_QUALITY_PROFILES_V1`

## MEDIA-04D scope

MEDIA-04D replaces the single persisted remote-video quality ceiling with separate automatic
profiles for Wi-Fi/unmetered and mobile-data/metered playback. Android meteredness, rather than a
transport name or SSID, owns runtime selection. A temporarily unmetered default network therefore
uses the Wi-Fi/unmetered profile.

## Defaults and migration

- New installations default to `High` on unmetered networks and `Medium` on metered networks.
- Existing `video_quality` values migrate to both profiles so an upgrade does not silently alter
  the user's established ceiling.
- New backups write only `video_quality_wifi` and `video_quality_mobile`.
- Older backups without any quality key receive the new defaults.

## Runtime selection

- Remote media on `UNMETERED` uses the unmetered profile.
- Remote media on `METERED` uses the metered profile.
- A transition to `OFFLINE` preserves the active automatic ceiling. Connectivity remains a preload
  input and never becomes an explicit playback gate.
- Packaged/local media always uses the unmetered profile and does not change quality when the
  device network class changes.
- Changing either persisted profile reapplies the effective automatic ceiling to active playback.

## Session overrides

Exact video-track selection remains session-scoped and takes precedence over automatic ceilings.
Network or settings changes update the pending automatic profile without clearing the exact
`TrackSelectionOverride`. Returning to Auto applies the latest effective profile. Source changes
continue to clear exact selections under the MEDIA-03C.2 contract.

## Scope boundaries

MEDIA-04D does not change MEDIA-04B preload depth, MEDIA-04C cache ownership or clear behavior,
download storage, provider parsing, authentication, autoplay policy, audio selection, subtitles,
cache keys, or network telemetry. Telemetry remains coarse, numeric or enum-based and URL-free.

## Runtime proof

`VideoNetworkQualityProfilesInstrumentedTest` observes the real Android default-network snapshot
and executes a deterministic matrix for unmetered, metered, offline-preservation and local-media
independence. `tools/run-media04d-network-quality-runtime.sh` installs the verified debug and test
APKs using the mDNS-first device policy and requires the explicit runtime pass marker.
