# MESSAGES02D3A — Direct Chat redaction read probe

D3A observes deletion/redaction events from Reddit Direct Chat without adding any delete write path.

The existing C7 Matrix `/sync` filter already receives unrestricted room timeline event types.
D3A therefore changes no sync filter. It only projects `m.room.redaction` events from the already
received timeline and logs sanitized runtime facts.

Supported observation shapes:

- classic Matrix room shape with top-level `redacts`;
- newer room-version-compatible shape with `content.redacts`.

Runtime log tag: `subshell-matrix-redaction`.

The log exposes only:

- number of redaction events in the sync batch;
- count sent by the authenticated Matrix user;
- count sent by the peer.

It never logs event IDs, target IDs, usernames, room IDs, tokens, reasons or message bodies.

D3A performs no Matrix write, no delete write, no UI mutation and no persistence. The probe exists
only to establish what Reddit's live Matrix service emits when the official client deletes a chat
message. A successor delete implementation must be based on runtime evidence, not assumption.

## Runtime evidence — 2026-08-28

<!-- SUBSHELL_MESSAGES02D3A_RUNTIME_EVIDENCE_20260828 -->

Verified on DEV1 against Reddit's live Matrix Direct Chat service:

- the foreground live reader connected with `identity_cache=true room_cache=true resumed=true`;
- deleting one Direct Chat message from the peer client produced
  `redaction_events=1 sender_self=0 sender_peer=1`;
- Reddit Direct Chat therefore delivers timeline `m.room.redaction` events for deletion.
