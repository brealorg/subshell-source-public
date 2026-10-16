# subshell FEEDPOST-V2E full process-state contract V1

FEEDPOST-V2E makes the complete navigation model saveable as one versioned
`SubshellProcessSnapshot`. Compose owns persistence through
`Saver<SubshellState, String>`, so Android captures the latest state when it
saves the activity instance. The implementation does not continuously encode
the anchor registry while the user scrolls.

## Snapshot contents

The V1 snapshot contains:

- the complete ordered `SubshellRouteStack`;
- every keyed `FeedScrollAnchor`;
- per-user profile and activity tab selections;
- Library and You tab selections;
- the active local post-composer draft;
- feed mute state and conditional media-resume identity.

Every route kind is represented explicitly: Main, Community, Search, Post,
User, Conversation, Secondary, Media, and PostComposer. Post routes retain
post id, focused comment id, visit id, and typed V2A origin. Search routes retain
destination, originating community, query, scope, and area. Media routes retain
post id and gallery index.

## Wire format and validation

The snapshot is a versioned line-record format. Every field is URL-safe Base64
encoded and nullable fields use an explicit null marker. The token is bounded, route and anchor counts are capped, and malformed field
encoding rejects the entire snapshot. Semantically invalid route fields are
either defaulted or dropped by the restore policy below.

Restore validates records against the local fixture catalog:

- Home is always reconstructed as the canonical root.
- Unknown enum values use safe defaults.
- Unknown route or record kinds are ignored for forward compatibility.
- Unknown communities and inbox items are dropped.
- A missing post drops that Post route and every later dependent route.
- Invalid media routes are dropped unless the post exists and is media-capable.
- A current-community search without a valid community becomes an all-subshell
  search.
- Corrupt or unsupported snapshots fall back to Home with an empty anchor
  registry.

## Compatibility

The V2A origin-token and earlier saveable mirrors remain as a migration
fallback for state produced before V2E. New state is restored from the complete
V2E snapshot first. V2B remains the only navigation engine, and V2C remains the
only scroll-anchor store.

V2E does not add Reddit networking, alter media ownership, or persist generated
local posts that do not exist in the process-restoration catalog.

## Runtime proof

`SubshellProcessStateInstrumentedTest` runs in two separate instrumentation
invocations. Phase one writes a full snapshot. The runtime script force-stops
the application and test packages. Phase two starts a new process, restores the
snapshot, verifies the route stack, keyed anchors, tabs, composer draft, media
route, and deterministic Back unwinding, then deletes the test residue.
