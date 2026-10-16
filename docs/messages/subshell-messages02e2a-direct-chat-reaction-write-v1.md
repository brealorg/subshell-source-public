# MESSAGES02E2A — Direct Chat reaction write

E2A promotes the runtime-proven Reddit Web reaction transport into the first explicit Subshell
reaction product slice.

## Proven write boundary

A sanitized Reddit Web HAR captured on 2026-08-30 proves that adding a reaction sends:

`PUT /_matrix/client/v3/rooms/{roomId}/send/m.reaction/{transactionId}`

with `m.relates_to.event_id`, reaction `key`, and `rel_type = m.annotation`. The successful Reddit
Web response was HTTP 200 with a Matrix `event_id`.

E2A reuses the existing first-party Reddit Web JWT, verified Matrix identity and exact cached
two-party room shared by the foreground Direct Chat runtime. It performs no room discovery.

## Product policy

- React is offered only when one displayed Direct Chat bubble maps to exactly one current live
  Matrix event.
- Both self and peer messages may be reaction targets.
- Legacy Reddit inbox/T4 IDs are never sent directly as Matrix reaction targets.
- The picker uses the 42 Reddit reaction assets captured from Reddit Web with cache disabled.
- HTTP 200 plus a valid returned reaction `event_id` is the server-confirmed acceptance boundary.
- An accepted self reaction is projected immediately into memory and rendered as a reaction chip.
- Duplicate same-key self reactions are suppressed within the current E2A in-memory state.
- A transport interruption after dispatch is `OUTCOME_UNKNOWN`; E2A never automatically retries.
- Reaction state is memory-only and bounded.

## Explicit read-side boundary

E1 proved reply structure in foreground `/sync`, but the isolated Reddit Web reaction action did
not yield an observed `m.reaction` / `m.annotation` event in that probe.

E2A therefore does **not** claim historical or peer-reaction read support. Its visible reaction chip
is based only on the server-confirmed reaction event created by Subshell in the current process.
Promoting inbound/historical reaction state requires a separately observed read path.

## Excluded

E2A does not add unreact / reaction removal, reply, read receipts / Seen, presence, reaction
persistence, automatic retry, or generic Matrix room discovery.

Runtime write logging uses `subshell-matrix-reaction` and emits only accepted/failed outcome,
bounded failure kind and HTTP status. It never logs the reaction key, target event ID, returned
event ID, room ID, Matrix user ID, username, token or message body.

## Runtime verification

Open the same Direct Chat in DEV1 and Reddit Web. React to one peer message and one own message in
Subshell. Each accepted write should immediately produce a chip in Subshell and become visible in
Reddit Web. Expected sanitized log: `subshell-matrix-reaction: result=accepted`.

Runtime proof remains pending until that cross-client check is completed.

## E2A R1 — initial Matrix timeline hydration

Runtime eligibility instrumentation showed `scope=true`, `live=true`, eight authoritative Direct
Chat messages, but `raw_live=0`, `scoped_live=0`, `matrix=0`, `react=0`, and `delete=0`.

The C7 initial `/sync` path filtered `baseline.projection.messages` against the process start time.
That prevented existing displayed T4 bubbles from reconciling to exact Matrix event IDs when an
older chat was opened.

R1 forwards the existing bounded `INITIAL_TIMELINE_LIMIT` baseline messages regardless of age. It
does not broaden the timeline limit and does not add historical pagination. Existing
authoritative/live reconciliation remains unchanged.

This hydration is shared infrastructure for E2A React eligibility and D3B Delete eligibility.
Reaction and redaction write transports are unchanged.

## E2A R2 — Reddit reaction catalog

A cache-disabled Reddit Web capture exposed 42 reaction assets in one reaction family. The
successful Matrix write used `g6akcitga7081.gif` as `m.reaction.key`; an earlier successful capture
used `foyijyyga7081.gif`. Both keys are filenames of `https://i.redd.it/` assets.

R2 removes the placeholder Unicode reaction picker. The picker renders the 42 captured Reddit
reaction assets and sends the selected asset filename as the Matrix annotation key. The writer
fails closed for values outside this captured catalog.

The reaction strip renders known Reddit reaction assets instead of exposing their filename as UI
text. Unknown future keys retain a text fallback rather than crashing.

R1 baseline Matrix hydration remains unchanged.

## E2A R2R1 — reaction picker presentation cleanup

R2R1 is presentation-only. It keeps the R2 42-asset Reddit reaction catalog, filename-key writer
policy, R1 baseline hydration, reaction strip, and Matrix transport unchanged.

The phone picker uses four columns instead of five, preventing the clipped fifth-column sliver
observed on Pixel 6. Visible GIF reaction assets play directly in the lazy picker instead of
rendering the generic GIF play overlay. Picker cells are slightly larger for touch and visual
parity.

No reaction keys, writer behavior, room/session behavior, read-side behavior, or reaction-strip
projection semantics change in R2R1.

## E2A R3A — authoritative reaction relations read probe

Force-stop/reopen testing proved that R2 reactions are process-local until an authoritative read
path is added. Foreground `/sync` did not expose `m.reaction` / `m.annotation` events or unsigned
annotation summaries in the observed runtime.

R3A is a read-only discovery slice. After the existing R1 baseline has hydrated Matrix-backed
message event IDs and the live reader has established the verified Matrix identity and room cache,
a separate probe queries at most the latest 12 Matrix message event IDs through:

`GET /_matrix/client/v1/rooms/{roomId}/relations/{eventId}/m.annotation/m.reaction`

The probe shares the existing Reddit Web JWT provider and verified Matrix session cache, performs
no room discovery, and does not project reactions into `DirectChatLiveUiState`. It emits one
sanitized aggregate log containing target count, HTTP status counts, total reaction count, known
catalog count, self/peer counts, unknown-key count, malformed-event count, protocol failures, and
transport failures. It never logs room IDs, target IDs, reaction event IDs, reaction keys,
usernames, message bodies, or tokens.

R3A does not yet claim reaction persistence or authoritative reaction presentation. A successful
relations observation is the gate for R3B hydration.

## E2A R3B — authoritative reaction hydration

R3A runtime proof returned HTTP 200 for all 12 bounded relations queries and found 17 valid,
catalog-known reactions with no malformed, protocol, transport, or unknown-key failures. R3B
promotes that same read into product state without adding another network request.

The R3A relations reader now returns the known reaction child events and the exact set of target
messages whose relations response was successfully parsed. On foreground Direct Chat connect,
those authoritative targets replace stale in-memory reaction state for the same messages.
Reactions for targets that were not authoritatively read are retained. Server-confirmed local
writes that occur while the read is in flight are also retained by reaction event ID.

Known relations are materialized as `DirectChatReaction` and merged into the existing bounded
reaction state. Redacted reaction events and reactions targeting redacted messages remain
suppressed. No database persistence is introduced; persistence comes from re-reading Matrix
relations on foreground hydration.

R3B does not add reaction removal/unreact. Unknown future reaction keys are counted by the reader
but are not projected until the Reddit reaction catalog is updated.

## E2A R3C — fast progressive reaction hydration

R3B proved authoritative Matrix relations hydration after process restart, but the original reader
issued up to 12 relations requests sequentially and applied state only after the final response.
Observed cold-open reaction hydration therefore took several seconds.

R3C keeps the same bounded maximum of 12 read-only relations requests and the same authoritative
R3B reconciliation model, but changes scheduling and presentation timing:

- target messages are queried newest-first;
- relations requests run in bounded waves of four concurrent GETs;
- each completed wave is projected immediately instead of waiting for all 12 targets;
- a compare-and-set state update recomputes against the latest reaction state, preserving writes
  accepted while hydration is in flight;
- sanitized batch and final logs include elapsed milliseconds for runtime measurement.

No extra network requests, database cache, reaction writer changes, catalog changes, or unreact
behavior are added. The intended cold-open behavior is that reactions on the newest visible
messages appear after the first four-request wave, with the remaining bounded history following in
subsequent waves.

## E2A R3D — persisted stale-while-revalidate reaction presentation cache

R3C reduced the authoritative relations fan-out itself to roughly one second, but any network-first
design still leaves a visible delay after process death. R3D therefore adds a small local
stale-while-revalidate presentation cache.

The cache is account/conversation scoped through a SHA-256 storage key and expires after 24 hours.
It persists only Matrix target/reaction event IDs, known catalog keys, self/peer ownership,
outgoing direction, timestamp, and either Matrix media ID or SHA-256 of normalized message text
for fail-closed presentation matching.

It does not persist message bodies, usernames, participant names, room IDs, OAuth tokens, Reddit
Web JWTs, or arbitrary reaction assets. Unknown catalog keys are rejected during decode.

On cold open, a valid cache snapshot is loaded before Matrix baseline/relation traffic. Cached
reaction targets are used only for reaction presentation and duplicate awareness; they never
authorize React or Delete writes. Live Matrix target resolution continues to gate all mutations.
R3C remains the authoritative background revalidator and progressively replaces stale cached
state. Accepted writes and progressive authoritative batches refresh the cache.

If the cache is absent, invalid, or older than 24 hours, the existing R3C network hydration path
remains the fallback.

### R3D scope contract

R3D uses a persisted stale-while-revalidate presentation cache.
Cached reaction mappings never authorize React or Delete writes.
If the cache is absent, invalid, or older than 24 hours, R3C remains the authoritative fallback.
