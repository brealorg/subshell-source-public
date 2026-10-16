# MESSAGES02D3B — delete own Direct Chat message

D3B promotes the D3A runtime-proven Reddit Matrix redaction path into an explicit user action for
server-backed messages authored by the active user.

## Proven transport boundary

D3A observed a live `m.room.redaction` event from Reddit Direct Chat on 2026-08-28. D3B uses the
standard Matrix room redaction write for the exact Matrix event selected by the user:

`PUT /_matrix/client/v3/rooms/{roomId}/redact/{eventId}/{transactionId}`

The writer reuses the same first-party Reddit web JWT, verified Matrix identity and exact two-party
room cache already shared by C7/D2B. It performs no room discovery and never guesses between rooms.

## Product policy

- Delete is exposed only for the active user's own Direct Chat bubbles that map to exactly one live
  Matrix event ID.
- Legacy Reddit inbox IDs are never sent to Matrix as redaction targets.
- Ambiguous text/time reconciliation fails closed and offers no delete action.
- The user must confirm `Delete message?`; the dialog states that deletion applies to everyone and
  cannot be undone.
- An accepted redaction immediately suppresses the live Matrix event and its uniquely matching
  authoritative inbox bubble so stale T4 projection cannot resurrect it.
- Incoming peer or self redactions from `/sync` use the same bounded memory-only suppression state.
- Transport interruption after dispatch is `OUTCOME_UNKNOWN`; D3B never automatically retries a
  destructive redaction and the failure dialog deliberately has no Retry action.
- Read receipts, Seen UI and presence remain out of scope.

Runtime write logging uses `subshell-matrix-delete` and emits only accepted/failed outcome, bounded
failure kind and HTTP status. It never logs usernames, Matrix user IDs, room IDs, event IDs, bearer
tokens or message bodies.

## Runtime evidence

<!-- SUBSHELL_MESSAGES02D3B_RUNTIME_EVIDENCE_20260829 -->

Cross-client runtime verification on DEV1 confirmed the complete destructive-write round trip:

```text
subshell-matrix-delete: result=accepted
subshell-matrix-redaction: redaction_events=1 sender_self=1 sender_peer=0
```

The redaction sync event followed the accepted write immediately in the same Direct Chat. This
proves the authenticated Subshell account can delete its own unambiguous live Matrix-backed Direct
Chat message and receive the authoritative server redaction back through the existing foreground
live reader.

No automatic destructive retry is enabled. Peer-owned and non-unambiguous/non-live messages remain
outside the delete-action eligibility policy.
