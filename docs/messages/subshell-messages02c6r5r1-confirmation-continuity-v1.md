# MESSAGES02C6R5R1 — optimistic media confirmation continuity

C6R5 removes perceived Matrix upload latency by rendering the selected local media URI
immediately while upload proceeds. Runtime video showed one remaining visual defect:
after Matrix accepted the `m.image` event, the local `content://` backing was cleared and
the same GIF was reloaded from the Matrix download URL. That created a visible blank /
spinner confirmation flicker.

C6R5R1 keeps the local URI as the visual backing through the accepted state:

1. `SENDING`: render local GIF/image with `Sending…`.
2. Matrix HTTP 200 + `event_id`: keep exactly the same local media backing and remove only
   the `Sending…` delivery label.
3. Continue using the server `sentMatrixMediaId` / event ID for authoritative reconciliation.
4. Once the Reddit/Matrix projection containing that unique media ID is present, suppress
   the local backing and reset reply state; the authoritative item is already in the list.
5. Failures still clear the optimistic bubble and leave the original composer attachment
   available for recovery.

The redundant composer footer `Uploading and sending…` is suppressed for Direct Chat
media because the outgoing bubble already exposes `Sending…`. Legacy private-message media
status is unchanged.

No Matrix upload, send, cache, room-resolution, auth, retry, or authoritative
reconciliation semantics change.
