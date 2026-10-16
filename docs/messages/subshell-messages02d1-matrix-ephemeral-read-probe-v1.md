# MESSAGES02D1 — Matrix ephemeral Direct Chat read probe

D1 extends the runtime-verified C7 foreground Matrix `/sync` reader with read-only observation of
room-scoped `m.typing` and `m.receipt` events.

Presence remains explicitly filtered out. D1 performs no Matrix writes, adds no typing writes,
adds no read-receipt writes, persists nothing, and changes no user-facing UI.

Runtime logging uses `subshell-matrix-ephemeral` and emits only counts/booleans. It never logs
usernames, Matrix user IDs, room IDs, bearer tokens, event IDs, message bodies, or timestamps.

## Runtime evidence — 2026-08-27

<!-- SUBSHELL_MESSAGES02D1_RUNTIME_EVIDENCE_20260827 -->

Verified on DEV1 against Reddit's live `matrix.redditspace.com` Direct Chat service:

- repeated `m.typing` ephemeral events were observed for the peer;
- typing start projected as `peer_typing=true`;
- typing stop projected as `peer_typing=false`;
- start/stop repeated cleanly across multiple typing sessions;
- normal `m.room.message` events continued to arrive through the C7 live timeline;
- one baseline `m.receipt` event was observed, but it did not contain a peer public read receipt;
- reading a Subshell-sent message on the other client did not produce peer `m.read` evidence;
- therefore D1 proves inbound typing support, but does not prove a user-visible peer "Seen" state;
- presence was not probed and remains explicitly filtered out.

Product consequence: inbound typing may be promoted to UI in D2A. Read-receipt UI, presence,
and all Matrix typing/read writes remain out of scope until separately proven.
