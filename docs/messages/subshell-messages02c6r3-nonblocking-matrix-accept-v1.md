# MESSAGES02C6R3 — Non-blocking Matrix acceptance

Matrix HTTP 200 + event_id is the server-confirmed write boundary. The UI may render the confirmed Matrix media echo immediately after that response; it must not block on a second full `/sync`. The existing Reddit inbox refresh remains a read-only reconciliation path keyed by the unique Matrix media ID. Unknown write outcomes remain fail-closed with no blind resend.

A safe performance log records only durations and cache-hit booleans under `subshell-matrix-media`; it never logs tokens, room IDs, participants, event IDs, or media IDs.
